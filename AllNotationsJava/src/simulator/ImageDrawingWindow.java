package simulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class ImageDrawingWindow {

    // *** CHANGE THIS to your image path ***
    private static final String IMAGE_PATH = "C:\\temp\\sample.png";

    private JFrame mainFrame;
    private JTextField titleField;
    private JButton openButton;

    // Keep track of open image windows by title
    private final Map<String, ImageWindow> windowsByTitle = new HashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ImageDrawingWindow().createAndShowGUI());
    }

    private void createAndShowGUI() {
        mainFrame = new JFrame("Main Window");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        titleField = new JTextField("My Image Window");
        openButton = new JButton("Open Image Window");

        openButton.addActionListener(e -> onOpenImageWindow());

        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Window title:"), BorderLayout.WEST);
        panel.add(titleField, BorderLayout.CENTER);
        panel.add(openButton, BorderLayout.EAST);

        mainFrame.setContentPane(panel);
        mainFrame.pack();
        mainFrame.setLocationByPlatform(true);
        mainFrame.setVisible(true);
    }

    private void onOpenImageWindow() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Please enter a title first.",
                    "No Title", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // If a window with this title already exists and is still displayable, do nothing
        ImageWindow existing = windowsByTitle.get(title);
        if (existing != null && existing.isDisplayable()) {
            // Optionally bring to front:
            existing.toFront();
            existing.requestFocus();
            return;
        }

        // Otherwise create a new window
        try {
            BufferedImage img = ImageIO.read(new File("../AllNotations/image/State_Machine_MainStm_MainStmTop.PNG"));
            ImageWindow w = new ImageWindow(mainFrame, title, img);

            // When this window is disposed, remove it from the map
            w.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    windowsByTitle.remove(title);
                }
            });

            windowsByTitle.put(title, w);
            w.setVisible(true); // non-modal
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(mainFrame,
                    "Failed to load image:\n" + ex.getMessage(),
                    "Image Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============================================================
    // Image window: shows an image, draws a hard-coded rectangle,
    // and allows freehand drawing with the mouse.
    // ============================================================
    private static class ImageWindow extends JFrame {

        private final BufferedImage baseImage;
        private final BufferedImage drawLayer;

        public ImageWindow(Frame owner, String title, BufferedImage image) {
            super(title);
            this.baseImage = image;
            this.drawLayer = new BufferedImage(
                    image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);

            // Draw a hard-coded rectangle on the drawLayer
            drawInitialRectangle();

            DrawingPanel panel = new DrawingPanel();
            panel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

            setContentPane(panel);
            pack();
            setLocationRelativeTo(owner);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE); // non-modal, only this window closes
        }

        private void drawInitialRectangle() {
            Graphics2D g2 = drawLayer.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(3f));
                // Hard-coded rectangle position & size
                int x = 50;
                int y = 50;
                int w = Math.min(200, baseImage.getWidth() - 60);
                int h = Math.min(150, baseImage.getHeight() - 60);
                g2.drawRect(x, y, w, h);
            } finally {
                g2.dispose();
            }
        }

        private class DrawingPanel extends JPanel
                implements MouseListener, MouseMotionListener {

            private int lastX = -1;
            private int lastY = -1;

            DrawingPanel() {
                addMouseListener(this);
                addMouseMotionListener(this);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(baseImage, 0, 0, null);
                g.drawImage(drawLayer, 0, 0, null);
            }

            private void drawSegment(int x1, int y1, int x2, int y2) {
                Graphics2D g2 = drawLayer.createGraphics();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setStroke(new BasicStroke(3f,
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(Color.BLUE); // freehand drawing color
                    g2.drawLine(x1, y1, x2, y2);
                } finally {
                    g2.dispose();
                }
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastX = lastY = -1;
            }

            @Override public void mouseClicked(MouseEvent e) {}
            @Override public void mouseEntered(MouseEvent e) {}
            @Override public void mouseExited(MouseEvent e) {}

            @Override
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                if (lastX >= 0 && lastY >= 0) {
                    drawSegment(lastX, lastY, x, y);
                }
                lastX = x;
                lastY = y;
            }

            @Override public void mouseMoved(MouseEvent e) {}
        }
    }
}
