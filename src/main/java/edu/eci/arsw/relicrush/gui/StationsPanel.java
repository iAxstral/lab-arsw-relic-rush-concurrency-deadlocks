package edu.eci.arsw.relicrush.gui;

import edu.eci.arsw.relicrush.model.ForgeStation;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of every ForgeStation's busy/free state.
 *
 * All reads (bind/refresh) run on the EDT via the poll timer in
 * RelicRushFrame; ForgeStation.isBusy() is a volatile field written by the
 * adventurer threads inside LockPair.withBoth, so this panel never blocks
 * on the game's monitors - it only observes them.
 */
final class StationsPanel extends JPanel {
    private static final Color FREE_COLOR = new Color(0x2E, 0x7D, 0x32);
    private static final Color BUSY_COLOR = new Color(0xC6, 0x28, 0x28);
    private static final Color FREE_TEXT = new Color(0xF1, 0xF8, 0xE9);
    private static final Color BUSY_TEXT = new Color(0xFF, 0xEB, 0xEE);

    private List<ForgeStation> stations = List.of();
    private final List<JLabel> tiles = new ArrayList<>();

    StationsPanel() {
        setLayout(new GridLayout(0, 2, 8, 8));
        setBorder(BorderFactory.createTitledBorder("Forge Stations"));
    }

    void bind(List<ForgeStation> newStations) {
        this.stations = newStations;
        removeAll();
        tiles.clear();
        for (ForgeStation station : newStations) {
            JLabel tile = new JLabel(station.name(), SwingConstants.CENTER);
            tile.setOpaque(true);
            tile.setFont(tile.getFont().deriveFont(Font.BOLD, 13f));
            tile.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 6));
            tile.setPreferredSize(new Dimension(150, 54));
            tiles.add(tile);
            add(tile);
        }
        refresh();
        revalidate();
        repaint();
    }

    void refresh() {
        for (int i = 0; i < stations.size(); i++) {
            ForgeStation station = stations.get(i);
            JLabel tile = tiles.get(i);
            boolean busy = station.isBusy();
            tile.setBackground(busy ? BUSY_COLOR : FREE_COLOR);
            tile.setForeground(busy ? BUSY_TEXT : FREE_TEXT);
            tile.setText("<html><center>" + station.name() + "<br>"
                    + (busy ? "BUSY" : "free") + "</center></html>");
        }
    }
}
