package simulator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class RectImageWindow extends JFrame {

    private final ImagePanel imagePanel;

    public RectImageWindow(String title, String imagePath) {
        super(title);
        this.imagePanel = new ImagePanel(imagePath);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(imagePanel);
        pack();
        setLocationRelativeTo(null);
    }

    // ---------------- Rectangles (unchanged) ----------------

    public void addRect(String rectName, Rectangle rect) {
        if (SwingUtilities.isEventDispatchThread()) {
            imagePanel.addRect(rectName, rect);
        } else {
            SwingUtilities.invokeLater(() -> imagePanel.addRect(rectName, rect));
        }
    }

    public void removeRect(String rectName) {
        if (SwingUtilities.isEventDispatchThread()) {
            imagePanel.removeRect(rectName);
        } else {
            SwingUtilities.invokeLater(() -> imagePanel.removeRect(rectName));
        }
    }

    // ---------------- NEW: Polyline wrapper ----------------

    /**
     * Show a single active polyline on this window.
     * Any previously drawn polyline will be removed automatically.
     */
    public void addPolyline(int[] coords) {
        if (SwingUtilities.isEventDispatchThread()) {
            imagePanel.addPolyline(coords);
        } else {
            SwingUtilities.invokeLater(() -> imagePanel.addPolyline(coords));
        }
    }

    // (optional) if you still want explicit removal:
    public void clearPolyline() {
        if (SwingUtilities.isEventDispatchThread()) {
            imagePanel.clearPolyline();
        } else {
            SwingUtilities.invokeLater(imagePanel::clearPolyline);
        }
    }

    // ---------------- ImagePanel ----------------

    private static class ImagePanel extends JPanel {

        private BufferedImage backgroundImage;
        private final Map<String, Rectangle> rectMap = new LinkedHashMap<>();
        private int[] activePolyline;   // single active polyline

        public ImagePanel(String imagePath) {
            loadImage(imagePath);
        }

        private void loadImage(String path) {
            try {
                backgroundImage = ImageIO.read(new File(path));
                setPreferredSize(new Dimension(
                        backgroundImage.getWidth(),
                        backgroundImage.getHeight()));
            } catch (IOException e) {
                System.err.println("Could not load image: " + path);
                backgroundImage = null;
                setPreferredSize(new Dimension(800, 600));
            }
        }

        // Rectangles
        void addRect(String name, Rectangle rect) {
            rectMap.put(name, rect);
            repaint();
        }

        void removeRect(String name) {
            if (rectMap.remove(name) != null) {
                repaint();
            }
        }

        // NEW: polyline handling (single active)
        void addPolyline(int[] coords) {
            this.activePolyline = coords;
            repaint();
        }

        void clearPolyline() {
            if (this.activePolyline != null) {
                this.activePolyline = null;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (backgroundImage != null) {
                    g2.drawImage(backgroundImage, 0, 0, this);
                }

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                // Rectangles
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(2.0f));
                for (Rectangle r : rectMap.values()) {
                    g2.drawRoundRect(r.x, r.y, r.width, r.height, 20, 20);
                }

                // Single polyline
                if (activePolyline != null && activePolyline.length >= 4) {
                    int n = activePolyline.length / 2;
                    int[] xs = new int[n];
                    int[] ys = new int[n];
                    for (int i = 0; i < n; i++) {
                        xs[i] = activePolyline[2 * i];
                        ys[i] = activePolyline[2 * i + 1];
                    }
                    g2.drawPolyline(xs, ys, n);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
