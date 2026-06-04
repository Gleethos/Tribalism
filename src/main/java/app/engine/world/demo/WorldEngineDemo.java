package app.engine.world.demo;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;
import app.engine.primitives.VecF64;
import app.engine.world.Entity;
import app.engine.world.World;
import app.engine.world.WorldSector;
import app.engine.world.gen.WorldGenerator;
import app.engine.world.render.WorldRenderer;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  A self-contained demo of the world engine: it procedurally generates a small
 *  landscape and renders it each frame with the first-draft {@link WorldRenderer}.
 *  <p>
 *  The camera slowly orbits the world on its own, but the moment you press a
 *  movement key (W/A/S/D, Q/E or Space, Shift to sprint) or move the mouse, it
 *  switches to a free-fly camera you steer yourself &mdash; handy for inspecting
 *  the rendering up close. Rendering stays a pure function of world + camera state.
 *  <p>
 *  This is intentionally isolated from the main Tribalism application. Run
 *  {@link #main(String[])} to see the engine in action.
 */
public final class WorldEngineDemo
{
    private static final int REGION_EDGE = 128;
    private static final int GENERATION_DEPTH = 2;

    public static void main( String[] args ) {
        System.out.println("Generating world...");
        long start = System.currentTimeMillis();

        BoundsF64 region = BoundsF64.cube(VecF64.zero(), REGION_EDGE);
        WorldGenerator generator = WorldGenerator.withSeed(1337L);
        WorldSector root = generator.generate(region, GENERATION_DEPTH);

        CameraF64 initialCamera = orbitingCamera(0, 16.0 / 9.0);
        World world = World.of(root)
                           .withEntity(Entity.CameraEntity.of(1L, initialCamera));

        System.out.println("World generated in " + (System.currentTimeMillis() - start) + " ms.");

        SwingUtilities.invokeLater(() -> showWindow(world));
    }

    private static CameraF64 orbitingCamera( double angleRadians, double aspect ) {
        double radius = REGION_EDGE * 0.75;
        double height = REGION_EDGE * 0.30;
        VecF64 position = VecF64.of(
                Math.cos(angleRadians) * radius,
                height,
                Math.sin(angleRadians) * radius
        );
        return new CameraF64(
                position,
                VecF64.of(0, 0, 0), // look at the world center
                VecF64.of(0, 1, 0),
                Math.toRadians(60),
                aspect,
                0.5,
                2000
        );
    }

    private static void showWindow( World world ) {
        WorldRenderer renderer = new WorldRenderer();
        long orbitStartMillis = System.currentTimeMillis();
        FlyController fly = new FlyController();

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent( Graphics g ) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth();
                int h = getHeight();
                double aspect = h == 0 ? 1 : (double) w / h;

                CameraF64 camera;
                String mode;
                if ( fly.active ) {
                    camera = fly.camera(aspect);
                    mode = "free-fly  -  W/A/S/D move, Q/E or Space up/down, Shift sprint, mouse look";
                } else {
                    double angle = (System.currentTimeMillis() - orbitStartMillis) / 4000.0;
                    camera = orbitingCamera(angle, aspect);
                    mode = "auto-orbit  -  press W/A/S/D or move the mouse to take control";
                }
                renderer.render(g2, world, camera, w, h);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                g2.drawString("World Engine demo  -  " + mode, 12, 20);
                g2.drawString("faces drawn: " + renderer.facesDrawn()
                            + "   occlusion-culled sectors: " + renderer.occlusionCulledSectors(), 12, 38);
            }
        };
        canvas.setPreferredSize(new Dimension(960, 600));
        canvas.setFocusable(true);

        // Switching to fly mode seeds the fly camera from wherever the orbit currently is,
        // so taking control never snaps the view.
        Runnable takeControl = () -> {
            if ( !fly.active ) {
                double angle = (System.currentTimeMillis() - orbitStartMillis) / 4000.0;
                fly.seedFrom(orbitingCamera(angle, 1.0));
                fly.active = true;
            }
        };

        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed( KeyEvent e ) {
                if ( FlyController.isControlKey(e.getKeyCode()) ) {
                    takeControl.run();
                    fly.keys.add(e.getKeyCode());
                }
            }
            @Override public void keyReleased( KeyEvent e ) {
                fly.keys.remove(e.getKeyCode());
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed( MouseEvent e ) { canvas.requestFocusInWindow(); }
            @Override public void mouseEntered( MouseEvent e ) { fly.lastMouse = e.getPoint(); }
            @Override public void mouseExited( MouseEvent e )  { fly.lastMouse = null; }
            @Override public void mouseMoved( MouseEvent e )   { onMove(e); }
            @Override public void mouseDragged( MouseEvent e ) { onMove(e); }
            private void onMove( MouseEvent e ) {
                Point last = fly.lastMouse;
                fly.lastMouse = e.getPoint();
                if ( last == null )
                    return; // just (re)entered: establish a reference without a jump.
                takeControl.run();
                fly.look(e.getX() - last.x, e.getY() - last.y);
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

        // ~60 FPS loop: advance the fly camera by real elapsed time, then repaint.
        long[] lastTick = { System.nanoTime() };
        new Timer(16, e -> {
            long now = System.nanoTime();
            double dt = (now - lastTick[0]) / 1_000_000_000.0;
            lastTick[0] = now;
            if ( fly.active )
                fly.update(dt);
            canvas.repaint();
        }).start();
    }

    /** Mutable free-fly camera state, steered by held keys and mouse look. */
    private static final class FlyController
    {
        private volatile boolean active = false;
        private final Set<Integer> keys = ConcurrentHashMap.newKeySet();
        private volatile Point lastMouse = null;

        private double x, y, z;     // position
        private double yaw, pitch;  // orientation, in radians

        static boolean isControlKey( int code ) {
            return code == KeyEvent.VK_W || code == KeyEvent.VK_A || code == KeyEvent.VK_S || code == KeyEvent.VK_D
                || code == KeyEvent.VK_Q || code == KeyEvent.VK_E || code == KeyEvent.VK_SPACE
                || code == KeyEvent.VK_SHIFT;
        }

        /** Seed position and orientation from an existing camera (no view jump on takeover). */
        void seedFrom( CameraF64 camera ) {
            VecF64 p = camera.position();
            x = p.x(); y = p.y(); z = p.z();
            VecF64 f = camera.forward();
            pitch = Math.asin(Math.max(-1.0, Math.min(1.0, f.y())));
            yaw = Math.atan2(f.x(), f.z());
        }

        private VecF64 forward() {
            double cp = Math.cos(pitch);
            return VecF64.of(cp * Math.sin(yaw), Math.sin(pitch), cp * Math.cos(yaw));
        }

        /** Rotate the view by a mouse delta (pixels): right looks right, up looks up. */
        void look( int dx, int dy ) {
            double sensitivity = 0.005;
            yaw -= dx * sensitivity;   // camera's right is -X at yaw 0, so look-right decreases yaw
            pitch -= dy * sensitivity;
            double limit = Math.toRadians(89);
            pitch = Math.max(-limit, Math.min(limit, pitch));
        }

        /** Advance the position by held movement keys over {@code dt} seconds. */
        void update( double dt ) {
            if ( keys.isEmpty() )
                return;
            double speed = REGION_EDGE * 0.6 * dt;
            if ( keys.contains(KeyEvent.VK_SHIFT) )
                speed *= 3.0;

            VecF64 forward = forward();
            VecF64 worldUp = VecF64.of(0, 1, 0);
            VecF64 right = forward.cross(worldUp).normalize();

            VecF64 move = VecF64.zero();
            if ( keys.contains(KeyEvent.VK_W) ) move = move.add(forward);
            if ( keys.contains(KeyEvent.VK_S) ) move = move.sub(forward);
            if ( keys.contains(KeyEvent.VK_D) ) move = move.add(right);
            if ( keys.contains(KeyEvent.VK_A) ) move = move.sub(right);
            if ( keys.contains(KeyEvent.VK_E) || keys.contains(KeyEvent.VK_SPACE) ) move = move.add(worldUp);
            if ( keys.contains(KeyEvent.VK_Q) ) move = move.sub(worldUp);

            if ( move.lengthSquared() > 0 ) {
                move = move.normalize().mul(speed);
                x += move.x(); y += move.y(); z += move.z();
            }
        }

        CameraF64 camera( double aspect ) {
            VecF64 position = VecF64.of(x, y, z);
            return new CameraF64(
                    position,
                    position.add(forward()),
                    VecF64.of(0, 1, 0),
                    Math.toRadians(60),
                    aspect,
                    0.5,
                    2000
            );
        }
    }
}