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

    /**
     *  Greedy-meshes the grid into visible surface quads, applying each cell's per-side
     *  {@link TextureProfile#inset() insets} at <b>every</b> resolution: a cell is meshed as the box its
     *  insets shrink it to, so a recessed face is drawn at its content surface rather than the cell
     *  boundary. The interesting case is two solid neighbours of different inset: the taller one's side
     *  face is only <i>partly</i> buried, so it must still emit the exposed "step wall". This is handled by
     *  a <b>coverage</b> cull &mdash; a flush face is dropped only when the neighbour's (also shrunk) content
     *  fully covers it (opaque, flush on the shared side, and at least as wide on both perpendicular axes);
     *  otherwise the face is emitted, so steps never leave holes. Faces that span their whole cell (no
     *  perpendicular inset) and share appearance <i>and</i> recession still merge into maximal rectangles,
     *  so flush, uniform regions (solid interiors, flat ground, voxel chunks) mesh as cheaply as before.
     */
    private static SectorMesh greedyMesh( BoundsF64 bounds, WorldSectorEtherData[] grid, int res ) {
        double[] origin = { bounds.min().x(), bounds.min().y(), bounds.min().z() };
        double[] cell = { bounds.size().x() / res, bounds.size().y() / res, bounds.size().z() / res };

        List<Quad> quads = new ArrayList<>();
        TextureProfile[][] face = new TextureProfile[res][res]; // this cell's face appearance, or null if none here
        double[][] depth = new double[res][res];                // its inset along the side's axis, in [0,1]
        boolean[][] mergeable = new boolean[res][res];           // spans its whole cell (no perpendicular inset)?
        double[][] uLoI = new double[res][res], uHiI = new double[res][res];
        double[][] vLoI = new double[res][res], vHiI = new double[res][res];
        boolean[][] used = new boolean[res][res];

        for ( Side side : Side.values() ) {
            int a = side.axis();
            int u = otherAxis(a, 0), v = otherAxis(a, 1);
            Side uNeg = sideFor(u, false), uPos = sideFor(u, true);
            Side vNeg = sideFor(v, false), vPos = sideFor(v, true);
            Side opposite = side.opposite();
            int step = side.isPositive() ? 1 : -1;

            for ( int la = 0; la < res; la++ ) {
                for ( int vv = 0; vv < res; vv++ )
                    for ( int uu = 0; uu < res; uu++ ) {
                        used[uu][vv] = false;
                        face[uu][vv] = null;
                        WorldSectorEtherData e = cellAt(grid, res, a, u, v, la, uu, vv);
                        if ( e == null || !e.isMajorityOpaque() )
                            continue;
                        double insetA = e.insetOf(side);
                        if ( insetA == 0 && covered(grid, res, a, u, v, la + step, uu, vv, e, opposite, uNeg, uPos, vNeg, vPos) )
                            continue; // flush face fully hidden behind the neighbour's content.
                        face[uu][vv] = WorldRenderer.faceProfile(e, side);
                        depth[uu][vv] = insetA;
                        uLoI[uu][vv] = e.insetOf(uNeg); uHiI[uu][vv] = e.insetOf(uPos);
                        vLoI[uu][vv] = e.insetOf(vNeg); vHiI[uu][vv] = e.insetOf(vPos);
                        mergeable[uu][vv] = uLoI[uu][vv] == 0 && uHiI[uu][vv] == 0 && vLoI[uu][vv] == 0 && vHiI[uu][vv] == 0;
                    }

                double aLo = origin[a] + la * cell[a], aHi = aLo + cell[a];
                for ( int vv = 0; vv < res; vv++ )
                    for ( int uu = 0; uu < res; uu++ ) {
                        TextureProfile p = face[uu][vv];
                        if ( p == null || used[uu][vv] )
                            continue;
                        double d = depth[uu][vv];
                        double aPlane = side.isPositive() ? aHi - d * cell[a] : aLo + d * cell[a];
                        if ( !mergeable[uu][vv] ) {
                            // A face shrunk on a perpendicular axis can't tile with its neighbours: emit it alone.
                            used[uu][vv] = true;
                            quads.add(faceQuad(a, u, v, side, p, aPlane,
                                    origin[u] + (uu + uLoI[uu][vv]) * cell[u], origin[u] + (uu + 1 - uHiI[uu][vv]) * cell[u],
                                    origin[v] + (vv + vLoI[uu][vv]) * cell[v], origin[v] + (vv + 1 - vHiI[uu][vv]) * cell[v]));
                            continue;
                        }
                        // Merge full faces sharing appearance AND recession into a maximal rectangle.
                        int w = 1;
                        while ( uu + w < res && canMerge(face, used, mergeable, depth, p, d, uu + w, vv) )
                            w++;
                        int h = 1;
                        grow:
                        while ( vv + h < res ) {
                            for ( int k = 0; k < w; k++ )
                                if ( !canMerge(face, used, mergeable, depth, p, d, uu + k, vv + h) )
                                    break grow;
                            h++;
                        }
                        for ( int dv = 0; dv < h; dv++ )
                            for ( int du = 0; du < w; du++ )
                                used[uu + du][vv + dv] = true;
                        quads.add(faceQuad(a, u, v, side, p, aPlane,
                                origin[u] + uu * cell[u], origin[u] + (uu + w) * cell[u],
                                origin[v] + vv * cell[v], origin[v] + (vv + h) * cell[v]));
                    }
            }
        }
        return new SectorMesh(Tuple.of(Quad.class, quads.toArray(new Quad[0])));
    }

    /**
     *  @return Whether cell {@code e}'s flush face toward the neighbour at {@code (nla,uu,vv)} is fully
     *          hidden: the neighbour exists, is opaque, reaches the shared boundary ({@code opposite} inset
     *          {@code 0}), and its content is at least as wide as {@code e}'s on both perpendicular axes.
     *          When it is only partly covered (a shorter/narrower neighbour), this is {@code false}, so the
     *          exposed step wall is still emitted.
     */
    private static boolean covered(
        WorldSectorEtherData[] grid, int res, int a, int u, int v, int nla, int uu, int vv,
        WorldSectorEtherData e, Side opposite, Side uNeg, Side uPos, Side vNeg, Side vPos
    ) {
        if ( nla < 0 || nla >= res )
            return false; // the unit boundary: always exposed.
        WorldSectorEtherData n = cellAt(grid, res, a, u, v, nla, uu, vv);
        if ( n == null || !n.isMajorityOpaque() || n.insetOf(opposite) > 0 )
            return false;
        return n.insetOf(uNeg) <= e.insetOf(uNeg) && n.insetOf(uPos) <= e.insetOf(uPos)
            && n.insetOf(vNeg) <= e.insetOf(vNeg) && n.insetOf(vPos) <= e.insetOf(vPos);
    }

    /** @return Whether the full face at {@code (uu,vv)} can join a merge run: unused, mergeable, present, same recession and appearance. */
    private static boolean canMerge(
        TextureProfile[][] face, boolean[][] used, boolean[][] mergeable, double[][] depth,
        TextureProfile p, double d, int uu, int vv
    ) {
        return !used[uu][vv] && mergeable[uu][vv] && face[uu][vv] != null
            && depth[uu][vv] == d && p.sameAppearance(face[uu][vv]);
    }

    /** @return The ether of the grid cell at ({@code la} along axis {@code a}, {@code uu}/{@code vv} on the free axes). */
    private static @Nullable WorldSectorEtherData cellAt(
        WorldSectorEtherData[] grid, int res, int a, int u, int v, int la, int uu, int vv
    ) {
        return grid[index(coord(0, a, la, u, uu, v, vv), coord(1, a, la, u, uu, v, vv), coord(2, a, la, u, uu, v, vv), res)];
    }

    /** @return The {@link Side} on {@code axis} (0=X, 1=Y, 2=Z) in the positive or negative direction. */
    private static Side sideFor( int axis, boolean positive ) {
        return switch ( axis ) {
            case 0  -> positive ? Side.POS_X : Side.NEG_X;
            case 1  -> positive ? Side.POS_Y : Side.NEG_Y;
            default -> positive ? Side.POS_Z : Side.NEG_Z;
        };
    }

    /** Builds the world-space quad for a face on {@code side} at the plane {@code aPlane} (axis {@code a}), spanning {@code [uLo,uHi]}x{@code [vLo,vHi]}. */
    private static Quad faceQuad(
        int a, int u, int v, Side side, TextureProfile p,
        double aPlane, double uLo, double uHi, double vLo, double vHi
    ) {
        double[] lo = new double[3], hi = new double[3];
        lo[a] = hi[a] = aPlane; // a zero-thickness box: Cubes.faceQuad reads this side's face off it.
        lo[u] = uLo; hi[u] = uHi;
        lo[v] = vLo; hi[v] = vHi;
        return Cubes.faceQuad(BoundsF64.of(VecF64.of(lo[0], lo[1], lo[2]), VecF64.of(hi[0], hi[1], hi[2])), side, p);
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