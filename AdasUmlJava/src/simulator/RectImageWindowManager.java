package simulator;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RectImageWindowManager {

    private final Map<String, RectImageWindow> windowMap = new ConcurrentHashMap<>();

    /**
     * Create (and show) a window with the given name.
     * If it already exists, it is just brought to front.
     *
     * This method is synchronous:
     *  - If called from EDT: runs directly.
     *  - If called from another thread: uses invokeAndWait and returns
     *    only after the window is created and stored in windowMap.
     */
    public void createWindow(String windowName, String title, String imagePath) {
        Runnable task = () -> {
            RectImageWindow existing = windowMap.get(windowName);
            if (existing != null) {
                existing.toFront();
                existing.requestFocus();
                return;
            }

            RectImageWindow window = new RectImageWindow(title, imagePath);
            windowMap.put(windowName, window);
            window.setVisible(true);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            // Already on EDT → run directly
            task.run();
        } else {
            // From worker thread → block until window is created
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public RectImageWindow getWindow(String windowName) {
        return windowMap.get(windowName);
    }

    public void addRect(String windowName, String rectName, Rectangle rect) {
        RectImageWindow window = windowMap.get(windowName);
        if (window != null) {
            window.addRect(rectName, rect);
        } else {
            System.err.println("No window named: " + windowName);
        }
    }

    public void removeRect(String windowName, String rectName) {
        RectImageWindow window = windowMap.get(windowName);
        if (window != null) {
            window.removeRect(rectName);
        }
    }

    public void closeWindow(String windowName) {
        RectImageWindow window = windowMap.remove(windowName);
        if (window != null) {
            SwingUtilities.invokeLater(window::dispose);
        }
    }

    public void closeAll() {
        for (RectImageWindow w : windowMap.values()) {
            SwingUtilities.invokeLater(w::dispose);
        }
        windowMap.clear();
    }
    
    public void addPolyline(String windowName, int[] coords) {
        RectImageWindow window = windowMap.get(windowName);
        if (window != null) {
            window.addPolyline(coords);
        } else {
            System.err.println("No window named: " + windowName);
        }
    }

    public void clearPolyline(String windowName) {
        RectImageWindow window = windowMap.get(windowName);
        if (window != null) {
            window.clearPolyline();
        }
    }
    
}
