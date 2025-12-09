package simulator;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import design.base.EventParams;
import design.model.ContextImpl;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    // Context lives on the worker thread
    private volatile ContextImpl context;

    // Worker that owns the context thread
    private ContextWorker contextWorker;

    private JButton startButton;

    // ---------- Reflection helpers ----------

    private static Class<?> findEventParamsClass(Class<?> contextClass, String eventName) {
        String simpleName = eventName + "Params";
        for (Class<?> nested : contextClass.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(simpleName)) {
                return nested;
            }
        }
        return null;
    }

    private static class ParamBinding {
        Field field;        // field inside params class (or nested class)
        JComponent editor;  // the Swing component to read from
    }

    private static class EventUI {
        ContextImpl.EventId eventId;
        Class<?> paramsClass;       // null if none
        JButton button;             // the event button
        List<ParamBinding> bindings = new ArrayList<>();
    }

    // All events → their UI description
    private final Map<ContextImpl.EventId, EventUI> eventUiMap = new LinkedHashMap<>();

    public MainWindow() {
        super("Simulator Main");
        initUI();
    }

    // ---------- UI building ----------

    private void buildEventRows(JPanel eventsPanel) {
        for (ContextImpl.EventId eid : ContextImpl.EventId.values()) {

            // Skip Num (count value)
            if (eid == ContextImpl.EventId.Num) continue;

            EventUI ui = new EventUI();
            ui.eventId = eid;

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

            Class<?> paramsClass = findEventParamsClass(ContextImpl.class, eid.name());
            ui.paramsClass = paramsClass;

            // If no params → create a button
            if (paramsClass == null) {
                JButton btn = new JButton(eid.name());
                btn.setEnabled(false); // enabled after Start
                ui.button = btn;
                row.add(btn);

                btn.addActionListener(e -> fireEvent(ui));
            }

            // If parameters exist → build editors, no button
            if (paramsClass != null) {
                buildEditorsForType(paramsClass, "", ui.bindings, row, ui);
            }

            eventsPanel.add(row);
            eventUiMap.put(eid, ui);
        }
    }

    private void buildEditorsForType(Class<?> type,
                                     String pathPrefix,
                                     List<ParamBinding> bindings,
                                     JPanel container,
                                     EventUI ui) {
        // If it's an array: for simplicity, create 3 elements editor demo
        if (type.isArray()) {
            Class<?> compType = type.getComponentType();
            int fixedLength = 3; // or decide based on your own rules

            JPanel arrPanel = new JPanel();
            arrPanel.setBorder(BorderFactory.createTitledBorder(pathPrefix + "[]"));
            arrPanel.setLayout(new BoxLayout(arrPanel, BoxLayout.X_AXIS));

            for (int i = 0; i < fixedLength; i++) {
                String idxPrefix = pathPrefix + "[" + i + "].";
                // arrays will be handled specially on writeback; here we just create editors
                buildEditorsForPrimitiveOrClass(compType, idxPrefix, bindings, arrPanel, null, ui);
            }

            container.add(arrPanel);
            return;
        }

        // enum? (top-level)
        if (type.isEnum()) {
            // this case is usually for fields; direct type-level enum param not common
            buildEditorsForPrimitiveOrClass(type, pathPrefix, bindings, container, null, ui);
            return;
        }

        // primitive / wrapper / String / etc
        if (isDirectPrimitiveLike(type)) {
            buildEditorsForPrimitiveOrClass(type, pathPrefix, bindings, container, null, ui);
            return;
        }

        // otherwise: class → recurse through its fields
        for (Field f : type.getDeclaredFields()) {
            f.setAccessible(true);
            Class<?> fType = f.getType();
            String fieldPath = pathPrefix.isEmpty()
                    ? f.getName()
                    : pathPrefix + f.getName();

            buildEditorsForPrimitiveOrClass(fType, fieldPath + ".", bindings, container, f, ui);
        }
    }

    private boolean isDirectPrimitiveLike(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || type == String.class
                || type == Boolean.class
                || type.isEnum();
    }

    private void buildEditorsForPrimitiveOrClass(Class<?> fType,
                                                 String fieldPathPrefix,
                                                 List<ParamBinding> bindings,
                                                 JPanel container,
                                                 Field field,
                                                 EventUI ui) {
        // arrays and nested classes are handled by buildEditorsForType
        if (fType.isArray() || (!isDirectPrimitiveLike(fType) && !fType.isEnum())) {
            // Nested complex type
            JPanel group = new JPanel();
            group.setBorder(BorderFactory.createTitledBorder(fieldPathPrefix));
            group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
            container.add(group);

            buildEditorsForType(fType, fieldPathPrefix, bindings, group, ui);
            return;
        }

        // direct primitive-like field
        String labelText = (field != null) ? field.getName() : fieldPathPrefix;
        JLabel label = new JLabel(labelText + ":");
        container.add(label);

        ParamBinding binding = new ParamBinding();
        binding.field = field;

        JComponent editor;

        if (fType == int.class || fType == Integer.class
                || fType == long.class || fType == Long.class
                || fType == short.class || fType == Short.class
                || fType == byte.class || fType == Byte.class) {

            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(5);
            editor = spinner;

        } else if (fType == double.class || fType == Double.class
                || fType == float.class || fType == Float.class
                || fType == String.class) {

            JTextField tf = new JTextField(8);
            editor = tf;

        } else if (fType == boolean.class || fType == Boolean.class) {

            JCheckBox cb = new JCheckBox();
            editor = cb;

        } else if (fType.isEnum()) {

            Object[] constants = fType.getEnumConstants();
            JComboBox<Object> combo = new JComboBox<>(constants);
            editor = combo;

        } else {
            // fallback: text field
            JTextField tf = new JTextField(8);
            editor = tf;
        }

        binding.editor = editor;
        bindings.add(binding);
        container.add(editor);

        // attach live-change listener to non-button components
        attachLiveUpdateListener(binding, ui);
    }

    // ---------- Attach listeners for non-button components ----------

    private void attachLiveUpdateListener(ParamBinding b, EventUI ui) {
        JComponent editor = b.editor;

        if (editor instanceof JSpinner) {
            ((JSpinner) editor).addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    fireEvent(ui);
                }
            });
        } else if (editor instanceof JCheckBox) {
            ((JCheckBox) editor).addActionListener(e -> fireEvent(ui));
        } else if (editor instanceof JComboBox) {
            ((JComboBox<?>) editor).addActionListener(e -> fireEvent(ui));
        } else if (editor instanceof JTextField) {
            ((JTextField) editor).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { fireEvent(ui); }
                @Override
                public void removeUpdate(DocumentEvent e) { fireEvent(ui); }
                @Override
                public void changedUpdate(DocumentEvent e) { fireEvent(ui); }
            });
        }
    }

    // ---------- Event firing ----------

    private void fireEvent(EventUI ui) {
        if (context == null) return;

        Runnable task = () -> {
            ContextImpl ctx = context;
            try {
                EventParams params = null;

                if (ui.paramsClass != null) {
                    Object obj = ui.paramsClass.getDeclaredConstructor().newInstance();

                    // set all bound fields
                    for (ParamBinding b : ui.bindings) {
                        if (b.field == null) {
                            continue; // complex / array paths to be handled if you expand this
                        }

                        Object value = readEditorValue(b.editor, b.field.getType());
                        b.field.setAccessible(true);
                        b.field.set(obj, value);
                    }

                    params = (EventParams) obj;
                }

                ctx.EventProc(ui.eventId, params);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };

        // post to worker thread if you have one
        if (contextWorker != null) {
            contextWorker.post(task);
        } else {
            task.run();
        }
    }

    private Object readEditorValue(JComponent editor, Class<?> type) {
        if (editor instanceof JSpinner) {
            Number n = (Number) ((JSpinner) editor).getValue();
            if (type == int.class || type == Integer.class) return n.intValue();
            if (type == long.class || type == Long.class)   return n.longValue();
            if (type == short.class || type == Short.class) return n.shortValue();
            if (type == byte.class || type == Byte.class)   return n.byteValue();
            return n;
        } else if (editor instanceof JCheckBox) {
            boolean v = ((JCheckBox) editor).isSelected();
            return (type == boolean.class || type == Boolean.class) ? v : v;
        } else if (editor instanceof JComboBox) {
            return ((JComboBox<?>) editor).getSelectedItem();
        } else if (editor instanceof JTextField) {
            String s = ((JTextField) editor).getText();
            if (type == String.class) return s;
            try {
                if (type == double.class || type == Double.class) return Double.parseDouble(s);
                if (type == float.class || type == Float.class)   return Float.parseFloat(s);
                if (type == int.class || type == Integer.class)   return Integer.parseInt(s);
                if (type == long.class || type == Long.class)     return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return 0;
            }
            return s;
        }
        return null;
    }

    // ---------- initUI & lifecycle ----------

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        startButton = new JButton("Start");
        top.add(startButton);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // === dynamic event UI generation ===
        JPanel eventsPanel = new JPanel();
        eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));

        buildEventRows(eventsPanel);

        center.add(eventsPanel);
        add(center, BorderLayout.CENTER);

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
            if (context == null) {
                context = new ContextImpl(0, "pubAttr", 0, 1, 0, null, null);
            }
            context.Start();
        });

        // Enable event buttons on UI thread (only those that exist)
        for (EventUI ui : eventUiMap.values()) {
            if (ui.button != null) {
                ui.button.setEnabled(true);
            }
        }
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
