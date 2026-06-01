package app.engine.world.render;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;
import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import app.engine.world.Material;
import app.engine.world.World;
import app.engine.world.WorldSection;
import app.engine.world.WorldTreeNode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *  A first-draft renderer that draws a {@link World} into a {@link Graphics2D}
 *  surface as shaded voxel cubes.
 *  <p>
 *  What the user sees is purely a function of world state. The renderer walks the
 *  world tree and, for each section, decides via {@link #projectedEdgePixels} how
 *  big it would appear on screen: distant sections are drawn as a single coarse
 *  "super-voxel", while nearby sections are recursed into for finer detail. This
 *  is the level-of-detail story made visible &mdash; the further away something is,
 *  the higher up the tree we stop.
 */
public final class WorldRenderer
{
    /** The on-screen edge size, in pixels, above which a section is refined into its children. */
    private final double _refineThresholdPx;
    private final VecF64 _lightDirection;
    private final Color _skyColor;

    public WorldRenderer() {
        this(28.0, VecF64.of(-0.4, -1.0, -0.3).normalize(), new Color(135, 180, 235));
    }

    public WorldRenderer( double refineThresholdPx, VecF64 lightDirection, Color skyColor ) {
        _refineThresholdPx = refineThresholdPx;
        _lightDirection = lightDirection.normalize();
        _skyColor = skyColor;
    }

    /**
     *  The approximate on-screen size, in pixels, that an object of the given
     *  {@code edgeLength} occupies at the given {@code distance} from the camera.
     *  This is the pure heart of the level-of-detail decision.
     *
     *  @param edgeLength     The world-space edge length of the object.
     *  @param distance       The distance from the camera to the object.
     *  @param focalLengthPx  The camera focal length in pixels (see {@link #focalLengthPx}).
     */
    public static double projectedEdgePixels( double edgeLength, double distance, double focalLengthPx ) {
        if ( distance <= 0 )
            return Double.POSITIVE_INFINITY;
        return edgeLength * focalLengthPx / distance;
    }

    /** @return The focal length in pixels for a camera rendered into a viewport of the given height. */
    public static double focalLengthPx( CameraF64 camera, int viewportHeight ) {
        return (viewportHeight / 2.0) / Math.tan(camera.fovYRadians() / 2.0);
    }

    public void render( Graphics2D g, World world, CameraF64 camera, int width, int height ) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(_skyColor);
        g.fillRect(0, 0, width, height);

        Mat4F64 viewProjection = camera.viewProjectionMatrix();
        double focal = focalLengthPx(camera, height);

        List<Renderable> renderables = new ArrayList<>();
        collect(world.root(), camera, focal, renderables);

        // Painter's algorithm: draw far voxels first so near ones cover them.
        renderables.sort(Comparator.comparingDouble((Renderable r) -> r.distance).reversed());
        for ( Renderable r : renderables )
            drawVoxel(g, r.bounds, MaterialPalette.colorOf(r.material), viewProjection, camera, width, height);
    }

    /** Walks the tree, choosing the level of detail to draw at for each section. */
    private void collect( WorldSection section, CameraF64 camera, double focal, List<Renderable> out ) {
        double distance = camera.position().distance(section.bounds().center());
        double edge = maxEdge(section.bounds());

        boolean canRefine = section.children() != null;
        boolean wantsRefine = projectedEdgePixels(edge, distance, focal) > _refineThresholdPx;

        if ( canRefine && wantsRefine ) {
            WorldTreeNode node = section.children();
            for ( int i = 0; i < WorldTreeNode.SECTION_COUNT; i++ )
                collect(node.section(i), camera, focal, out);
        } else {
            Material material = section.ether().dominantMaterial();
            if ( !MaterialPalette.isTransparent(material) )
                out.add(new Renderable(section.bounds(), material, distance));
        }
    }

    private void drawVoxel( Graphics2D g, BoundsF64 bounds, Color base, Mat4F64 vp, CameraF64 camera, int w, int h ) {
        VecF64[] corners = corners(bounds);
        double[][] screen = new double[8][];
        for ( int i = 0; i < 8; i++ ) {
            screen[i] = project(corners[i], vp, w, h);
            if ( screen[i] == null )
                return; // a corner is at/behind the camera: skip this voxel for the first draft.
        }

        for ( int[] face : FACES ) {
            VecF64 normal = FACE_NORMALS[indexOfFace(face)];
            VecF64 faceCenter = corners[face[0]].add(corners[face[2]]).div(2);
            // Back-face culling: only draw faces whose outward normal points towards the camera.
            if ( normal.dot(camera.position().sub(faceCenter)) <= 0 )
                continue;

            Polygon polygon = new Polygon();
            for ( int corner : face )
                polygon.addPoint((int) Math.round(screen[corner][0]), (int) Math.round(screen[corner][1]));

            g.setColor(shade(base, normal));
            g.fillPolygon(polygon);
        }
    }

    /** Flat directional shading with an ambient floor, clamped to valid colour values. */
    private Color shade( Color base, VecF64 normal ) {
        double diffuse = Math.max(0, normal.dot(_lightDirection.negate()));
        double brightness = 0.45 + 0.55 * diffuse;
        int r = clampColor((int) Math.round(base.getRed()   * brightness));
        int gr = clampColor((int) Math.round(base.getGreen() * brightness));
        int b = clampColor((int) Math.round(base.getBlue()  * brightness));
        return new Color(r, gr, b);
    }

    /**
     *  Projects a world point to screen pixels.
     *  @return {@code {screenX, screenY}}, or {@code null} if the point is at or
     *          behind the camera (clip {@code w <= 0}).
     */
    private static double[] project( VecF64 p, Mat4F64 vp, int w, int h ) {
        double x = vp.get(0, 0) * p.x() + vp.get(0, 1) * p.y() + vp.get(0, 2) * p.z() + vp.get(0, 3);
        double y = vp.get(1, 0) * p.x() + vp.get(1, 1) * p.y() + vp.get(1, 2) * p.z() + vp.get(1, 3);
        double clipW = vp.get(3, 0) * p.x() + vp.get(3, 1) * p.y() + vp.get(3, 2) * p.z() + vp.get(3, 3);
        if ( clipW <= 1e-9 )
            return null;
        double ndcX = x / clipW;
        double ndcY = y / clipW;
        return new double[]{ (ndcX * 0.5 + 0.5) * w, (1 - (ndcY * 0.5 + 0.5)) * h };
    }

    private static double maxEdge( BoundsF64 bounds ) {
        VecF64 size = bounds.size();
        return Math.max(size.x(), Math.max(size.y(), size.z()));
    }

    private static VecF64[] corners( BoundsF64 b ) {
        VecF64 lo = b.min(), hi = b.max();
        VecF64[] c = new VecF64[8];
        for ( int i = 0; i < 8; i++ )
            c[i] = VecF64.of(
                    (i & 1) == 0 ? lo.x() : hi.x(),
                    (i & 2) == 0 ? lo.y() : hi.y(),
                    (i & 4) == 0 ? lo.z() : hi.z()
            );
        return c;
    }

    private static int clampColor( int v ) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // The six cube faces, each as four corner indices in boundary order, with
    // matching outward normals (see corners(): corner i has bit 0 = x, 1 = y, 2 = z).
    private static final int[][] FACES = {
            { 0, 1, 5, 4 }, // -Y bottom
            { 2, 3, 7, 6 }, // +Y top
            { 0, 2, 6, 4 }, // -X left
            { 1, 3, 7, 5 }, // +X right
            { 0, 1, 3, 2 }, // -Z front
            { 4, 5, 7, 6 }  // +Z back
    };

    private static final VecF64[] FACE_NORMALS = {
            VecF64.of(0, -1, 0),
            VecF64.of(0, 1, 0),
            VecF64.of(-1, 0, 0),
            VecF64.of(1, 0, 0),
            VecF64.of(0, 0, -1),
            VecF64.of(0, 0, 1)
    };

    private static int indexOfFace( int[] face ) {
        for ( int i = 0; i < FACES.length; i++ )
            if ( FACES[i] == face )
                return i;
        throw new IllegalStateException("Unknown face.");
    }

    /** A single cube to be drawn, tagged with its distance for painter's-order sorting. */
    private record Renderable(BoundsF64 bounds, Material material, double distance) {}
}