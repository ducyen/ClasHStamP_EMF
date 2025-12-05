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

    // scheduled removals keyed by "instanceName\u0000regionKey\u0000stateName"
    private final Map<String, ScheduledFuture<?>> scheduledRemovals = new ConcurrentHashMap<>();
    // scheduled adds keyed similarly (for delayed child-add)
    private final Map<String, ScheduledFuture<?>> scheduledAdds = new ConcurrentHashMap<>();

    // keep-removals scheduled by DefaultDoingAction (per-instance+region+state)
    private final Map<String, ScheduledFuture<?>> keepRemovals = new ConcurrentHashMap<>();

    // track last entry/exit timestamps (ms) and state names per instance+region
    private final Map<String, Long> lastEntryTime = new ConcurrentHashMap<>();
    private final Map<String, String> lastEntryState = new ConcurrentHashMap<>();
    private final Map<String, Long> lastExitTime = new ConcurrentHashMap<>();
    private final Map<String, String> lastExitState = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "simulator-scheduler");
        t.setDaemon(true);
        return t;
    });

    // per-region single-thread schedulers so each region runs on its own thread
    private final Map<String, ScheduledExecutorService> regionSchedulers = new ConcurrentHashMap<>();

    private ScheduledExecutorService getRegionScheduler(String regionInstanceKey) {
        if (regionInstanceKey == null) regionInstanceKey = "";
        return regionSchedulers.computeIfAbsent(regionInstanceKey, k -> Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-region-" + Math.abs(k.hashCode()));
            t.setDaemon(true);
            return t;
        }));
    }

    // policy timings
    private static final long ENTRY_DELAY_MS = 500; // delay for child entry
    private static final long EXIT_DELAY_MS = 500;  // delay for parent exit
    private static final long RECENT_THRESHOLD_MS = 500; // consider previous event "recent" (per-region)

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
    private void addRect(String diagramName, String instanceName, String regionKey, String stateName, Rectangle rect) {
        // cancel any scheduled removal for this (instance,region,state) because we're re-adding it
        String key = makeKey(instanceName, regionKey, stateName);
        ScheduledFuture<?> f = scheduledRemovals.remove(key);
        if (f != null) {
            f.cancel(false);
        }

        DiagramWindow w = ensureWindow(diagramName, instanceName);
        if (w == null) return;
        SwingUtilities.invokeLater(() -> w.addNamedRect(key, rect));
    }

    // Internal remove helper
    private void removeRect(String instanceName, String regionKey, String stateName) {
        // cancel any scheduled removal record (we're doing it now)
        String key = makeKey(instanceName, regionKey, stateName);
        ScheduledFuture<?> f = scheduledRemovals.remove(key);
        if (f != null) {
            f.cancel(false);
        }
        // also cancel scheduled add for same key
        ScheduledFuture<?> a = scheduledAdds.remove(key);
        if (a != null) a.cancel(false);

        DiagramWindow w = windows.get(instanceName);
        if (w == null) return;
        SwingUtilities.invokeLater(() -> w.removeNamedRect(key));
    }

    /**
     * Add rectangle but apply entry policy: if a recent entry occurred in the same instance+region,
     * delay this add by ENTRY_DELAY_MS (child will be delayed), otherwise add immediately.
     * Dispatches logic to the region's single-thread scheduler so different regions run concurrently.
     */
    public void addRectWithEntryPolicy(String diagramName, String instanceName, String regionKey, String stateName, Rectangle rect) {
        long now = System.currentTimeMillis();
        String regionInstanceKey = makeRegionInstanceKey(instanceName, regionKey);
        ScheduledExecutorService rs = getRegionScheduler(regionInstanceKey);

        rs.execute(() -> {
            Long last = lastEntryTime.get(regionInstanceKey);
            String lastState = lastEntryState.get(regionInstanceKey);
            boolean recent = last != null && (now - last.longValue()) <= RECENT_THRESHOLD_MS && (lastState == null || !lastState.equals(stateName));

            String key = makeKey(instanceName, regionKey, stateName);
            // cancel any scheduled removal (we're re-adding)
            ScheduledFuture<?> prevRem = scheduledRemovals.remove(key);
            if (prevRem != null) prevRem.cancel(false);
            // cancel any scheduled add for same key
            ScheduledFuture<?> prevAdd = scheduledAdds.remove(key);
            if (prevAdd != null) prevAdd.cancel(false);

            if (!recent) {
                addRect(diagramName, instanceName, regionKey, stateName, rect);
            } else {
                // schedule add after ENTRY_DELAY_MS on this region scheduler
                ScheduledFuture<?> f = rs.schedule(() -> addRect(diagramName, instanceName, regionKey, stateName, rect), ENTRY_DELAY_MS, TimeUnit.MILLISECONDS);
                scheduledAdds.put(key, f);
            }

            lastEntryTime.put(regionInstanceKey, now);
            lastEntryState.put(regionInstanceKey, stateName);
        });
    }

    /**
     * Remove rectangle with exit policy: if a recent exit occurred in same instance+region, delay removal (parent delayed), else remove immediate.
     * Dispatches logic to the region's scheduler to keep region operations serialized.
     */
    public void removeRectWithExitPolicy(String instanceName, String regionKey, String stateName) {
        long now = System.currentTimeMillis();
        String regionInstanceKey = makeRegionInstanceKey(instanceName, regionKey);
        ScheduledExecutorService rs = getRegionScheduler(regionInstanceKey);

        rs.execute(() -> {
            Long last = lastExitTime.get(regionInstanceKey);
            String lastState = lastExitState.get(regionInstanceKey);
            boolean recent = last != null && (now - last.longValue()) <= RECENT_THRESHOLD_MS && (lastState == null || !lastState.equals(stateName));

            String key = makeKey(instanceName, regionKey, stateName);
            // cancel any scheduled add for this key (we are removing)
            ScheduledFuture<?> prevAdd = scheduledAdds.remove(key);
            if (prevAdd != null) prevAdd.cancel(false);
            // cancel any keep-removal for this key (we're performing explicit removal now)
            ScheduledFuture<?> kr = keepRemovals.remove(key);
            if (kr != null) kr.cancel(false);

            if (!recent) {
                removeRect(instanceName, regionKey, stateName);
            } else {
                ScheduledFuture<?> f = rs.schedule(() -> removeRect(instanceName, regionKey, stateName), EXIT_DELAY_MS, TimeUnit.MILLISECONDS);
                scheduledRemovals.put(key, f);
            }

            lastExitTime.put(regionInstanceKey, now);
            lastExitState.put(regionInstanceKey, stateName);
        });
    }

    /**
     * Called by DefaultDoingAction: ensure the given rect stays visible for at least durationMs.
     * Each call resets the timeout. Removal (DefaultExitAction) cancels the keep.
     * This scheduling is done on the region scheduler so it's serialized with add/remove.
     */
    public void keepRectFor(String instanceName, String regionKey, String stateName, long durationMs) {
        String regionInstanceKey = makeRegionInstanceKey(instanceName, regionKey);
        ScheduledExecutorService rs = getRegionScheduler(regionInstanceKey);
        rs.execute(() -> {
            String key = makeKey(instanceName, regionKey, stateName);
            // cancel any scheduled removal or keep-removal and re-schedule
            ScheduledFuture<?> prevRem = scheduledRemovals.remove(key);
            if (prevRem != null) prevRem.cancel(false);
            ScheduledFuture<?> prevKeep = keepRemovals.remove(key);
            if (prevKeep != null) prevKeep.cancel(false);

            ScheduledFuture<?> f = rs.schedule(() -> removeRect(instanceName, regionKey, stateName), durationMs, TimeUnit.MILLISECONDS);
            keepRemovals.put(key, f);
        });
    }

    private String makeKey(String instanceName, String regionKey, String stateName) {
        return (instanceName != null ? instanceName : "") + ',' + (regionKey != null ? regionKey : "") + ',' + (stateName != null ? stateName : "");
    }

    private String makeRegionInstanceKey(String instanceName, String regionKey) {
        return (instanceName != null ? instanceName : "") + ',' + (regionKey != null ? regionKey : "");
    }
}
