package app.engine.world.demo;

import app.engine.primitives.BoundsF64;
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
import app.engine.world.WorldSector;
import app.engine.world.gen.WorldGenerator;
import app.engine.world.render.WorldRenderer;
import org.jspecify.annotations.Nullable;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

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
    private static final int REGION_EDGE = 128;
    private static final int GENERATION_DEPTH = 2;

    private static final long     CAMERA_ID = 1L;
    private static final ScreenId SCREEN_ID = ScreenId.of(1L);
    private static final int      INITIAL_W = 960, INITIAL_H = 600;

    public static void main( String[] args ) {
        System.out.println("Generating world...");
        long start = System.currentTimeMillis();

        BoundsF64 region = BoundsF64.cube(VecF64.zero(), REGION_EDGE);
        WorldSector root = WorldGenerator.withSeed(1337L).generate(region, GENERATION_DEPTH);

        World world = World.of(root)
                           .createCamera(CAMERA_ID, orbitingCamera(0, (double) INITIAL_W / INITIAL_H))
                           .createScreen(SCREEN_ID, INITIAL_W, INITIAL_H)
                           .bindScreenToCamera(SCREEN_ID, CAMERA_ID);

        System.out.println("World generated in " + (System.currentTimeMillis() - start) + " ms.");
        SwingUtilities.invokeLater(() -> showWindow(world));
    }

    private static CameraF64 orbitingCamera( double angleRadians, double aspect ) {
        double radius = REGION_EDGE * 0.75;
        double height = REGION_EDGE * 0.30;
        VecF64 position = VecF64.of(Math.cos(angleRadians) * radius, height, Math.sin(angleRadians) * radius);
        return new CameraF64(position, VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), aspect, 0.5, 2000);
    }

    private static void showWindow( World initialWorld ) {
        WorldRenderer renderer = new WorldRenderer();

        // Everything below runs on the Swing event-dispatch thread (listeners, the Timer
        // and painting all fire there), so this plain mutable state needs no synchronization.
        World[] worldRef = { initialWorld };
        boolean[] underControl = { false };
        List<ScreenInputEvent> pending = new ArrayList<>();
        Point[] lastCursor = { null };

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent( Graphics g ) {
                super.paintComponent(g);
                renderer.render((Graphics2D) g, worldRef[0], SCREEN_ID);

                String mode = underControl[0]
                        ? "free-fly  -  W/A/S/D move, Q/E or Space up/down, Shift sprint, mouse look"
                        : "auto-orbit  -  press W/A/S/D or move the mouse to take control";
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                g.drawString("World Engine demo  -  " + mode, 12, 20);
                g.drawString("faces drawn: " + renderer.facesDrawn()
                           + "   occlusion-culled sectors: " + renderer.occlusionCulledSectors(), 12, 38);
            }
        };
        canvas.setPreferredSize(new Dimension(INITIAL_W, INITIAL_H));
        canvas.setFocusable(true);

        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed( KeyEvent e ) {
                Key key = mapKey(e.getKeyCode());
                if ( key != null ) { pending.add(new ScreenInputEvent.KeyPressed(key)); underControl[0] = true; }
            }
            @Override public void keyReleased( KeyEvent e ) {
                Key key = mapKey(e.getKeyCode());
                if ( key != null ) pending.add(new ScreenInputEvent.KeyReleased(key));
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed( MouseEvent e ) {
                canvas.requestFocusInWindow();
                pending.add(new ScreenInputEvent.CursorDown(PointerId.MOUSE, e.getX(), e.getY(), buttonOf(e)));
            }
            @Override public void mouseReleased( MouseEvent e ) {
                pending.add(new ScreenInputEvent.CursorUp(PointerId.MOUSE, e.getX(), e.getY(), buttonOf(e)));
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
                pending.add(new ScreenInputEvent.CursorMoved(PointerId.MOUSE, e.getX(), e.getY(),
                                                             e.getX() - last.x, e.getY() - last.y));
                underControl[0] = true;
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);

        JFrame frame = new JFrame("Tribalism - World Engine Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        canvas.requestFocusInWindow();

        // ~60 FPS loop: feed inputs to the world, then repaint what the screen now shows.
        long[] lastTick = { System.nanoTime() };
        long orbitStart = System.nanoTime();
        new Timer(16, e -> {
            long now = System.nanoTime();
            double dt = (now - lastTick[0]) / 1_000_000_000.0;
            lastTick[0] = now;

            World world = worldRef[0];

            // Keep the modelled screen the same size as the actual panel.
            int w = Math.max(1, canvas.getWidth());
            int h = Math.max(1, canvas.getHeight());
            Screen screen = world.screen(SCREEN_ID).orElseThrow();
            if ( screen.width() != w || screen.height() != h )
                world = world.resizeScreen(SCREEN_ID, w, h);

            if ( underControl[0] ) {
                ScreenInputs inputs = ScreenInputs.of(pending.toArray(new ScreenInputEvent[0]));
                pending.clear();
                world = world.update(EngineInputs.of(dt).withScreen(SCREEN_ID, inputs));
            } else {
                pending.clear(); // ignore stray events while orbiting
                double angle = (now - orbitStart) / 4_000_000_000.0;
                world = world.createCamera(CAMERA_ID, orbitingCamera(angle, (double) w / h));
            }

            worldRef[0] = world;
            canvas.repaint();
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