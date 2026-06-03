package app.engine.world.render;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;
import app.engine.primitives.Frustum;
import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import app.engine.world.Material;
import app.engine.world.MaterialDistribution;
import app.engine.world.Side;
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
 *  surface as shaded voxel cubes.
 *  <p>
 *  What the user sees is purely a function of world state. The renderer walks the
 *  world tree and, for each sector, decides via {@link #projectedEdgePixels} how
 *  big it would appear on screen: distant sectors are drawn as a single coarse
 *  "super-voxel", while nearby sectors are recursed into for finer detail. This
 *  is the level-of-detail story made visible &mdash; the further away something is,
 *  the higher up the tree we stop.
 */
public final class WorldRenderer
{
    /** The on-screen edge size, in pixels, above which a sector is refined into its children. */
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
        Frustum frustum = camera.frustum();
        double focal = focalLengthPx(camera, height);

        List<Renderable> renderables = new ArrayList<>();
        collect(world.root(), camera, frustum, focal, renderables);

        // Painter's algorithm: draw far voxels first so near ones cover them.
        renderables.sort(Comparator.comparingDouble((Renderable r) -> r.distance).reversed());
        for ( Renderable r : renderables )
            drawVoxel(g, r.bounds, r.ether, viewProjection, camera, width, height);
    }

    /**
     *  Walks the tree, choosing the level of detail to draw at for each sector.
     *  <p>
     *  Frustum culling comes first: a sector whose bounds fall entirely outside the
     *  view volume is skipped wholesale, and with it the entire sub-tree beneath it.
     *  This is what keeps the walk cheap &mdash; we only ever descend into the
     *  fraction of the world the camera can actually see.
     */
    private void collect( WorldSector sector, CameraF64 camera, Frustum frustum, double focal, List<Renderable> out ) {
        if ( !frustum.intersects(sector.bounds()) )
            return; // outside the view: prune this sector and its whole sub-tree.

        double distance = camera.position().distance(sector.bounds().center());
        double edge = maxEdge(sector.bounds());

        boolean canRefine = sector.children() != null;
        boolean wantsRefine = projectedEdgePixels(edge, distance, focal) > _refineThresholdPx;

        if ( canRefine && wantsRefine ) {
            WorldTreeNode node = sector.children();
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
                collect(node.sector(i), camera, frustum, focal, out);
        } else {
            WorldSectorEtherData ether = sector.ether();
            if ( isMajoritySolid(ether) )
                out.add(new Renderable(sector.bounds(), ether, distance));
        }
    }

    /**
     *  @return {@code true} if the sector is <i>majority solid</i> &mdash; its
     *          combined per-side mixture is at least half non-transparent material.
     *  <p>
     *  This is the "draw it as a voxel?" decision. Gating on a solid majority (rather
     *  than merely "any solid face") keeps coarse LoD cubes from bulging out past the
     *  true surface: a super-voxel that is mostly air, with only a sliver of solid on
     *  one side, is left undrawn instead of being inflated into a full block.
     */
    private static boolean isMajoritySolid( WorldSectorEtherData ether ) {
        MaterialDistribution combined = ether.combined();
        double solid = 0, transparent = 0;
        for ( Material material : Material.values() ) {
            double fraction = combined.fractionOf(material);
            if ( MaterialPalette.isTransparent(material) )
                transparent += fraction;
            else
                solid += fraction;
        }
        return solid > 0 && solid >= transparent;
    }

    /**
     *  The material a given face should be painted with.
     *  <p>
     *  Normally this is the dominant material of that very {@link Side}. But an LoD
     *  super-voxel's ether is aggregated <i>per side</i>, so an individual face near
     *  the surface can come out air-dominant even when the cube is solid overall.
     *  Skipping such a face would leave a see-through hole in an otherwise solid
     *  cube (whereas a uniform leaf voxel never has this problem). To keep a drawn
     *  cube closed, an air-dominant face falls back to the sector's
     *  {@link #dominantSolidMaterial dominant solid material}. A transparent result
     *  therefore means the <i>whole</i> sector is transparent, and the face is
     *  genuinely not drawn.
     */
    public static Material faceMaterial( WorldSectorEtherData ether, Side side ) {
        Material material = ether.sideOf(side).dominantMaterial();
        if ( !MaterialPalette.isTransparent(material) )
            return material;
        return dominantSolidMaterial(ether);
    }

    /**
     *  @return The most prevalent non-transparent material across all six sides of
     *          {@code ether} (using the {@link WorldSectorEtherData#combined()
     *          combined} mixture), or {@link Material#AIR} if the sector is entirely
     *          transparent.
     */
    public static Material dominantSolidMaterial( WorldSectorEtherData ether ) {
        MaterialDistribution combined = ether.combined();
        Material best = Material.AIR;
        double bestFraction = 0;
        for ( Material material : Material.values() ) {
            if ( MaterialPalette.isTransparent(material) )
                continue;
            double fraction = combined.fractionOf(material);
            if ( fraction > bestFraction ) {
                bestFraction = fraction;
                best = material;
            }
        }
        return best;
    }

    private void drawVoxel( Graphics2D g, BoundsF64 bounds, WorldSectorEtherData ether, Mat4F64 vp, CameraF64 camera, int w, int h ) {
        VecF64[] corners = corners(bounds);
        double[][] screen = new double[8][];
        for ( int i = 0; i < 8; i++ ) {
            screen[i] = project(corners[i], vp, w, h);
            if ( screen[i] == null )
                return; // a corner is at/behind the camera: skip this voxel for the first draft.
        }

        for ( int f = 0; f < FACES.length; f++ ) {
            Side side = FACE_SIDES[f];
            // Each face is coloured by the material on that very side of the sector.
            Material material = faceMaterial(ether, side);
            if ( MaterialPalette.isTransparent(material) )
                continue; // only when the whole sector is transparent (e.g. all air).

            int[] face = FACES[f];
            VecF64 normal = side.normal();
            VecF64 faceCenter = corners[face[0]].add(corners[face[2]]).div(2);
            // Back-face culling: only draw faces whose outward normal points towards the camera.
            if ( normal.dot(camera.position().sub(faceCenter)) <= 0 )
                continue;

            Polygon polygon = new Polygon();
            for ( int corner : face )
                polygon.addPoint((int) Math.round(screen[corner][0]), (int) Math.round(screen[corner][1]));

            g.setColor(shade(MaterialPalette.colorOf(material), normal));
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

    // The six cube faces, each as four corner indices in boundary order (see
    // corners(): corner i has bit 0 = x, 1 = y, 2 = z), paired with the matching
    // Side, whose normal() is used both for culling and per-face material lookup.
    private static final int[][] FACES = {
            { 0, 1, 5, 4 }, // -Y bottom
            { 2, 3, 7, 6 }, // +Y top
            { 0, 2, 6, 4 }, // -X left
            { 1, 3, 7, 5 }, // +X right
            { 0, 1, 3, 2 }, // -Z front
            { 4, 5, 7, 6 }  // +Z back
    };

    private static final Side[] FACE_SIDES = {
            Side.NEG_Y, Side.POS_Y, Side.NEG_X, Side.POS_X, Side.NEG_Z, Side.POS_Z
    };

    /** A single cube to be drawn, tagged with its distance for painter's-order sorting. */
    private record Renderable(BoundsF64 bounds, WorldSectorEtherData ether, double distance) {}
}