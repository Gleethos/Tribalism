package app.engine.world.demo;

import app.engine.primitives.CameraF64;
import app.engine.primitives.VecF64;
import app.engine.world.EngineInputs;
import app.engine.world.Key;
import app.engine.world.PointerButton;
import app.engine.world.PointerId;
import app.engine.world.Screen;
import app.engine.world.ScreenId;
import app.engine.world.ScreenInputEvent;
import app.engine.world.ScreenInputs;
import app.engine.world.World;
import app.engine.world.gen.WorldGenerator;
import app.engine.world.render.FrameStats;
import app.engine.world.render.Graphics2DRenderer;
import app.engine.world.render.Renderer;
import app.engine.world.render.gl.GlRenderer;
import org.jspecify.annotations.Nullable;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *  A self-contained demo of the world engine: it procedurally generates a small
 *  landscape and renders it through one {@link Screen}.
 *  <p>
 *  This version drives everything through the real engine pipeline. The world owns a
 *  camera entity and a screen bound to it; the demo merely <i>translates</i> raw Swing
 *  input into {@link ScreenInputEvent}s, hands them to {@link World#update} once per
 *  frame, and asks the {@link WorldRenderer} to draw the screen. The free-fly camera
 *  logic itself lives in the engine ({@code CameraFlight}), not here.
 *  <p>
 *  The camera slowly orbits on its own until you press a movement key (W/A/S/D, Q/E or
 *  Space, Shift to sprint) or move the mouse, at which point control passes to you.
 *  Run {@link #main(String[])} to see it.
 */
public final class WorldEngineDemo
{
    private static final long     CAMERA_ID = 1L;
    private static final ScreenId SCREEN_ID = ScreenId.of(1L);
    private static final int      INITIAL_W = 960, INITIAL_H = 600;
    private static final double   ORBIT_RADIUS = 96, ORBIT_HEIGHT = 40;

    public static void main( String[] args ) {
        System.out.println("Generating world...");
        long start = System.currentTimeMillis();

        // The world is infinite: it owns the generator and streams 64-unit chunks in around
        // cameras within the generation distance, growing its tree outward as you fly. There is
        // no fixed region; grow/shrink the generation distance to trade view range for cost.
        WorldGenerator generator = WorldGenerator.withSeed(1337L)
                                                 .withChunkSize(64)
                                                 .withDetailDepth(2)
                                                 .withGenerationDistance(160);

        World world = World.of(generator)
                           .createCamera(CAMERA_ID, orbitingCamera(0, (double) INITIAL_W / INITIAL_H))
                           .createScreen(SCREEN_ID, INITIAL_W, INITIAL_H)
                           .bindScreenToCamera(SCREEN_ID, CAMERA_ID)
                           // Build the world around the initial camera up front, so the first frame isn't empty.
                           .update(EngineInputs.of(0.0));

        System.out.println("World generated in " + (System.currentTimeMillis() - start) + " ms.");
        SwingUtilities.invokeLater(() -> showWindow(world));
    }

    private static CameraF64 orbitingCamera( double angleRadians, double aspect ) {
        VecF64 position = VecF64.of(Math.cos(angleRadians) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(angleRadians) * ORBIT_RADIUS);
        return new CameraF64(position, VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), aspect, 0.5, 2000);
    }

    private static void showWindow( World initialWorld ) {
        // The renderer is a swappable backend behind the Renderer SPI: it owns its viewport
        // component and its own render loop; the demo just publishes the latest world to it.
        // Pick the backend with -Dengine.renderer=gl (OpenGL) or default (software Graphics2D).
        boolean useGl = "gl".equalsIgnoreCase(System.getProperty("engine.renderer"));
        Renderer renderer = useGl ? new GlRenderer() : new Graphics2DRenderer();
        renderer.setWorld(initialWorld);

        // A World is a deeply immutable value, so it can cross threads with no locking: the
        // background updater writes the latest world, the renderer reads the most recent one.
        // Input events are produced on the EDT (listeners) and consumed on the updater.
        AtomicReference<World> worldRef = new AtomicReference<>(initialWorld);
        AtomicBoolean underControl = new AtomicBoolean(false);
        ConcurrentLinkedQueue<ScreenInputEvent> events = new ConcurrentLinkedQueue<>();
        AtomicReference<Dimension> sizeRef = new AtomicReference<>(new Dimension(INITIAL_W, INITIAL_H));
        Point[] lastCursor = { null }; // EDT-only: cursor deltas are computed before enqueueing.

        // The renderer draws only the world (overlays are deferred); diagnostics go to the title bar.
        Component canvas = renderer.viewportFor(SCREEN_ID);
        canvas.setPreferredSize(new Dimension(INITIAL_W, INITIAL_H));
        canvas.setFocusable(true);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized( ComponentEvent e ) { sizeRef.set(canvas.getSize()); }
        });

        // Input handlers (EDT). For control-taking events, flip the flag BEFORE enqueueing so the
        // updater never sees an event without also seeing that we are now in control.
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed( KeyEvent e ) {
                Key key = mapKey(e.getKeyCode());
                if ( key != null ) { underControl.set(true); events.add(new ScreenInputEvent.KeyPressed(key)); }
            }
            @Override public void keyReleased( KeyEvent e ) {
                Key key = mapKey(e.getKeyCode());
                if ( key != null ) events.add(new ScreenInputEvent.KeyReleased(key));
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed( MouseEvent e ) {
                canvas.requestFocusInWindow();
                events.add(new ScreenInputEvent.CursorDown(PointerId.MOUSE, e.getX(), e.getY(), buttonOf(e)));
            }
            @Override public void mouseReleased( MouseEvent e ) {
                events.add(new ScreenInputEvent.CursorUp(PointerId.MOUSE, e.getX(), e.getY(), buttonOf(e)));
            }
            @Override public void mouseEntered( MouseEvent e ) { lastCursor[0] = e.getPoint(); }
            @Override public void mouseExited( MouseEvent e )  { lastCursor[0] = null; }
            @Override public void mouseMoved( MouseEvent e )   { onMove(e); }
            @Override public void mouseDragged( MouseEvent e ) { onMove(e); }
            private void onMove( MouseEvent e ) {
                Point last = lastCursor[0];
                lastCursor[0] = e.getPoint();
                if ( last == null )
                    return; // just (re)entered: establish a reference without a jump.
                underControl.set(true);
                events.add(new ScreenInputEvent.CursorMoved(PointerId.MOUSE, e.getX(), e.getY(),
                                                            e.getX() - last.x, e.getY() - last.y));
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);

        JFrame frame = new JFrame("Tribalism - World Engine Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(canvas); // works for both a lightweight panel and a heavyweight GL canvas
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        canvas.requestFocusInWindow();

        // Background updater: advance the world (input + terrain generation) off the paint thread,
        // so the next frame's world is computed while the EDT is busy rendering the current one.
        Thread updater = new Thread(() -> {
            long last = System.nanoTime();
            long orbitStart = last;
            while ( !Thread.currentThread().isInterrupted() ) {
                long now = System.nanoTime();
                double dt = (now - last) / 1_000_000_000.0;
                last = now;

                World world = worldRef.get();

                // Keep the modelled screen the same size as the actual panel.
                Dimension size = sizeRef.get();
                int w = Math.max(1, size.width), h = Math.max(1, size.height);
                Screen screen = world.screen(SCREEN_ID).orElseThrow();
                if ( screen.width() != w || screen.height() != h )
                    world = world.resizeScreen(SCREEN_ID, w, h);

                List<ScreenInputEvent> drained = new ArrayList<>();
                for ( ScreenInputEvent ev; (ev = events.poll()) != null; )
                    drained.add(ev);
                ScreenInputs inputs = ScreenInputs.of(drained.toArray(new ScreenInputEvent[0]));

                if ( underControl.get() ) {
                    world = world.update(EngineInputs.of(dt).withScreen(SCREEN_ID, inputs));
                } else {
                    // Orbit pins the camera to its path; held keys are empty here, so feeding the
                    // (only ever no-op) inputs alongside it just streams terrain in.
                    double angle = (now - orbitStart) / 4_000_000_000.0;
                    world = world.createCamera(CAMERA_ID, orbitingCamera(angle, (double) w / h))
                                 .update(EngineInputs.of(dt).withScreen(SCREEN_ID, inputs));
                }
                worldRef.set(world);
                renderer.setWorld(world); // publish to the backend, which paints on its own loop

                try { Thread.sleep(6); } // pace updates; cheap once nearby chunks are generated.
                catch ( InterruptedException ie ) { Thread.currentThread().interrupt(); }
            }
        }, "world-updater");
        updater.setDaemon(true);
        updater.start();

        // EDT: the backend drives painting itself; here we only refresh the diagnostics title.
        new Timer(250, e -> {
            FrameStats s = renderer.stats(SCREEN_ID);
            String mode = underControl.get()
                    ? "free-fly  -  W/A/S/D, Q/E or Space, Shift sprint, mouse look"
                    : "auto-orbit  -  press W/A/S/D or move the mouse to take control";
            frame.setTitle("Tribalism - World Engine Demo  |  " + mode
                         + "  |  faces: " + s.facesDrawn() + "  occlusion-culled: " + s.occlusionCulledSectors());
        }).start();
    }

    /** Maps the AWT key codes the demo cares about onto engine-neutral {@link Key}s. */
    private static @Nullable Key mapKey( int awtKeyCode ) {
        return switch ( awtKeyCode ) {
            case KeyEvent.VK_W -> Key.W;
            case KeyEvent.VK_A -> Key.A;
            case KeyEvent.VK_S -> Key.S;
            case KeyEvent.VK_D -> Key.D;
            case KeyEvent.VK_Q -> Key.Q;
            case KeyEvent.VK_E -> Key.E;
            case KeyEvent.VK_SPACE -> Key.SPACE;
            case KeyEvent.VK_SHIFT -> Key.SHIFT;
            default -> null;
        };
    }

    private static PointerButton buttonOf( MouseEvent e ) {
        return switch ( e.getButton() ) {
            case MouseEvent.BUTTON3 -> PointerButton.SECONDARY;
            case MouseEvent.BUTTON2 -> PointerButton.MIDDLE;
            default -> PointerButton.PRIMARY;
        };
    }
}