package simulator;

import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ModelExecutor {

    // ---------------------------------------------------------
    //                 SINGLETON INSTANCE
    // ---------------------------------------------------------
    private static final ModelExecutor instance = new ModelExecutor();

    public static ModelExecutor getInstance() {
        return instance;
    }

    // PRIVATE constructor → prevents external creation
    private ModelExecutor() {
        this.windowManager = new RectImageWindowManager();
    }

    // ---------------------------------------------------------
    //                 FIELDS
    // ---------------------------------------------------------
    private final RectImageWindowManager windowManager;

    // [windowName|regionName] -> Thread
    private final Map<String, Thread> threadMap = new ConcurrentHashMap<>();

    // [windowName|regionName] -> RegionWorker
    private final Map<String, RegionWorker> workerMap = new ConcurrentHashMap<>();

    // [windowName|regionName|stateName] -> Rectangle
    private final Map<String, Rectangle> stateRectMap = new ConcurrentHashMap<>();

    // ---------------------------------------------------------
    //                 WINDOW CREATION WRAPPER
    // ---------------------------------------------------------
    public void createWindow(String windowName, String title, String imagePath) {
        windowManager.createWindow(windowName, title, imagePath);
    }

    // ---------------------------------------------------------
    //           Register rectangle (optional)
    // ---------------------------------------------------------
    public void registerStateRect(String windowName,
                                  String regionName,
                                  String stateName,
                                  Rectangle rect) {
        stateRectMap.put(stateKey(windowName, regionName, stateName), rect);
    }

    // ---------------------------------------------------------
    //        PUBLIC API — ADD / REMOVE RECTANGLES
    // ---------------------------------------------------------

    // automatic registration is supported
    public void addRect(String windowName,
                        String regionName,
                        String stateName,
                        Rectangle rect) {
        String key = stateKey(windowName, regionName, stateName);
        stateRectMap.putIfAbsent(key, rect);
        addRect(windowName, regionName, stateName);
    }

    public void addRect(String windowName, String regionName, String stateName) {
        RegionWorker worker = ensureWorker(windowName, regionName);
        worker.enqueueAdd(stateName);
    }

    public void removeRect(String windowName, String regionName, String stateName) {
        RegionWorker worker = ensureWorker(windowName, regionName);
        worker.enqueueRemove(stateName);
    }

    // ---------------------------------------------------------
    //             SHUTDOWN CONTROL
    // ---------------------------------------------------------
    public void shutdownRegion(String windowName, String regionName) {
        String key = regionKey(windowName, regionName);
        RegionWorker worker = workerMap.remove(key);
        Thread t = threadMap.remove(key);
        if (worker != null) worker.shutdown();
        if (t != null) t.interrupt();
    }

    public void shutdownAll() {
        for (RegionWorker w : workerMap.values()) w.shutdown();
        for (Thread t : threadMap.values()) t.interrupt();
        workerMap.clear();
        threadMap.clear();
    }

    // ---------------------------------------------------------
    //                INTERNAL HELPERS
    // ---------------------------------------------------------

    private static String regionKey(String windowName, String regionName) {
        return windowName + "|" + regionName;
    }

    private static String stateKey(String windowName, String regionName, String stateName) {
        return windowName + "|" + regionName + "|" + stateName;
    }

    private RegionWorker ensureWorker(String windowName, String regionName) {
        String key = regionKey(windowName, regionName);

        RegionWorker existing = workerMap.get(key);
        if (existing != null)
            return existing;

        synchronized (this) {
            existing = workerMap.get(key);
            if (existing != null)
                return existing;

            RegionWorker worker = new RegionWorker(windowName, regionName);
            Thread t = new Thread(worker, "RegionWorker-" + key);
            workerMap.put(key, worker);
            threadMap.put(key, t);
            t.start();
            return worker;
        }
    }

    // ---------------------------------------------------------
    //                  REGION WORKER THREAD
    // ---------------------------------------------------------
    private class RegionWorker implements Runnable {

        private final String windowName;
        private final String regionName;
        private final BlockingQueue<Command> queue = new LinkedBlockingQueue<>();
        private volatile boolean running = true;

        RegionWorker(String windowName, String regionName) {
            this.windowName = windowName;
            this.regionName = regionName;
        }

        void enqueueAdd(String stateName) {
            queue.offer(new Command(CommandType.ADD, stateName));
        }

        void enqueueRemove(String stateName) {
            queue.offer(new Command(CommandType.REMOVE, stateName));
        }

        void shutdown() {
            running = false;
            queue.offer(new Command(CommandType.SHUTDOWN, null));
        }

        @Override
        public void run() {
            try {
                while (running) {
                    Command cmd = queue.take();
                    if (!running || cmd.type == CommandType.SHUTDOWN) break;

                    switch (cmd.type) {
                        case ADD -> handleAdd(cmd.stateName);
                        case REMOVE -> handleRemove(cmd.stateName);
                    }
                    
                    // ----------------------------------------
                    //   ADD 500ms delay BEFORE next command
                    // ----------------------------------------
                    Thread.sleep(500);                    
                }
            } catch (InterruptedException ignored) {}
        }

        private void handleAdd(String stateName) {
            Rectangle rect = stateRectMap.get(
                    stateKey(windowName, regionName, stateName));

            if (rect == null) {
                System.err.println("No rect registered for "
                        + windowName + "/" + regionName + "/" + stateName);
                return;
            }

            String rectName = regionName + ":" + stateName;
            windowManager.addRect(windowName, rectName, rect);
        }

        private void handleRemove(String stateName) {
            String rectName = regionName + ":" + stateName;
            windowManager.removeRect(windowName, rectName);
        }
    }

    // ---------------------------------------------------------
    //                  COMMAND STRUCTURE
    // ---------------------------------------------------------
    private enum CommandType { ADD, REMOVE, SHUTDOWN }

    private static class Command {
        final CommandType type;
        final String stateName;

        Command(CommandType type, String stateName) {
            this.type = type;
            this.stateName = stateName;
        }
    }

    public static void main(String[] args) {
        // Create ModelExecutor
        ModelExecutor executor = ModelExecutor.getInstance();

        // Create a window
        executor.createWindow(
                "mainStm",
                "Main STM",
                "../AllNotations/image/State_Machine_MainStm_MainStmTop.png"
        );


        // Simulate from another thread
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        // FIRST time: provide the Rectangle so it can auto-register
        executor.addRect(
                "mainStm",
                "RegionA",
                "State1",
                new Rectangle(50, 50, 200, 100)
        );

        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        executor.removeRect("mainStm", "RegionA", "State1");

        // FIRST time for State2: also provide the Rectangle
        executor.addRect(
                "mainStm",
                "RegionA",
                "State2",
                new Rectangle(300, 200, 150, 120)
        );

        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        executor.removeRect("mainStm", "RegionA", "State2");
        executor.shutdownAll();
    }
    
}
