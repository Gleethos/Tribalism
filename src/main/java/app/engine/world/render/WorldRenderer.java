package app.engine.world.render;

import app.engine.primitives.*;
import app.engine.world.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 *  A first-draft renderer that draws a {@link World} into a {@link Graphics2D}
 *  surface as shaded voxel faces.
 *  <p>
 *  What the user sees is purely a function of world state. The renderer walks the
 *  world tree <b>near&nbsp;&rarr;&nbsp;far</b> under four culling/level-of-detail
 *  decisions:
 *  <ul>
 *      <li><b>Frustum culling</b> &mdash; a sector outside the view volume (and its
 *          whole sub-tree) is skipped.</li>
 *      <li><b>Occlusion culling</b> &mdash; a sector whose screen rectangle is already
 *          fully blocked by nearer solid geometry (tracked in a {@link CoverageGrid})
 *          is skipped, sub-tree and all. Fully-solid sectors mark their silhouette into
 *          the grid as the walk proceeds, so a wall in front prunes everything behind
 *          it with one test per hidden sub-tree.</li>
 *      <li><b>Level of detail</b> &mdash; a sector too small on screen
 *          ({@link #projectedEdgePixels}) is drawn as one coarse, inset-fitted box.</li>
 *      <li><b>Face/occlusion within a block</b> &mdash; a full-detail block of voxels
 *          (a branch of all leaves) is drawn from a cached, occlusion-culled
 *          {@link SectorMesh}.</li>
 *  </ul>
 *  Everything decomposes to {@link Quad}s, which are back-face culled, projected and
 *  finally depth-sorted (painter's algorithm) and filled as 2D polygons.
 */
public final class WorldRenderer
{
    /** The on-screen edge size, in pixels, above which a sector is refined into its children. */
    private final double _refineThresholdPx;
    private final VecF64 _lightDirection;
    private final Color _skyColor;
    private final SectorMeshCache _meshCache = new SectorMeshCache();

    /** The occlusion coverage grid's tile size in pixels (coarser = faster, less culling). */
    private static final int COVERAGE_TILE = 16;

    // Per-frame stats (for the demo HUD and tests).
    private int _facesDrawn;
    private int _occlusionCulled;

    public WorldRenderer() {
        this(28.0, VecF64.of(-0.4, -1.0, -0.3).normalize(), new Color(135, 180, 235));
    }

    public WorldRenderer( double refineThresholdPx, VecF64 lightDirection, Color skyColor ) {
        _refineThresholdPx = refineThresholdPx;
        _lightDirection = lightDirection.normalize();
        _skyColor = skyColor;
    }

    /** @return The number of faces actually drawn in the most recent {@link #render}. */
    public int facesDrawn() { return _facesDrawn; }

    /** @return The number of sectors (and their sub-trees) skipped by occlusion culling in the most recent {@link #render}. */
    public int occlusionCulledSectors() { return _occlusionCulled; }

    /**
     *  The approximate on-screen size, in pixels, that an object of the given
     *  {@code edgeLength} occupies at the given {@code distance} from the camera.
     *  This is the pure heart of the level-of-detail decision.
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

        _facesDrawn = 0;
        _occlusionCulled = 0;

        List<ScreenFace> faces = new ArrayList<>();
        Walk walk = new Walk(
                new ViewInfo(camera, camera.frustum(), camera.viewProjectionMatrix(),
                focalLengthPx(camera, height), width, height),
                new CoverageGrid(width, height, COVERAGE_TILE),
                (sector, wantsDetail, viewInfo)->{
                    if ( sector.isSolidOpaque() ) {
                        emitBox(sector, viewInfo, _lightDirection, faces);
                    } else if ( !wantsDetail || sector.isLeaf() ) {
                        if ( isMajorityOpaque(sector.ether()) )
                            emitBox(sector, viewInfo, _lightDirection, faces);
                    } else if ( hasOnlyLeafChildren(sector) ) {
                        for ( Quad quad : _meshCache.meshOf(sector).quads() )
                            emitQuad(quad, viewInfo, _lightDirection, faces);
                    }
                }
        );
        walk.collect(world.root());

        // Painter's algorithm: draw far faces first so near ones cover them.
        faces.sort(Comparator.comparingDouble((ScreenFace f) -> f.distance).reversed());
        for ( ScreenFace f : faces ) {
            g.setColor(f.color);
            g.fillPolygon(f.polygon);
        }
        _facesDrawn = faces.size();
    }

    record ViewInfo(
        CameraF64 camera,
        Frustum frustum,
        Mat4F64 vp,
        double focal,
        int w,
        int h
    ) {}

    interface SectorDrawCollector {
        void collect(WorldSector sector, boolean wantsDetail, ViewInfo viewInfo);
    }

    /** Holds the per-frame walk state so the recursion stays a set of small methods. */
    private final class Walk {
        final ViewInfo viewInfo;
        final CoverageGrid coverage;
        final SectorDrawCollector collector;

        Walk(ViewInfo viewInfo, CoverageGrid coverage, SectorDrawCollector collector) {
            this.viewInfo = viewInfo;
            this.coverage = coverage;this.collector = collector;
        }

        void collect( WorldSector sector ) {
            if ( !viewInfo.frustum().intersects(sector.bounds()) )
                return; // outside the view: prune this sector and its whole sub-tree.

            double[][] corners = project8(sector.bounds());
            if ( corners != null && coverage.isOccluded(minOf(corners, 0), minOf(corners, 1),
                                                        maxOf(corners, 0), maxOf(corners, 1)) ) {
                _occlusionCulled++;
                return; // fully hidden behind nearer solid geometry: prune the sub-tree.
            }

            if ( sector.isSolidOpaque() ) {
                // A perfect occluder: draw it as a single box (its mesh would just be the
                // shell anyway) and record its silhouette so it blocks what is behind.
                emitSector(sector, false, viewInfo);
                if ( corners != null )
                    coverage.markOccluder(corners);
                return;
            }

            double distance = viewInfo.camera().position().distance(sector.bounds().center());
            boolean wantsDetail = projectedEdgePixels(maxEdge(sector.bounds()), distance, viewInfo.focal()) > _refineThresholdPx;

            emitSector(sector, wantsDetail, viewInfo);

            if ( sector.isLeaf() )
                return;
            if ( hasOnlyLeafChildren(sector) )
                return;

            // Recurse, nearest child first, so nearer occluders are marked before farther
            // siblings are tested.
            WorldTreeNode node = sector.children();
            Integer[] order = new Integer[WorldTreeNode.SECTOR_COUNT];
            double[] dist = new double[WorldTreeNode.SECTOR_COUNT];
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
                order[i] = i;
                dist[i] = viewInfo.camera().position().distance(node.sector(i).bounds().center());
            }
            Arrays.sort(order, Comparator.comparingDouble(i -> dist[i]));
            for ( int i : order )
                collect(node.sector(i));
        }

        private void emitSector( WorldSector sector, boolean wantsDetail, ViewInfo viewInfo ) {
            collector.collect(sector, wantsDetail, viewInfo);
        }

        /** @return The 8 corners of {@code bounds} projected to screen, or {@code null} if any is behind the camera. */
        private double[][] project8( BoundsF64 bounds ) {
            VecF64[] world = worldCorners(bounds);
            double[][] screen = new double[8][];
            for ( int i = 0; i < 8; i++ ) {
                screen[i] = project(world[i], viewInfo.vp(), viewInfo.w(), viewInfo.h());
                if ( screen[i] == null )
                    return null;
            }
            return screen;
        }
    }

    private static void emitBox( WorldSector sector, ViewInfo viewInfo, VecF64 lightDirection, List<ScreenFace> out ) {
        BoundsF64 bounds = sector.insets().shrink(sector.bounds());
        WorldSectorEtherData ether = sector.ether();
        for ( Side side : Side.values() ) {
            TextureProfile profile = faceProfile(ether, side);
            if ( !profile.isInvisible() )
                emitQuad(Cubes.faceQuad(bounds, side, profile), viewInfo, lightDirection, out);
        }
    }

    private static void emitQuad( Quad quad, ViewInfo viewInfo, VecF64 lightDirection, List<ScreenFace> out ) {
        VecF64 center = quad.centroid();
        if ( quad.normal().dot(viewInfo.camera().position().sub(center)) <= 0 )
            return; // back-face

        double[] s0 = project(quad.c0(), viewInfo.vp(), viewInfo.w(), viewInfo.h());
        double[] s1 = project(quad.c1(), viewInfo.vp(), viewInfo.w(), viewInfo.h());
        double[] s2 = project(quad.c2(), viewInfo.vp(), viewInfo.w(), viewInfo.h());
        double[] s3 = project(quad.c3(), viewInfo.vp(), viewInfo.w(), viewInfo.h());
        if ( s0 == null || s1 == null || s2 == null || s3 == null )
            return; // a corner is at/behind the camera: skip this face for the first draft.

        Polygon polygon = new Polygon();
        for ( double[] s : new double[][]{ s0, s1, s2, s3 } )
            polygon.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        Color color = shade(TexturePalette.colorOf(quad.profile()), quad.normal(), lightDirection);
        out.add(new ScreenFace(polygon, color, viewInfo.camera().position().distance(center)));
    }

    private static double minOf( double[][] pts, int axis ) {
        double m = Double.POSITIVE_INFINITY;
        for ( double[] p : pts ) m = Math.min(m, p[axis]);
        return m;
    }

    private static double maxOf( double[][] pts, int axis ) {
        double m = Double.NEGATIVE_INFINITY;
        for ( double[] p : pts ) m = Math.max(m, p[axis]);
        return m;
    }

    private static boolean hasOnlyLeafChildren( WorldSector sector ) {
        WorldTreeNode node = sector.children();
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            if ( !node.sector(i).isLeaf() )
                return false;
        return true;
    }

    /**
     *  @return {@code true} if the sector is <i>majority opaque</i> &mdash; its combined
     *          per-side appearance has {@link Texture#OPACITY} of at least
     *          {@link TexturePalette#VISIBILITY_THRESHOLD}. Gating on a majority keeps
     *          mostly-empty coarse boxes from inflating past the true surface.
     */
    public static boolean isMajorityOpaque( WorldSectorEtherData ether ) {
        return TexturePalette.isVisible(ether.combined().intensityOf(Texture.OPACITY));
    }

    /**
     *  The appearance a given face should be painted with: that {@link Side}'s own
     *  profile, or &mdash; when an aggregated face came out (near-)invisible on an
     *  otherwise opaque cube &mdash; the sector's {@link WorldSectorEtherData#combined()
     *  combined} appearance, so a drawn box is never left with see-through holes.
     */
    public static TextureProfile faceProfile( WorldSectorEtherData ether, Side side ) {
        TextureProfile profile = ether.sideOf(side);
        if ( !TexturePalette.isVisible(profile.intensityOf(Texture.OPACITY)) )
            return ether.combined();
        return profile;
    }

    /** Flat directional shading with an ambient floor, clamped to valid colour values. */
    private static Color shade( Color base, VecF64 normal, VecF64 lightDirection ) {
        double diffuse = Math.max(0, normal.dot(lightDirection.negate()));
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

    private static VecF64[] worldCorners( BoundsF64 b ) {
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
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** A face ready to draw: its screen polygon, colour, and distance for painter's ordering. */
    private record ScreenFace(Polygon polygon, Color color, double distance) {}
}