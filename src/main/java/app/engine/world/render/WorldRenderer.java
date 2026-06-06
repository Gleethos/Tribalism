package app.engine.world.render;

import app.engine.primitives.*;
import app.engine.world.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *  A first-draft renderer that draws a {@link World} into a {@link Graphics2D}
 *  surface as shaded voxel faces.
 *  <p>
 *  What the user sees is purely a function of world state. The renderer does not walk
 *  the world tree itself: it asks the world which sectors are visible from a camera via
 *  {@link World#collectSectorsForRendering}, which applies frustum culling, occlusion
 *  culling and the level-of-detail decision and hands back only the sectors worth
 *  drawing. The renderer's sole job is turning each such sector into geometry:
 *  <ul>
 *      <li>a fully-solid or too-small/lone sector becomes one coarse, inset-fitted
 *          box ({@code wantsDetail == false});</li>
 *      <li>a full-detail block of voxels (a branch of all leaves) becomes a cached,
 *          occlusion-culled {@link SectorMesh}.</li>
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

    public void render( Graphics2D g, World world, ScreenId screenId ) {
        Screen screen = world.screen(screenId).orElse(null);
        int width  = screen == null ? 0 : screen.width();
        int height = screen == null ? 0 : screen.height();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(_skyColor);
        g.fillRect(0, 0, width, height);

        List<ScreenFace> faces = new ArrayList<>();
        World.RenderStats stats = world.collectSectorsForRendering(
                screenId, _refineThresholdPx,
                ( sector, wantsDetail, view ) -> {
                    if ( sector.isSolidOpaque() ) {
                        emitBox(sector, view, _lightDirection, faces);
                    } else if ( !wantsDetail || sector.isLeaf() ) {
                        if ( isMajorityOpaque(sector.ether()) )
                            emitBox(sector, view, _lightDirection, faces);
                    } else if ( sector.hasOnlyLeafChildren() ) {
                        for ( Quad quad : _meshCache.meshOf(sector).quads() )
                            emitQuad(quad, view, _lightDirection, faces);
                    }
                });
        _occlusionCulled = stats.occlusionCulledSectors();

        // Painter's algorithm: draw far faces first so near ones cover them.
        faces.sort(Comparator.comparingDouble((ScreenFace f) -> f.distance).reversed());
        for ( ScreenFace f : faces ) {
            g.setColor(f.color);
            g.fillPolygon(f.xs, f.ys, 4);
        }
        _facesDrawn = faces.size();
    }

    private static void emitBox( WorldSector sector, ViewInfo view, VecF64 lightDirection, List<ScreenFace> out ) {
        BoundsF64 bounds = sector.insets().shrink(sector.bounds());
        WorldSectorEtherData ether = sector.ether();
        for ( Side side : Side.values() ) {
            TextureProfile profile = faceProfile(ether, side);
            if ( !profile.isInvisible() )
                emitQuad(Cubes.faceQuad(bounds, side, profile), view, lightDirection, out);
        }
    }

    private static void emitQuad( Quad quad, ViewInfo view, VecF64 lightDirection, List<ScreenFace> out ) {
        VecF64 center = quad.centroid();
        if ( quad.normal().dot(view.camera().position().sub(center)) <= 0 )
            return; // back-face

        double[] s0 = view.project(quad.c0());
        double[] s1 = view.project(quad.c1());
        double[] s2 = view.project(quad.c2());
        double[] s3 = view.project(quad.c3());
        if ( s0 == null || s1 == null || s2 == null || s3 == null )
            return; // a corner is at/behind the camera: skip this face for the first draft.

        int[] xs = { (int) Math.round(s0[0]), (int) Math.round(s1[0]), (int) Math.round(s2[0]), (int) Math.round(s3[0]) };
        int[] ys = { (int) Math.round(s0[1]), (int) Math.round(s1[1]), (int) Math.round(s2[1]), (int) Math.round(s3[1]) };
        if ( offScreen(xs, ys, view.width(), view.height()) )
            return; // the whole face is outside the viewport: nothing to draw.

        Color color = shade(TexturePalette.colorOf(quad.profile()), quad.normal(), lightDirection);
        out.add(new ScreenFace(xs, ys, color, view.camera().position().distance(center)));
    }

    /** @return {@code true} if the polygon's screen bounding box lies entirely outside {@code [0,w] x [0,h]}. */
    private static boolean offScreen( int[] xs, int[] ys, int w, int h ) {
        int minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        int maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        int minY = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
        int maxY = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
        return maxX < 0 || minX > w || maxY < 0 || minY > h;
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

    private static int clampColor( int v ) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** A face ready to draw: its screen polygon (4 points), colour, and distance for painter's ordering. */
    private record ScreenFace(int[] xs, int[] ys, Color color, double distance) {}
}