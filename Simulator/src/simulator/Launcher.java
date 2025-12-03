package simulator;

import javax.swing.*;

/**
 * Small launcher to run the WindowBuilder frame; useful for Run Configurations.
 */
public class Launcher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesignMainFrame frame = new DesignMainFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
