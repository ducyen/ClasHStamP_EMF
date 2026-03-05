package simulator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.*;

public class RectImageWindow extends JFrame {

    private final ImagePanel imagePanel;

    public RectImageWindow(String title, String imagePath) {
        super(title);
        this.imagePanel = new ImagePanel(imagePath);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(imagePanel);
        pack();
        setLocationRelativeTo(null);
        
		int[] off = readOverlayIni(imagePanel.getBackgroundImageFile());
		if (off != null) {
		    imagePanel.setOverlayOffset(off[0], off[1]);
		}
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
    
	private static File iniFileOf(File pngFile) {
	    return new File(pngFile.getParentFile(), pngFile.getName() + ".ini");
	}
	
	private static int[] readOverlayIni(File pngFile) {
	    File ini = iniFileOf(pngFile);
	    if (!ini.exists()) return null;
	
	    int dx = 0, dy = 0;
	    boolean hasDx = false, hasDy = false;
	
	    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(ini))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	            line = line.trim();
	            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;
	            if (line.startsWith("[") && line.endsWith("]")) continue; // ignore section name
	
	            int eq = line.indexOf('=');
	            if (eq <= 0) continue;
	
	            String key = line.substring(0, eq).trim();
	            String val = line.substring(eq + 1).trim();
	
	            if (key.equalsIgnoreCase("dx")) {
	                dx = Integer.parseInt(val);
	                hasDx = true;
	            } else if (key.equalsIgnoreCase("dy")) {
	                dy = Integer.parseInt(val);
	                hasDy = true;
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        return null;
	    }
	
	    return (hasDx || hasDy) ? new int[]{dx, dy} : null;
	}

    // ---------------- ImagePanel ----------------

    private static class ImagePanel extends JPanel {

        private BufferedImage backgroundImage;
        private final Map<String, Rectangle> rectMap = new LinkedHashMap<>();
        private int[] activePolyline;   // single active polyline
        private int overlayDx = 0;
        private int overlayDy = 0;
        private File backgroundImageFile; // for saving metadata
        
        public ImagePanel(String imagePath) {
            loadImage(imagePath);
        }

        public void setOverlayOffset(int dx, int dy) {
            this.overlayDx = dx;
            this.overlayDy = dy;
            repaint();
        }
        
        public File getBackgroundImageFile() {
			return backgroundImageFile;
		}
        
        private void loadImage(String path) {
            try {
            	backgroundImageFile = new File(path);
                backgroundImage = ImageIO.read(backgroundImageFile);
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

                // ===== ADD THIS (overlay offset) =====
                final int dx = -overlayDx;   // <- tune here
                final int dy = -overlayDy;  // <- tune here
                g2.translate(dx, dy);
                // ====================================

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
