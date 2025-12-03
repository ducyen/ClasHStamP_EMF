package simulator;

import javax.swing.*;


/**
 * WindowBuilder-friendly JFrame skeleton. Open in WindowBuilder Design view to drag/drop components.
 */
public class DesignMainFrame extends JFrame {
    private static final long serialVersionUID = 1L;
	// Exposed fields so WindowBuilder shows them in the component tree


    public DesignMainFrame() {
    	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    	setTitle("Simulator");
        initComponents();
        // ensure the frame size requested by the user
        setSize(774, 562);
        getContentPane().setLayout(null);
        
        JButton btnNewButton = new JButton("New button");
        btnNewButton.setBounds(44, 44, 91, 21);
        getContentPane().add(btnNewButton);
        
        JLabel lblNewLabel = new JLabel("");
        lblNewLabel.setIcon(new ImageIcon("C:\\Workspace\\EclipseWsp\\papyrus202506a\\CarUmlModel\\CarBody.png"));
        lblNewLabel.setBounds(168, 131, 497, 322);
        getContentPane().add(lblNewLabel);
    }

    private void initComponents() {
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesignMainFrame frame = new DesignMainFrame();
            // enforce the requested size in main as well
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}