# Simulator WindowBuilder skeleton

This project contains a WindowBuilder-friendly Swing skeleton you can open in Eclipse and edit with the WYSIWYG designer.

Files added:
- `src/simulator/DesignMainFrame.java` — a JFrame with exposed component fields and `initComponents()` so WindowBuilder can render and edit the form visually.
- `src/simulator/Launcher.java` — a small launcher to run the frame.
- `src/module-info.java` — updated to `requires java.desktop;` so Swing compiles when using the module system.

How to use WindowBuilder:
1. Install WindowBuilder (Help → Eclipse Marketplace → WindowBuilder → Install).
2. Open `DesignMainFrame.java` in Package Explorer, Right-click → Open With → WindowBuilder Editor.
3. Use the Design tab to drag/drop components, set properties, and double-click buttons to auto-create event handlers.

How to run:
- From Eclipse: Right-click `Launcher.java` → Run As → Java Application.
- From command-line (compile & run):

```cmd
cd /d C:\Workspace\EclipseWsp\papyrus202506a\Simulator
javac -d out\classes src\simulator\*.java
java -cp out\classes simulator.Launcher
```

If you want the designer to show the car image, copy `CarBody.png` into `src` (project root) so it becomes a classpath resource and WindowBuilder will preview it when you set the JLabel icon from the workspace.
