package edu.eci.arsw.relicrush.app;

import edu.eci.arsw.relicrush.gui.RelicRushFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class RelicRushGuiMain {
    private RelicRushGuiMain() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to the default cross-platform look and feel.
        }
        SwingUtilities.invokeLater(() -> new RelicRushFrame().setVisible(true));
    }
}
