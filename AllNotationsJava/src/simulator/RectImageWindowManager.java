package simulator;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RectImageWindowManager {
	
    private final Map<String, RectImageWindow> windowMap = new ConcurrentHashMap<>();

    /**
     * Create (and show) a window with the given name, title and background image.
     * If a window with the same name already exists, it is just brought to front.
     * This method is safe to call from any thread.
     */
    public void createWindow(String windowName, String title, String imagePath) {
        SwingUtilities.invokeLater(() -> {
            RectImageWindow existing = windowMap.get(windowName);
            if (existing != null) {
                existing.toFront();
                existing.requestFocus();
                return;
            }

            RectImageWindow window = new RectImageWindow(title, imagePath);
            windowMap.put(windowName, window);
            window.setVisible(true);
        });
    }

    /**
     * Get the RectImageWindow instance by name.
     * (You usually don't need this if you use addRect/removeRect wrappers below.)
     */
    public RectImageWindow getWindow(String windowName) {
        return windowMap.get(windowName);
    }

    /**
     * Add or update a rectangle in a specific window.
     * Safe to call from any thread.
     */
    public void addRect(String windowName, String rectName, Rectangle rect) {
        RectImageWindow window = windowMap.get(windowName);
        if (window != null) {
            window.addRect(rectName, rect); // RectImageWindow already EDT-safe
        }
    }

    /**
     * Remove a rectangle from a specific window.
     * Safe to call from any thread.
     */
    public void removeRect(String windowName, String rectName) {
        RectImageWindow window = windowMap.get(windowName);
        if (window != null) {
            window.removeRect(rectName); // RectImageWindow already EDT-safe
        }
    }

    /**
     * Close (dispose) a window and remove it from the manager.
     * Safe to call from any thread.
     */
    public void closeWindow(String windowName) {
        RectImageWindow window = windowMap.remove(windowName);
        if (window != null) {
            SwingUtilities.invokeLater(window::dispose);
        }
    }

    /**
     * Close all windows and clear the manager.
     * Safe to call from any thread.
     */
    public void closeAll() {
        for (RectImageWindow window : windowMap.values()) {
            SwingUtilities.invokeLater(window::dispose);
        }
        windowMap.clear();
    }
    
    public static void main(String[] args) {
        RectImageWindowManager manager = new RectImageWindowManager();

        // Create two windows
        manager.createWindow("mainStm", "Main STM",
                "../AllNotations/image/State_Machine_MainStm_MainStmTop.png");
        manager.createWindow("subStm", "Sub STM",
                "../AllNotations/image/State_Machine_SubStm_SubStmTop.png");

        // Add rectangles from any thread
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            manager.addRect("mainStm", "r1", new Rectangle(50, 50, 200, 100));
            manager.addRect("subStm", "r2", new Rectangle(30, 80, 150, 120));

            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            manager.removeRect("mainStm", "r1");
        }).start();
    }
    
}
