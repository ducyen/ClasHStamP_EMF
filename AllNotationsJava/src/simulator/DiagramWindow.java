package simulator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Lightweight window that shows a diagram image and an overlay of named rectangles.
 */
public class DiagramWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private final BufferedImage baseImage;
    // thread-safe map of stateName -> Rectangle
    private final Map<String, Rectangle> rects = new ConcurrentHashMap<>();

    public DiagramWindow(String title, File imageFile) throws IOException {
        super(title);
        this.baseImage = ImageIO.read(imageFile);
        DrawingPanel panel = new DrawingPanel();
        panel.setPreferredSize(new Dimension(baseImage.getWidth(), baseImage.getHeight()));
        setContentPane(panel);
        pack();
        setLocationByPlatform(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

	public void addNamedRect(String stateName, Rectangle r) {
        if (stateName == null || r == null) return;
        rects.put(stateName, new Rectangle(r));
        repaint();
    }

    public void removeNamedRect(String stateName) {
        if (stateName == null) return;
        rects.remove(stateName);
        repaint();
    }

    public boolean isOpen() {
        return isDisplayable();
    }

    private class DrawingPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(baseImage, 0, 0, null);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new BasicStroke(3f));
                g2.setColor(new Color(0xFF, 0x00, 0x00, 0xFF)); // opaque red
                for (Rectangle r : rects.values()) {
                    if (r.width > 0 && r.height > 0) {
                        g2.drawRect(r.x, r.y, r.width, r.height);
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
