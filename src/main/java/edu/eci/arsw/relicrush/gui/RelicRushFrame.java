package edu.eci.arsw.relicrush.gui;

import edu.eci.arsw.relicrush.game.Adventurer;
import edu.eci.arsw.relicrush.game.GameConfig;
import edu.eci.arsw.relicrush.game.GameEngine;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.BrokenBarrierException;

/**
 * Control room for a Relic Rush match.
 *
 * Integration approach: this window never touches the concurrency
 * primitives directly. It only
 *  - starts/stops/pauses a GameEngine through the existing GameControl API,
 *  - and, on a Swing Timer (fired on the EDT), polls read-only accessors
 *    (AtomicInteger/AtomicBoolean getters, the volatile ForgeStation.busy
 *    flag, the volatile Adventurer.score field, and the lock-free
 *    ForgeLedger) that were already safe to read from another thread.
 * No lock, barrier, atomic or the deadlock-prevention ordering in
 * LockPair is created, removed or altered by the GUI.
 */
public final class RelicRushFrame extends JFrame {

    private enum RunState { IDLE, RUNNING, PAUSED, ENDED }

    private static final int POLL_MILLIS = 150;
    private static final Color OK_COLOR = new Color(0x2E, 0x7D, 0x32);
    private static final Color BROKEN_COLOR = new Color(0xC6, 0x28, 0x28);

    private final JSpinner adventurersSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 128, 1));
    private final JSpinner stationsSpinner = new JSpinner(new SpinnerNumberModel(6, 2, 16, 1));
    private final JSpinner roundsSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 5000, 1));

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resumeButton = new JButton("Resume");
    private final JButton stopButton = new JButton("Stop");

    private final JLabel statusValue = new JLabel("IDLE");
    private final JLabel roundValue = new JLabel("0 / 0");
    private final JLabel scoreSumValue = new JLabel("0");
    private final JLabel ledgerTotalValue = new JLabel("0");
    private final JLabel eventCountValue = new JLabel("0");
    private final JLabel invariantValue = new JLabel("-");

    private final StationsPanel stationsPanel = new StationsPanel();
    private final AdventurerTableModel adventurerTableModel = new AdventurerTableModel();
    private final JTable adventurerTable = new JTable(adventurerTableModel);
    private final JTextArea logArea = new JTextArea();

    private final Timer pollTimer = new Timer(POLL_MILLIS, e -> onTick());

    private GameEngine engine;
    private int totalRounds;
    private int lastLoggedRound;

    public RelicRushFrame() {
        super("Relic Rush - Concurrency Control Room");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (engine != null) {
                    engine.control().stop();
                }
                dispose();
                System.exit(0);
            }
        });

        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildControlBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        wireButtons();
        applyRunState(RunState.IDLE);

        setMinimumSize(new Dimension(960, 640));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createTitledBorder("Match setup & controls"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;

        int col = 0;
        c.gridx = col++;
        bar.add(new JLabel("Adventurers:"), c);
        c.gridx = col++;
        bar.add(adventurersSpinner, c);

        c.gridx = col++;
        bar.add(new JLabel("Stations:"), c);
        c.gridx = col++;
        bar.add(stationsSpinner, c);

        c.gridx = col++;
        bar.add(new JLabel("Rounds:"), c);
        c.gridx = col++;
        bar.add(roundsSpinner, c);

        c.gridx = col++;
        bar.add(startButton, c);
        c.gridx = col++;
        bar.add(pauseButton, c);
        c.gridx = col++;
        bar.add(resumeButton, c);
        c.gridx = col++;
        bar.add(stopButton, c);

        c.gridx = col++;
        bar.add(new JLabel("Status:"), c);
        c.gridx = col;
        statusValue.setFont(statusValue.getFont().deriveFont(Font.BOLD));
        bar.add(statusValue, c);

        return bar;
    }

    private JSplitPane buildCenter() {
        JScrollPane stationsScroll = new JScrollPane(stationsPanel);
        stationsScroll.setBorder(BorderFactory.createEmptyBorder());

        adventurerTable.setFillsViewportHeight(true);
        adventurerTable.setRowHeight(22);
        JScrollPane tableScroll = new JScrollPane(adventurerTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Adventurers"));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Round log"));

        JPanel invariantPanel = buildInvariantPanel();

        JPanel rightBottom = new JPanel(new BorderLayout(6, 6));
        rightBottom.add(invariantPanel, BorderLayout.NORTH);
        rightBottom.add(logScroll, BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, rightBottom);
        rightSplit.setResizeWeight(0.45);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stationsScroll, rightSplit);
        mainSplit.setResizeWeight(0.35);
        return mainSplit;
    }

    private JPanel buildInvariantPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 5, 10, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Simulation state & invariant"));

        panel.add(labelPair("Round", roundValue));
        panel.add(labelPair("Sum of scores", scoreSumValue));
        panel.add(labelPair("Ledger total", ledgerTotalValue));
        panel.add(labelPair("Ledger events", eventCountValue));
        panel.add(labelPair("Invariant", invariantValue));
        return panel;
    }

    private JPanel labelPair(String caption, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel captionLabel = new JLabel(caption, SwingConstants.CENTER);
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(captionLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private void wireButtons() {
        startButton.addActionListener(e -> onStart());
        pauseButton.addActionListener(e -> {
            if (engine != null) {
                engine.control().pause();
            }
            applyRunState(RunState.PAUSED);
        });
        resumeButton.addActionListener(e -> {
            if (engine != null) {
                engine.control().resume();
            }
            applyRunState(RunState.RUNNING);
        });
        stopButton.addActionListener(e -> {
            if (engine != null) {
                // Wakes up any adventurer blocked in awaitIfPaused() too.
                engine.control().stop();
            }
        });
    }

    private void onStart() {
        int adventurers = (Integer) adventurersSpinner.getValue();
        int stations = (Integer) stationsSpinner.getValue();
        int rounds = (Integer) roundsSpinner.getValue();

        GameConfig config = new GameConfig(adventurers, stations, rounds);
        engine = new GameEngine(config);
        totalRounds = rounds;
        lastLoggedRound = 0;

        stationsPanel.bind(engine.stations());
        adventurerTableModel.bind(engine.adventurers());
        logArea.setText("");
        appendLog("Started: " + adventurers + " adventurers, " + stations + " stations, " + rounds + " rounds.");

        GameEngine startedEngine = engine;
        Thread runner = new Thread(() -> runEngine(startedEngine), "gui-engine-runner");
        runner.setDaemon(true);
        runner.start();

        pollTimer.start();
        applyRunState(RunState.RUNNING);
    }

    private void runEngine(GameEngine startedEngine) {
        try {
            startedEngine.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException e) {
            // The coordinator resets the barriers when the game is stopped; expected.
        }
    }

    private void onTick() {
        if (engine == null) {
            return;
        }

        stationsPanel.refresh();
        adventurerTableModel.refresh();

        int scoreSum = engine.adventurers().stream().mapToInt(Adventurer::score).sum();
        int ledgerTotal = engine.ledger().totalCrafted();
        int eventCount = engine.ledger().eventCount();
        boolean invariantOk = scoreSum == ledgerTotal && ledgerTotal == eventCount;

        roundValue.setText(engine.currentRound() + " / " + totalRounds);
        scoreSumValue.setText(String.valueOf(scoreSum));
        ledgerTotalValue.setText(String.valueOf(ledgerTotal));
        eventCountValue.setText(String.valueOf(eventCount));
        invariantValue.setText(invariantOk ? "OK" : "BROKEN");
        invariantValue.setForeground(invariantOk ? OK_COLOR : BROKEN_COLOR);

        int round = engine.currentRound();
        if (round != lastLoggedRound && round > 0) {
            lastLoggedRound = round;
            appendLog(String.format("Round %02d | scoreSum=%d | ledger=%d | events=%d | invariant=%s",
                    round, scoreSum, ledgerTotal, eventCount, invariantOk ? "OK" : "BROKEN"));
        }

        if (engine.isFinished()) {
            pollTimer.stop();
            boolean stoppedByUser = engine.control().isStopped();
            appendLog(stoppedByUser ? "Stopped by user." : "Finished: all rounds completed.");
            applyRunState(RunState.ENDED);
        }
    }

    private void appendLog(String line) {
        logArea.append(line + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void applyRunState(RunState state) {
        statusValue.setText(state.name());
        boolean idleOrEnded = state == RunState.IDLE || state == RunState.ENDED;

        startButton.setEnabled(idleOrEnded);
        pauseButton.setEnabled(state == RunState.RUNNING);
        resumeButton.setEnabled(state == RunState.PAUSED);
        stopButton.setEnabled(state == RunState.RUNNING || state == RunState.PAUSED);

        adventurersSpinner.setEnabled(idleOrEnded);
        stationsSpinner.setEnabled(idleOrEnded);
        roundsSpinner.setEnabled(idleOrEnded);
    }
}
