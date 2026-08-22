package edu.eci.arsw.relicrush.gui;

import edu.eci.arsw.relicrush.game.Adventurer;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Leaderboard view of every Adventurer's live score.
 *
 * refresh() takes one snapshot copy of the adventurer list and sorts it,
 * then serves getValueAt from that fixed snapshot - this keeps one table
 * repaint internally consistent even though adventurer threads keep
 * updating their (volatile) score concurrently in the background.
 */
final class AdventurerTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"#", "Adventurer", "Relics crafted"};

    private List<Adventurer> adventurers = List.of();
    private List<Adventurer> ranked = List.of();

    void bind(List<Adventurer> newAdventurers) {
        this.adventurers = newAdventurers;
        refresh();
    }

    void refresh() {
        List<Adventurer> snapshot = new ArrayList<>(adventurers);
        snapshot.sort(Comparator.comparingInt(Adventurer::score).reversed());
        this.ranked = snapshot;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return ranked.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 1 ? String.class : Integer.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Adventurer adventurer = ranked.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> adventurer.playerId();
            case 1 -> adventurer.getName();
            case 2 -> adventurer.score();
            default -> "";
        };
    }
}
