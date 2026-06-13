package app.maps;

import app.engine.primitives.CameraF64;
import app.engine.primitives.VecF64;
import app.engine.world.EngineInputs;
import app.engine.world.Key;
import app.engine.world.PointerId;
import app.engine.world.Screen;
import app.engine.world.ScreenId;
import app.engine.world.ScreenInputEvent;
import app.engine.world.ScreenInputs;
import app.engine.world.World;
import app.engine.world.render.Graphics2DRenderer;
import app.engine.world.render.Renderer;
import org.jspecify.annotations.Nullable;

import swingtree.UI;

import javax.swing.JPanel;
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
import java.util.concurrent.atomic.AtomicReference;

import static swingtree.UI.*;

/**
 *  An embeddable view of a world-engine {@link World} — the first realization of the in-app map
 *  surface (VISION §6, "Build mode"). It lifts the engine demo's camera/screen/renderer/update
 *  wiring into a reusable SwingTree component: it adds a free-fly camera and a screen to the
 *  world, embeds the renderer's viewport, translates Swing input into engine
 *  {@link ScreenInputEvent}s, and advances the world on a background thread (the immutable world
 *  crosses threads with no locking).
 *  <p>
 *  Construct it over a world built from a {@link GameMap} via {@link MapWorlds#worldOf} to render
 *  a campaign's map. Call {@link #stop()} when removing it to end the update thread. This first
 *  cut uses the dependable software {@link Graphics2DRenderer}; the OpenGL backend can slot in
 *  behind the same {@link Renderer} SPI later.
 */
public final class MapView extends JPanel
{
    private static final ScreenId SCREEN_ID = ScreenId.of(1L);
    private static final long     CAMERA_ID = 1L;
    private static final int      INITIAL_W = 900, INITIAL_H = 600;

    private final Renderer renderer;
    private final Thread updater;
    private volatile boolean running = true;

    public MapView( World world ) {
        // Give the world a camera looking at the origin terrain and a screen bound to it, then
        // prime one update so the first painted frame already has terrain streamed in.
        World primed = world
            .createCamera(CAMERA_ID, viewingCamera((double) INITIAL_W / INITIAL_H))
            .createScreen(SCREEN_ID, INITIAL_W, INITIAL_H)
            .bindScreenToCamera(SCREEN_ID, CAMERA_ID)
            .update(EngineInputs.of(0.0));

        this.renderer = new Graphics2DRenderer();
        renderer.setWorld(primed);

        AtomicReference<World> worldRef = new AtomicReference<>(primed);
        AtomicReference<Dimension> sizeRef = new AtomicReference<>(new Dimension(INITIAL_W, INITIAL_H));
        ConcurrentLinkedQueue<ScreenInputEvent> events = new ConcurrentLinkedQueue<>();
        Point[] lastCursor = { null };

        Component canvas = renderer.viewportFor(SCREEN_ID);
        canvas.setPreferredSize(new Dimension(INITIAL_W, INITIAL_H));
        canvas.setFocusable(true);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized( ComponentEvent e ) { sizeRef.set(canvas.getSize()); }
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed( KeyEvent e ) {
                Key key = mapKey(e.getKeyCode());
                if ( key != null ) events.add(new ScreenInputEvent.KeyPressed(key));
            }
            @Override public void keyReleased( KeyEvent e ) {
                Key key = mapKey(e.getKeyCode());
                if ( key != null ) events.add(new ScreenInputEvent.KeyReleased(key));
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed( MouseEvent e ) { canvas.requestFocusInWindow(); }
            @Override public void mouseEntered( MouseEvent e ) { lastCursor[0] = e.getPoint(); }
            @Override public void mouseExited( MouseEvent e )  { lastCursor[0] = null; }
            @Override public void mouseMoved( MouseEvent e )   { onMove(e); }
            @Override public void mouseDragged( MouseEvent e ) { onMove(e); }
            private void onMove( MouseEvent e ) {
                Point last = lastCursor[0];
                lastCursor[0] = e.getPoint();
                if ( last == null ) return;
                events.add(new ScreenInputEvent.CursorMoved(PointerId.MOUSE, e.getX(), e.getY(),
                                                            e.getX() - last.x, e.getY() - last.y));
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);

        of(this).withLayout(FILL).add(GROW, UI.of((JPanel) canvas));

        this.updater = new Thread(() -> {
            long last = System.nanoTime();
            while ( running && !Thread.currentThread().isInterrupted() ) {
                long now = System.nanoTime();
                double dt = (now - last) / 1_000_000_000.0;
                last = now;

                World current = worldRef.get();
                Dimension size = sizeRef.get();
                int w = Math.max(1, size.width), h = Math.max(1, size.height);
                Screen screen = current.screen(SCREEN_ID).orElseThrow();
                if ( screen.width() != w || screen.height() != h )
                    current = current.resizeScreen(SCREEN_ID, w, h);

                List<ScreenInputEvent> drained = new ArrayList<>();
                for ( ScreenInputEvent ev; (ev = events.poll()) != null; )
                    drained.add(ev);
                ScreenInputs inputs = ScreenInputs.of(drained.toArray(new ScreenInputEvent[0]));

                current = current.update(EngineInputs.of(dt).withScreen(SCREEN_ID, inputs));
                worldRef.set(current);
                renderer.setWorld(current);

                try { Thread.sleep(6); }
                catch ( InterruptedException ie ) { Thread.currentThread().interrupt(); }
            }
        }, "map-view-updater");
        this.updater.setDaemon(true);
        this.updater.start();
    }

    /** Stops the background update thread and releases the renderer. Call when removing the view. */
    public void stop() {
        running = false;
        updater.interrupt();
        try { renderer.close(); } catch ( Exception ignored ) {}
    }

    private static CameraF64 viewingCamera( double aspect ) {
        // Positioned back and above the origin, looking down at the terrain surface.
        VecF64 position = VecF64.of(96, 40, 96);
        return new CameraF64(position, VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), aspect, 0.5, 4096);
    }

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

    /** Standalone demo: render a GameMap-derived world embedded in a SwingTree window. */
    public static void main( String[] args ) {
        UI.runLater(() -> {
            try {
                String dbPath = "build/map_demo.db";
                java.io.File f = new java.io.File(dbPath);
                f.getParentFile().mkdirs();
                if ( f.exists() ) f.delete();
                f.createNewFile();
                var db = dal.api.DataBase.at(dbPath);
                db.dropAllTables();
                db.createTablesFor(app.models.GameMap.class);
                var map = db.create(app.models.GameMap.class);
                map.name().set("Demo Map");
                map.seed().set(1337L);
                map.chunkSize().set(64.0d);
                map.generationDistance().set(160.0d);

                World world = MapWorlds.worldOf(map);
                UI.show(frame -> new MapView(world));
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        });
    }
}
