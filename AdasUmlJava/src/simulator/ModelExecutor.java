package simulator;

import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModelExecutor {

    // -------------------- Singleton --------------------
    private static final ModelExecutor instance = new ModelExecutor();

    public static ModelExecutor getInstance() {
        return instance;
    }

    private ModelExecutor() {
        this.windowManager = new RectImageWindowManager();
    }

    // -------------------- Fields --------------------
    private final RectImageWindowManager windowManager;

    // [windowName|regionName|stateName] -> Rectangle
    private final Map<String, Rectangle> stateRectMap = new ConcurrentHashMap<>();

    // -------------------- Helper keys --------------------
    private static String stateKey(String windowName, String regionName, String stateName) {
        return windowName + "|" + regionName + "|" + stateName;
    }

    // -------------------- Window wrapper --------------------
    public void createWindow(String windowName, String title, String imagePath) {
        windowManager.createWindow(windowName, title, imagePath);
    }

    // -------------------- Public API: add/remove rect --------------------

    /**
     * Auto-register rect if not already registered, then show it.
     */
    public void addRect(String windowName,
                        String regionName,
                        String stateName,
                        Rectangle rect) {
        String key = stateKey(windowName, regionName, stateName);
        stateRectMap.putIfAbsent(key, rect);
        addRect(windowName, regionName, stateName);
    }

    /**
     * Show rect for a previously registered (windowName, regionName, stateName).
     */
    public void addRect(String windowName,
                        String regionName,
                        String stateName) {
        String key = stateKey(windowName, regionName, stateName);
        Rectangle rect = stateRectMap.get(key);
        if (rect == null) {
            System.err.println("No rect registered for "
                    + windowName + "/" + regionName + "/" + stateName);
            return;
        }

        String rectName = regionName + ":" + stateName;
        windowManager.addRect(windowName, rectName, rect);
    }

    public void removeRect(String windowName,
                           String regionName,
                           String stateName) {
        String rectName = regionName + ":" + stateName;
        windowManager.removeRect(windowName, rectName);
    }

    // Optional: shutdown helpers
    public void closeWindow(String windowName) {
        windowManager.closeWindow(windowName);
    }

    public void closeAllWindows() {
        windowManager.closeAll();
    }
    
    /**
     * Show a single active polyline on the given window.
     * Any previously drawn polyline on that window is overwritten.
     */
    public void addPolyline(String windowName, int[] coords) {
        if (coords == null || coords.length < 4) {
            System.err.println("ModelExecutor.addPolyline: invalid coords");
            return;
        }
        windowManager.addPolyline(windowName, coords);
    }

    /**
     * Optional: clear current polyline on a window.
     */
    public void clearPolyline(String windowName) {
        windowManager.clearPolyline(windowName);
    }
    
}
