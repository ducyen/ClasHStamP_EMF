package simulator;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import javax.swing.SwingUtilities;

/**
 * Singleton manager for diagram windows used by the simulator.
 *
 * Provides entry/exit policies:
 * - If an Entry is immediately preceded by another Entry in the same instance,
 *   we delay the later Entry by ENTRY_DELAY_MS to allow the parent rectangle to be
 *   drawn first (parent immediate, child delayed).
 * - If an Exit is immediately preceded by another Exit in the same instance,
 *   we delay the later Exit by EXIT_DELAY_MS so child is removed immediately and
 *   parent removed after the delay.
 */
public class SimulatorManager {
    private static final SimulatorManager INSTANCE = new SimulatorManager();

    // map from instance name -> DiagramWindow
    private final Map<String, DiagramWindow> windows = new ConcurrentHashMap<>();

    // scheduled removals keyed by "instanceName\u0000stateName"
    private final Map<String, ScheduledFuture<?>> scheduledRemovals = new ConcurrentHashMap<>();
    // scheduled adds keyed similarly (for delayed child-add)
    private final Map<String, ScheduledFuture<?>> scheduledAdds = new ConcurrentHashMap<>();

    // track last entry/exit timestamps (ms) and state names per instance
    private final Map<String, Long> lastEntryTime = new ConcurrentHashMap<>();
    private final Map<String, String> lastEntryState = new ConcurrentHashMap<>();
    private final Map<String, Long> lastExitTime = new ConcurrentHashMap<>();
    private final Map<String, String> lastExitState = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "simulator-scheduler");
        t.setDaemon(true);
        return t;
    });

    // policy timings
    private static final long ENTRY_DELAY_MS = 500; // delay for child entry
    private static final long EXIT_DELAY_MS = 500;  // delay for parent exit
    private static final long RECENT_THRESHOLD_MS = 200; // consider previous event "recent"

    private SimulatorManager() {}

    public static SimulatorManager getInstance() {
        return INSTANCE;
    }

    /**
     * Ensure a window exists for diagramName (imagePath) and instanceName as title.
     * Returns the DiagramWindow (may be newly created). Non-blocking; runs UI ops on EDT.
     */
    public DiagramWindow ensureWindow(String diagramName, String instanceName) {
        // diagramName corresponds to an image file in AllNotations/image; try to resolve
        String imgPath = resolveImagePath("../AllNotations/image/" + diagramName + ".PNG");
        DiagramWindow existing = windows.get(instanceName);
        if (existing != null && existing.isOpen()) return existing;

        try {
            DiagramWindow w = new DiagramWindow(instanceName, new File(imgPath));
            windows.put(instanceName, w);
            SwingUtilities.invokeLater(() -> w.setVisible(true));
            return w;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String resolveImagePath(String diagramName) {
        // Accept either name or path; default location is project AllNotations/image
        File f = new File(diagramName);
        if (f.exists()) return diagramName;
        // if diagramName already ends with .PNG, use it
        if (diagramName.toUpperCase().endsWith(".PNG")) {
            String candidate = "AllNotations/image/" + diagramName;
            if (new File(candidate).exists()) return candidate;
            return diagramName;
        }
        return "AllNotations/image/" + diagramName + ".PNG";
    }

    // Basic immediate add (cancels scheduled removal)
    // Made private: internal helper only
    private void addRect(String diagramName, String instanceName, String stateName, Rectangle rect) {
        // cancel any scheduled removal for this (instance,state) because we're re-adding it
        String key = makeKey(instanceName, stateName);
        ScheduledFuture<?> f = scheduledRemovals.remove(key);
        if (f != null) {
            f.cancel(false);
        }

        DiagramWindow w = ensureWindow(diagramName, instanceName);
        if (w == null) return;
        SwingUtilities.invokeLater(() -> w.addNamedRect(stateName, rect));
    }

    // Internal remove helper
    private void removeRect(String instanceName, String stateName) {
        // cancel any scheduled removal record (we're doing it now)
        String key = makeKey(instanceName, stateName);
        ScheduledFuture<?> f = scheduledRemovals.remove(key);
        if (f != null) {
            f.cancel(false);
        }
        // also cancel scheduled add for same key
        ScheduledFuture<?> a = scheduledAdds.remove(key);
        if (a != null) a.cancel(false);

        DiagramWindow w = windows.get(instanceName);
        if (w == null) return;
        SwingUtilities.invokeLater(() -> w.removeNamedRect(stateName));
    }

    /**
     * Add rectangle but apply entry policy: if a recent entry occurred in the same instance,
     * delay this add by ENTRY_DELAY_MS (child will be delayed), otherwise add immediately.
     */
    public void addRectWithEntryPolicy(String diagramName, String instanceName, String stateName, Rectangle rect) {
        long now = System.currentTimeMillis();
        Long last = lastEntryTime.get(instanceName);
        String lastState = lastEntryState.get(instanceName);
        boolean recent = last != null && (now - last.longValue()) <= RECENT_THRESHOLD_MS && (lastState == null || !lastState.equals(stateName));

        String key = makeKey(instanceName, stateName);
        // cancel any scheduled removal (we're re-adding)
        ScheduledFuture<?> prevRem = scheduledRemovals.remove(key);
        if (prevRem != null) prevRem.cancel(false);
        // cancel any scheduled add for same key
        ScheduledFuture<?> prevAdd = scheduledAdds.remove(key);
        if (prevAdd != null) prevAdd.cancel(false);

        if (!recent) {
            addRect(diagramName, instanceName, stateName, rect);
        } else {
            // schedule add after ENTRY_DELAY_MS
            ScheduledFuture<?> f = scheduler.schedule(() -> addRect(diagramName, instanceName, stateName, rect), ENTRY_DELAY_MS, TimeUnit.MILLISECONDS);
            scheduledAdds.put(key, f);
        }

        lastEntryTime.put(instanceName, now);
        lastEntryState.put(instanceName, stateName);
    }

    /**
     * Remove rectangle with exit policy: if a recent exit occurred in same instance, delay removal (parent delayed), else remove immediate.
     */
    public void removeRectWithExitPolicy(String instanceName, String stateName) {
        long now = System.currentTimeMillis();
        Long last = lastExitTime.get(instanceName);
        String lastState = lastExitState.get(instanceName);
        boolean recent = last != null && (now - last.longValue()) <= RECENT_THRESHOLD_MS && (lastState == null || !lastState.equals(stateName));

        String key = makeKey(instanceName, stateName);
        // cancel any scheduled add for this key (we are removing)
        ScheduledFuture<?> prevAdd = scheduledAdds.remove(key);
        if (prevAdd != null) prevAdd.cancel(false);

        if (!recent) {
            removeRect(instanceName, stateName);
        } else {
            ScheduledFuture<?> f = scheduler.schedule(() -> removeRect(instanceName, stateName), EXIT_DELAY_MS, TimeUnit.MILLISECONDS);
            scheduledRemovals.put(key, f);
        }

        lastExitTime.put(instanceName, now);
        lastExitState.put(instanceName, stateName);
    }

    private String makeKey(String instanceName, String stateName) {
        return (instanceName != null ? instanceName : "") + '\u0000' + (stateName != null ? stateName : "");
    }
}