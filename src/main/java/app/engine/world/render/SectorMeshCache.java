package app.engine.world.render;

import app.engine.world.Side;
import app.engine.world.WorldSector;
import app.engine.world.WorldTreeNode;
import sprouts.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 *  Builds and memoizes the occlusion-culled {@link SectorMesh} of a sector.
 *  <p>
 *  Meshing a block of voxels is relatively expensive, but a {@link WorldSector} is
 *  an immutable value, so it makes a perfect cache key: the same (structurally
 *  shared) block re-encountered on the next frame reuses its mesh for free. The
 *  cache is a {@link WeakHashMap}, so meshes for blocks no longer referenced by the
 *  world are garbage-collected. Keys are cheap because {@code WorldSector} memoizes
 *  its (otherwise deep) hash code, and {@code equals} short-circuits on identity for
 *  the common "same instance again" hit.
 *  <p>
 *  This deliberately lives in the demo renderer; the data model knows nothing of
 *  meshes.
 */
public final class SectorMeshCache
{
    private final Map<WorldSector, SectorMesh> _cache = new WeakHashMap<>();

    /**
     *  @param block A branch sector whose children are leaf voxels (an 8&times;8&times;8 block).
     *  @return Its occlusion-culled mesh, built on first request and cached thereafter.
     */
    public SectorMesh meshOf( WorldSector block ) {
        SectorMesh mesh = _cache.get(block);
        if ( mesh == null ) {
            mesh = build(block);
            _cache.put(block, mesh);
        }
        return mesh;
    }

    private static SectorMesh build( WorldSector block ) {
        WorldTreeNode node = block.children();
        int res = WorldTreeNode.RESOLUTION;
        List<Quad> quads = new ArrayList<>();

        for ( int z = 0; z < res; z++ ) {
            for ( int y = 0; y < res; y++ ) {
                for ( int x = 0; x < res; x++ ) {
                    WorldSector voxel = node.sector(WorldTreeNode.indexOf(x, y, z));
                    if ( !WorldRenderer.isMajorityOpaque(voxel.ether()) )
                        continue; // empty voxel contributes no faces

                    for ( Side side : Side.values() ) {
                        if ( !isExposed(node, x, y, z, side, res) )
                            continue; // the neighbour is opaque and hides this face: cull it
                        quads.add(Cubes.faceQuad(voxel.bounds(), side, voxel.ether().sideOf(side)));
                    }
                }
            }
        }
        return new SectorMesh(Tuple.of(Quad.class, quads.toArray(new Quad[0])));
    }

    /** @return {@code true} if the face of voxel {@code (x,y,z)} on {@code side} is visible. */
    private static boolean isExposed( WorldTreeNode node, int x, int y, int z, Side side, int res ) {
        int step = side.isPositive() ? 1 : -1;
        int nx = x, ny = y, nz = z;
        switch ( side.axis() ) {
            case 0  -> nx += step;
            case 1  -> ny += step;
            default -> nz += step;
        }
        if ( nx < 0 || ny < 0 || nz < 0 || nx >= res || ny >= res || nz >= res )
            return true; // block boundary: we cannot see the neighbouring block, so draw it
        WorldSector neighbour = node.sector(WorldTreeNode.indexOf(nx, ny, nz));
        return !WorldRenderer.isMajorityOpaque(neighbour.ether());
    }
}