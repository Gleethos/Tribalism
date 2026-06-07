package app.engine.world.render;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import app.engine.world.Side;
import app.engine.world.TextureProfile;
import app.engine.world.WorldSector;
import app.engine.world.WorldSectorEtherData;
import app.engine.world.WorldTreeNode;
import org.jspecify.annotations.Nullable;
import sprouts.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 *  Builds and memoizes the occlusion-culled, <b>greedy-meshed</b> {@link SectorMesh}
 *  of a whole render unit (a chunk-sized sub-tree).
 *  <p>
 *  Meshing a chunk is relatively expensive, but a {@link WorldSector} is an immutable
 *  value, so it makes a perfect cache key: the same (structurally shared) chunk
 *  re-encountered on the next frame reuses its mesh for free. The cache is a
 *  {@link WeakHashMap}, so meshes for chunks no longer referenced by the world are
 *  garbage-collected. Keys are cheap because {@code WorldSector} memoizes its (otherwise
 *  deep) hash code, and {@code equals} short-circuits on identity for the common "same
 *  instance again" hit.
 *  <p>
 *  <b>Unified meshing, at a chosen level of detail.</b> The sub-tree is first rasterized
 *  into a single uniform voxel grid, then greedy-meshed in one pass: within each face
 *  direction and layer, adjacent <i>exposed</i> voxel faces that share the same appearance
 *  are merged into the largest possible rectangles. Because it is one grid, every interior
 *  face is culled &mdash; including the faces on the boundaries <i>between</i> sub-blocks,
 *  which a per-block mesher would have drawn. A solid block collapses to its six shell faces;
 *  a flat grass top to a single quad. The picture is unchanged (merged rectangles are
 *  coplanar and uniformly coloured); only the quad count drops.
 *  <p>
 *  The grid resolution is a <b>level-of-detail knob</b> ({@link #meshOf(WorldSector, int)}).
 *  At the finest resolution the grid reaches the sub-tree's leaves (full detail); at a coarser
 *  resolution the rasterizer stops higher up and fills each cell with that <i>branch</i>
 *  sector's own (level-of-detail aggregated) ether &mdash; so a distant sector is greedy-meshed
 *  from big blocks of branch sectors rather than leaves, for a few coarse quads instead of
 *  thousands. Valid resolutions are powers of {@value WorldTreeNode#RESOLUTION}
 *  ({@code 1, 8, 64}), because each tree level divides the grid by that factor.
 *  <p>
 *  This deliberately lives in the renderer; the data model knows nothing of meshes.
 */
public final class SectorMeshCache
{
    /** Cap on the grid edge so a deep/large sector meshes coarsely rather than blowing memory ({@code res}&sup3;). */
    public static final int MAX_GRID_RES = 64;

    /** Per sector, the meshes built for it keyed by grid resolution. Outer map is weak so meshes of
     *  no-longer-referenced sectors are garbage-collected; the inner map holds the few LoD levels. */
    private final Map<WorldSector, Map<Integer, SectorMesh>> _cache = new WeakHashMap<>();

    /**
     *  Meshes a render unit at its full available detail (the finest resolution its sub-tree supports,
     *  capped at {@link #MAX_GRID_RES}). Equivalent to {@link #meshOf(WorldSector, int)} with the cap.
     *
     *  @param sector A render unit (a chunk-sized sub-tree, a solid region, or a leaf).
     *  @return Its occlusion-culled, greedy-meshed visible surface.
     */
    public SectorMesh meshOf( WorldSector sector ) {
        return meshOf(sector, MAX_GRID_RES);
    }

    /**
     *  Meshes a render unit at the requested level of detail, building on first request and caching
     *  thereafter (per sector, per resolution).
     *
     *  @param sector         A render unit (a sub-tree, a solid region, or a leaf).
     *  @param resolutionCap  The desired grid edge; clamped to the sector's available detail and to
     *                        {@link #MAX_GRID_RES}, and snapped down to a power of
     *                        {@value WorldTreeNode#RESOLUTION}. A smaller value meshes the sector
     *                        more coarsely (from branch sectors rather than leaves).
     *  @return Its occlusion-culled, greedy-meshed visible surface at that detail.
     */
    public SectorMesh meshOf( WorldSector sector, int resolutionCap ) {
        int res = Math.min(snapDownToPowerOfResolution(resolutionCap), gridResolution(sector));
        return _cache.computeIfAbsent(sector, s -> new HashMap<>())
                     .computeIfAbsent(res, r -> build(sector, r));
    }

    private static SectorMesh build( WorldSector sector, int res ) {
        WorldSectorEtherData[] grid = new WorldSectorEtherData[res * res * res];
        rasterize(sector, grid, res, 0, 0, 0, res);
        return greedyMesh(sector.bounds(), grid, res);
    }

    /** @return {@code cap} reduced to the largest power of {@link WorldTreeNode#RESOLUTION} that is {@code <= cap} (at least 1). */
    private static int snapDownToPowerOfResolution( int cap ) {
        int res = 1;
        while ( res * WorldTreeNode.RESOLUTION <= cap && res * WorldTreeNode.RESOLUTION <= MAX_GRID_RES )
            res *= WorldTreeNode.RESOLUTION;
        return res;
    }

    /** @return The grid edge to mesh {@code sector} at its finest: {@code 8^depth} to its deepest leaf, capped at {@link #MAX_GRID_RES}. */
    private static int gridResolution( WorldSector sector ) {
        int depth = maxLeafDepth(sector);
        int res = 1;
        while ( depth-- > 0 && res * WorldTreeNode.RESOLUTION <= MAX_GRID_RES )
            res *= WorldTreeNode.RESOLUTION;
        return res;
    }

    private static int maxLeafDepth( WorldSector sector ) {
        if ( sector.isLeaf() )
            return 0;
        WorldTreeNode node = sector.children();
        int max = 0;
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            max = Math.max(max, maxLeafDepth(node.sector(i)));
        return 1 + max;
    }

    /** Fills the grid cells covered by {@code sector} (a region {@code cellRes} cells on a side at {@code x0,y0,z0}). */
    private static void rasterize(
        WorldSector sector, WorldSectorEtherData[] grid, int res, int x0, int y0, int z0, int cellRes
    ) {
        if ( sector.isLeaf() || cellRes == 1 ) {
            WorldSectorEtherData ether = sector.ether();
            if ( !ether.isInvisible() )
                fill(grid, res, x0, y0, z0, cellRes, ether);
            return;
        }
        int sub = cellRes / WorldTreeNode.RESOLUTION;
        WorldTreeNode node = sector.children();
        for ( int z = 0; z < WorldTreeNode.RESOLUTION; z++ )
            for ( int y = 0; y < WorldTreeNode.RESOLUTION; y++ )
                for ( int x = 0; x < WorldTreeNode.RESOLUTION; x++ )
                    rasterize(node.sector(WorldTreeNode.indexOf(x, y, z)), grid, res,
                              x0 + x * sub, y0 + y * sub, z0 + z * sub, sub);
    }

    private static void fill(
        WorldSectorEtherData[] grid, int res, int x0, int y0, int z0, int size, WorldSectorEtherData ether
    ) {
        for ( int z = 0; z < size; z++ )
            for ( int y = 0; y < size; y++ )
                for ( int x = 0; x < size; x++ )
                    grid[index(x0 + x, y0 + y, z0 + z, res)] = ether;
    }

    private static SectorMesh greedyMesh( BoundsF64 bounds, WorldSectorEtherData[] grid, int res ) {
        double[] origin = { bounds.min().x(), bounds.min().y(), bounds.min().z() };
        double cell = bounds.size().x() / res;

        List<Quad> quads = new ArrayList<>();
        TextureProfile[][] mask = new TextureProfile[res][res];
        boolean[][] used = new boolean[res][res];

        for ( Side side : Side.values() ) {
            int a = side.axis();
            int u = otherAxis(a, 0), v = otherAxis(a, 1);
            int step = side.isPositive() ? 1 : -1;

            for ( int la = 0; la < res; la++ ) {
                // Mask of exposed, opaque faces in this layer, keyed by appearance.
                for ( int vv = 0; vv < res; vv++ )
                    for ( int uu = 0; uu < res; uu++ ) {
                        mask[uu][vv] = faceAt(grid, res, side, a, la, u, uu, v, vv, step);
                        used[uu][vv] = false;
                    }
                // Merge equal, adjacent faces into maximal rectangles (greedy).
                for ( int vv = 0; vv < res; vv++ )
                    for ( int uu = 0; uu < res; uu++ ) {
                        TextureProfile p = mask[uu][vv];
                        if ( p == null || used[uu][vv] )
                            continue;
                        int w = 1;
                        while ( uu + w < res && !used[uu + w][vv] && p.equals(mask[uu + w][vv]) )
                            w++;
                        int h = 1;
                        grow:
                        while ( vv + h < res ) {
                            for ( int k = 0; k < w; k++ )
                                if ( used[uu + k][vv + h] || !p.equals(mask[uu + k][vv + h]) )
                                    break grow;
                            h++;
                        }
                        for ( int dv = 0; dv < h; dv++ )
                            for ( int du = 0; du < w; du++ )
                                used[uu + du][vv + dv] = true;
                        quads.add(rectQuad(origin, cell, side, a, u, v, la, uu, vv, w, h, p));
                    }
            }
        }
        return new SectorMesh(Tuple.of(Quad.class, quads.toArray(new Quad[0])));
    }

    /**
     *  @return The appearance of the grid cell ({@code la} along axis {@code a}, {@code uu}/{@code vv}
     *          on the free axes) on {@code side} if that face is opaque <i>and</i> exposed (the
     *          neighbour in that direction is empty or outside the grid), else {@code null}.
     */
    private static @Nullable TextureProfile faceAt(
        WorldSectorEtherData[] grid, int res, Side side, int a, int la, int u, int uu, int v, int vv, int step
    ) {
        WorldSectorEtherData ether = grid[index(coord(0, a, la, u, uu, v, vv),
                                                coord(1, a, la, u, uu, v, vv),
                                                coord(2, a, la, u, uu, v, vv), res)];
        if ( ether == null || !ether.isMajorityOpaque() )
            return null;
        int nla = la + step;
        if ( nla >= 0 && nla < res ) {
            WorldSectorEtherData neighbour = grid[index(coord(0, a, nla, u, uu, v, vv),
                                                        coord(1, a, nla, u, uu, v, vv),
                                                        coord(2, a, nla, u, uu, v, vv), res)];
            if ( neighbour != null && neighbour.isMajorityOpaque() )
                return null; // buried between two opaque voxels: cull this face
        }
        return WorldRenderer.faceProfile(ether, side);
    }

    /** Builds the world-space quad for a merged rectangle, reusing {@link Cubes} for winding/normal. */
    private static Quad rectQuad(
        double[] origin, double cell, Side side, int a, int u, int v, int la, int u0, int v0, int w, int h, TextureProfile p
    ) {
        double[] lo = new double[3];
        double[] hi = new double[3];
        lo[a] = origin[a] + la * cell;       hi[a] = lo[a] + cell;
        lo[u] = origin[u] + u0 * cell;       hi[u] = lo[u] + w * cell;
        lo[v] = origin[v] + v0 * cell;       hi[v] = lo[v] + h * cell;
        BoundsF64 box = BoundsF64.of(VecF64.of(lo[0], lo[1], lo[2]), VecF64.of(hi[0], hi[1], hi[2]));
        return Cubes.faceQuad(box, side, p);
    }

    private static int index( int x, int y, int z, int res ) {
        return x + y * res + z * res * res;
    }

    /** @return The {@code which}-th (0 or 1) axis other than {@code a}, in ascending order. */
    private static int otherAxis( int a, int which ) {
        int[] others = a == 0 ? new int[]{ 1, 2 } : a == 1 ? new int[]{ 0, 2 } : new int[]{ 0, 1 };
        return others[which];
    }

    /** @return The grid coordinate on {@code axis} for a cell at ({@code la} on {@code a}, {@code uu} on {@code u}, {@code vv} on {@code v}). */
    private static int coord( int axis, int a, int la, int u, int uu, int v, int vv ) {
        if ( axis == a ) return la;
        if ( axis == u ) return uu;
        return vv;
    }
}