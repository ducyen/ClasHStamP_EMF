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

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(imagePanel);
        pack(); // size based on image / preferred size
        setLocationRelativeTo(null); // center on screen
    }

    /**
     * Add or update a red rectangle with a given name.
     * This method is safe to call from any thread.
     * @param rectName unique rectangle name
     * @param rect     rectangle coordinates (x, y, width, height)
     */
    public void addRect(String rectName, Rectangle rect) {
        if (SwingUtilities.isEventDispatchThread()) {
            imagePanel.addRect(rectName, rect);
        } else {
            SwingUtilities.invokeLater(() -> imagePanel.addRect(rectName, rect));
        }
    }

    /**
     * Remove a rectangle by its name.
     * This method is safe to call from any thread.
     * @param rectName name used in addRect
     */
    public void removeRect(String rectName) {
        if (SwingUtilities.isEventDispatchThread()) {
            imagePanel.removeRect(rectName);
        } else {
            SwingUtilities.invokeLater(() -> imagePanel.removeRect(rectName));
        }
    }

    /**
     * Panel that draws the background image and foreground rectangles.
     */
    private static class ImagePanel extends JPanel {

        private BufferedImage backgroundImage;
        private final Map<String, Rectangle> rectMap = new LinkedHashMap<>();

        public ImagePanel(String imagePath) {
            loadImage(imagePath);
        }

        private void loadImage(String path) {
            try {
                backgroundImage = ImageIO.read(new File(path));
                setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
            } catch (IOException e) {
                System.err.println("Could not load image: " + path);
                backgroundImage = null;
                // fallback size
                setPreferredSize(new Dimension(800, 600));
            }
        }

        // These are only called from EDT (enforced by RectImageWindow)
        public void addRect(String name, Rectangle rect) {
            rectMap.put(name, rect);
            repaint();
        }

        public void removeRect(String name) {
            if (rectMap.remove(name) != null) {
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                // Draw background image
                if (backgroundImage != null) {
                    // Draw at (0,0) with original size; adjust if you want scaling
                    g2.drawImage(backgroundImage, 0, 0, this);
                }

                // Draw red rectangles
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(2.0f));

                for (Rectangle r : rectMap.values()) {
                    g2.drawRect(r.x, r.y, r.width, r.height);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
