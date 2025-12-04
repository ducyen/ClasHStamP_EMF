package simulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import all_notations.java_sample00.model.ContextImpl;
import all_notations.java_sample00.model.ContextImpl.EventId;

public class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private ContextImpl context;
    private JButton startButton;
    private JButton[] eventButtons;

    public MainWindow() {
        super("Simulator Main");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        startButton = new JButton("Start");
        top.add(startButton);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // Create event buttons area
        JPanel eventsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        int numEvents = ContextImpl.EventId.Num.ordinal();
        eventButtons = new JButton[numEvents];
        for (int i = 0; i < numEvents; i++) {
            final int idx = i;
            ContextImpl.EventId eid = ContextImpl.EventId.values()[i];
            JButton b = new JButton(eid.name());
            b.setEnabled(false); // disabled until Start
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (context != null) {
                        context.EventProc(ContextImpl.EventId.values()[idx], null);
                    }
                }
            });
            eventButtons[i] = b;
            eventsPanel.add(b);
        }

        center.add(eventsPanel);
        add(center, BorderLayout.CENTER);

        // Start button action
        startButton.addActionListener(e -> onStart());

        pack();
        setLocationByPlatform(true);
    }

    private void onStart() {
        // instantiate context with nulls as allowed
        context = new ContextImpl(null, null, null, null, null, null, null);
        context.Start();
        // enable event buttons
        for (JButton b : eventButtons) b.setEnabled(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setVisible(true);
        });
    }
}
