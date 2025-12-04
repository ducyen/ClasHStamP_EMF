package simulator;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

/**
 * Singleton manager for diagram windows used by the simulator.
 */
public class SimulatorManager {
    private static final SimulatorManager INSTANCE = new SimulatorManager();

    // map from diagram name -> DiagramWindow
    private final Map<String, DiagramWindow> windows = new ConcurrentHashMap<>();

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
        return "AllNotations/image/" + diagramName + ".PNG";
    }

    public void addRect(String diagramName, String instanceName, String stateName, Rectangle rect) {
        DiagramWindow w = ensureWindow(diagramName, instanceName);
        if (w == null) return;
        SwingUtilities.invokeLater(() -> w.addNamedRect(stateName, rect));
    }

    public void removeRect(String instanceName, String stateName) {
        DiagramWindow w = windows.get(instanceName);
        if (w == null) return;
        SwingUtilities.invokeLater(() -> w.removeNamedRect(stateName));
    }
}
