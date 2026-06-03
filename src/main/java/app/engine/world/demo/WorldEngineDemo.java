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

/**
 *  A self-contained demo of the world engine: it procedurally generates a small
 *  landscape, then orbits a camera around it, rendering each frame with the
 *  first-draft {@link WorldRenderer}.
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
        long[] angleMillisStart = { System.currentTimeMillis() };

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent( Graphics g ) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth();
                int h = getHeight();
                double aspect = h == 0 ? 1 : (double) w / h;
                double angle = (System.currentTimeMillis() - angleMillisStart[0]) / 4000.0;

                CameraF64 camera = orbitingCamera(angle, aspect);
                renderer.render(g2, world, camera, w, h);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                g2.drawString("World Engine demo  -  entities: " + world.allEntities().size()
                            + "  -  orbiting camera", 12, 20);
            }
        };
        canvas.setPreferredSize(new Dimension(960, 600));

        JFrame frame = new JFrame("Tribalism - World Engine Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // ~30 FPS animation loop; rendering stays a pure function of world + camera state.
        new Timer(33, e -> canvas.repaint()).start();
    }
}