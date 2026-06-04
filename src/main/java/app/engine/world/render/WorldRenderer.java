package app.engine.world.render;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;
import app.engine.primitives.Frustum;
import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import app.engine.world.Side;
import app.engine.world.Texture;
import app.engine.world.TextureProfile;
import app.engine.world.World;
import app.engine.world.WorldSector;
import app.engine.world.WorldSectorEtherData;
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
 *  surface as shaded voxel faces.
 *  <p>
 *  What the user sees is purely a function of world state. The renderer walks the
 *  world tree under three culling/level-of-detail decisions:
 *  <ul>
 *      <li><b>Frustum culling</b> &mdash; a sector outside the view volume (and its
 *          whole sub-tree) is skipped.</li>
 *      <li><b>Level of detail</b> &mdash; a sector too small on screen
 *          ({@link #projectedEdgePixels}) is drawn as a single coarse, inset-fitted
 *          box rather than recursed into.</li>
 *      <li><b>Occlusion culling</b> &mdash; once the walk reaches a full-detail block
 *          of voxels (a branch whose children are all leaves), it is drawn from a
 *          cached {@link SectorMesh}, which omits faces buried between opaque voxels.
 *          Because a {@link WorldSector} is an immutable value it is a perfect cache
 *          key, so an unchanged block is meshed once and reused every frame.</li>
 *  </ul>
 *  Everything decomposes to {@link Quad}s, which are back-face culled, depth-sorted
 *  (painter's algorithm) and filled as 2D polygons.
 */
public final class WorldRenderer
{
    /** The on-screen edge size, in pixels, above which a sector is refined into its children. */
    private final double _refineThresholdPx;
    private final VecF64 _lightDirection;
    private final Color _skyColor;
    private final SectorMeshCache _meshCache = new SectorMeshCache();

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
        Frustum frustum = camera.frustum();
        double focal = focalLengthPx(camera, height);

        List<Quad> quads = new ArrayList<>();
        collect(world.root(), camera, frustum, focal, quads);

        List<Drawable> drawables = new ArrayList<>();
        for ( Quad quad : quads ) {
            VecF64 center = quad.centroid();
            // Back-face culling: only keep faces whose outward normal points towards the camera.
            if ( quad.normal().dot(camera.position().sub(center)) <= 0 )
                continue;
            Polygon polygon = projectQuad(quad, viewProjection, width, height);
            if ( polygon == null )
                continue; // a corner is at/behind the camera: skip this face for the first draft.
            Color color = shade(TexturePalette.colorOf(quad.profile()), quad.normal());
            drawables.add(new Drawable(polygon, color, camera.position().distance(center)));
        }

        // Painter's algorithm: draw far faces first so near ones cover them.
        drawables.sort(Comparator.comparingDouble((Drawable d) -> d.distance).reversed());
        for ( Drawable d : drawables ) {
            g.setColor(d.color);
            g.fillPolygon(d.polygon);
        }
    }

    /**
     *  Walks the tree, emitting the {@link Quad}s to draw under frustum culling,
     *  level-of-detail and occlusion culling.
     */
    private void collect( WorldSector sector, CameraF64 camera, Frustum frustum, double focal, List<Quad> out ) {
        if ( !frustum.intersects(sector.bounds()) )
            return; // outside the view: prune this sector and its whole sub-tree.

        double distance = camera.position().distance(sector.bounds().center());
        double edge = maxEdge(sector.bounds());
        boolean wantsDetail = projectedEdgePixels(edge, distance, focal) > _refineThresholdPx;

        if ( !wantsDetail ) {
            // Far enough to draw as one coarse box, shrunk by its insets to fit content.
            emitBox(sector.insets().shrink(sector.bounds()), sector.ether(), out);
            return;
        }
        if ( sector.isLeaf() ) {
            // A single large voxel: there is no finer structure, so draw its box.
            emitBox(sector.bounds(), sector.ether(), out);
            return;
        }
        if ( hasOnlyLeafChildren(sector) ) {
            // A full-detail block of voxels: draw its cached, occlusion-culled mesh.
            out.addAll(_meshCache.meshOf(sector).quads().toList());
        } else {
            WorldTreeNode node = sector.children();
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
                collect(node.sector(i), camera, frustum, focal, out);
        }
    }

    /** Emits the (up to six) visible faces of a coarse box, hole-filling invisible sides. */
    private void emitBox( BoundsF64 bounds, WorldSectorEtherData ether, List<Quad> out ) {
        if ( !isMajorityOpaque(ether) )
            return;
        for ( Side side : Side.values() ) {
            TextureProfile profile = faceProfile(ether, side);
            if ( profile.isInvisible() )
                continue; // only when the whole sector is invisible (e.g. all air).
            out.add(Cubes.faceQuad(bounds, side, profile));
        }
    }

    private static boolean hasOnlyLeafChildren( WorldSector sector ) {
        WorldTreeNode node = sector.children();
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            if ( !node.sector(i).isLeaf() )
                return false;
        return true;
    }

    /**
     *  @return {@code true} if the sector is <i>majority opaque</i> &mdash; its
     *          combined per-side appearance has an {@link Texture#OPACITY} of at
     *          least {@link TexturePalette#VISIBILITY_THRESHOLD}.
     *  <p>
     *  This is the "draw it as a voxel?" decision. Gating on a solid majority (rather
     *  than merely "any opaque face") keeps coarse LoD cubes from bulging out past the
     *  true surface: a super-voxel that is mostly empty air, with only a sliver of
     *  opaque matter on one side, is left undrawn instead of being inflated into a
     *  full block.
     */
    public static boolean isMajorityOpaque( WorldSectorEtherData ether ) {
        return TexturePalette.isVisible(ether.combined().intensityOf(Texture.OPACITY));
    }

    /**
     *  The appearance a given face should be painted with.
     *  <p>
     *  Normally this is that very {@link Side}'s own profile. But an LoD super-voxel's
     *  ether is aggregated <i>per side</i>, so an individual face near the surface can
     *  come out (near) invisible even when the cube is opaque overall. Skipping such a
     *  face would leave a see-through hole in an otherwise solid cube (a uniform leaf
     *  voxel never has this problem). To keep a drawn cube closed, an invisible face
     *  falls back to the sector's {@link WorldSectorEtherData#combined() combined}
     *  appearance. The result is invisible only when the <i>whole</i> sector is.
     */
    public static TextureProfile faceProfile( WorldSectorEtherData ether, Side side ) {
        TextureProfile profile = ether.sideOf(side);
        if ( !TexturePalette.isVisible(profile.intensityOf(Texture.OPACITY)) )
            return ether.combined();
        return profile;
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
     *  Projects a quad's four world corners to a screen polygon.
     *  @return The polygon, or {@code null} if any corner is at/behind the camera.
     */
    private static Polygon projectQuad( Quad quad, Mat4F64 vp, int w, int h ) {
        VecF64[] corners = { quad.c0(), quad.c1(), quad.c2(), quad.c3() };
        Polygon polygon = new Polygon();
        for ( VecF64 corner : corners ) {
            double[] screen = project(corner, vp, w, h);
            if ( screen == null )
                return null;
            polygon.addPoint((int) Math.round(screen[0]), (int) Math.round(screen[1]));
        }
        return polygon;
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

    private static int clampColor( int v ) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** A face ready to draw: its screen polygon, colour, and distance for painter's ordering. */
    private record Drawable(Polygon polygon, Color color, double distance) {}
}