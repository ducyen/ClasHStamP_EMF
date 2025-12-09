package simulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import design.model.ContextImpl;
import design.model.ContextImpl.onSpeedChangeParams;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    // Context lives on the worker thread
    private volatile ContextImpl context;

    // Worker that owns the context thread
    private ContextWorker contextWorker;

    private JButton startButton;
    private JButton[] eventButtons;

    public MainWindow() {
        super("Simulator Main");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        startButton = new JButton("Start");
        top.add(startButton);
        getContentPane().add(top, BorderLayout.NORTH);

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
                    // send event to context worker thread
                    if (contextWorker != null) {
                        contextWorker.post(() -> {
                            ContextImpl ctx = context;
                            if (ctx != null) {
                                ctx.EventProc(ContextImpl.EventId.values()[idx], null);
                            }
                        });
                    }
                }
            });
            eventButtons[i] = b;
            eventsPanel.add(b);
        }

        center.add(eventsPanel);
        getContentPane().add(center, BorderLayout.CENTER);
        
        JPanel paramsPanel = new JPanel();
        center.add(paramsPanel);
        
        JSpinner spinner = new JSpinner();
        spinner.addChangeListener(new ChangeListener() {
        	public void stateChanged(ChangeEvent e) {
        		int value = (Integer)spinner.getValue();
				// send parameter update to context worker thread
				if (contextWorker != null) {
					contextWorker.post(() -> {
						ContextImpl ctx = context;
						if (ctx != null) {
							onSpeedChangeParams params = new onSpeedChangeParams();
							params.nKmPerHour = value;
                            ctx.EventProc(ContextImpl.EventId.onSpeedChange, params);							
						}
					});
				}
        	}
        });
        spinner.setModel(new SpinnerNumberModel(0, 0, 200, 1));
        paramsPanel.add(spinner);

        // Start button action
        startButton.addActionListener(e -> onStart());

        pack();
        setLocationByPlatform(true);
    }

    private void onStart() {
        // Create worker thread if not created yet
        if (contextWorker == null) {
            contextWorker = new ContextWorker();
            Thread t = new Thread(contextWorker, "ContextWorkerThread");
            t.setDaemon(true);
            t.start();
        }

        // Post context creation + Start() to the worker thread
        contextWorker.post(() -> {
            // You can recreate context each time Start is pressed,
            // or guard it with a null check if you want only once.
        	if (context == null) {
        		context = new ContextImpl(0, "pubAttr", 0, 1, 0, null, null);
        	}
            context.Start();
        });

        // Enable event buttons on UI thread
        for (JButton b : eventButtons) b.setEnabled(true);
    }

    // Optional: shutdown hook if you want to stop the worker explicitly
    @Override
    public void dispose() {
        if (contextWorker != null) {
            contextWorker.shutdown();
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setVisible(true);
        });
    }

    // -------------------------------------------------------
    //            Worker thread that owns ContextImpl
    // -------------------------------------------------------
    private static class ContextWorker implements Runnable {
        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        private volatile boolean running = true;

        void post(Runnable task) {
            if (!running) return;
            queue.offer(task);
        }

        void shutdown() {
            running = false;
            // push a dummy to unblock take()
            queue.offer(() -> {});
        }

        @Override
        public void run() {
            try {
                while (running) {
                    Runnable r = queue.take();
                    if (!running) break;
                    try {
                        r.run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                // exit gracefully
                Thread.currentThread().interrupt();
            }
        }
    }
}
