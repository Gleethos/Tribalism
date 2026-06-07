package app.engine.world.render;

import app.engine.world.Side;
import app.engine.world.TextureProfile;
import app.engine.world.World;
import app.engine.world.WorldSector;
import app.engine.world.WorldSectorEtherData;
import app.engine.world.WorldTreeNode;

import java.util.function.Consumer;

/**
 *  Turns a render unit &mdash; a whole chunk-sized sub-tree handed down by
 *  {@link World#collectSectorsForRendering} &mdash; into the {@link Quad}s of its visible
 *  surface, shared by every renderer backend so they show identical geometry.
 *  <p>
 *  There is no coarse-box level of detail: a unit is always meshed at full resolution.
 *  The sub-tree is walked and each terminal contributes its surface &mdash; a
 *  {@link SectorMeshCache greedy mesh} for a block of leaf voxels, or the six cube faces
 *  for a solid region or a lone opaque leaf. Because the per-block greedy meshes are
 *  memoized, re-meshing a chunk after one block changed still reuses the rest.
 *  <p>
 *  Each backend supplies a {@code sink} that consumes the quads (the software path
 *  projects and fills them; the GPU path appends them to a retained vertex buffer).
 */
public final class SectorGeometry
{
    private SectorGeometry() {}

    /**
     *  Emits the full-detail surface quads of a render unit (a whole sub-tree) to {@code sink}.
     *
     *  @param sector    The render unit (chunk-sized sector, solid occluder, or leaf) to mesh.
     *  @param meshCache The cache providing (and memoizing) greedy meshes of leaf-voxel blocks.
     *  @param sink      Receives each quad of the unit's visible surface.
     */
    public static void emitChunk( WorldSector sector, SectorMeshCache meshCache, Consumer<Quad> sink ) {
        if ( sector.isSolidOpaque() ) {
            emitCubeSurface(sector, sink); // a solid region: just its outer shell
            return;
        }
        if ( sector.isLeaf() ) {
            if ( sector.ether().isMajorityOpaque() )
                emitCubeSurface(sector, sink);
            return;
        }
        if ( sector.hasOnlyLeafChildren() ) {
            for ( Quad quad : meshCache.meshOf(sector).quads() )
                sink.accept(quad);
            return;
        }
        // A deeper branch: mesh each child sub-tree, so the whole chunk is one mesh.
        WorldTreeNode node = sector.children();
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            emitChunk(node.sector(i), meshCache, sink);
    }

    /** The six outer faces of a cube-shaped sector (solid region or lone leaf), with hole-filled appearance. */
    private static void emitCubeSurface( WorldSector sector, Consumer<Quad> sink ) {
        WorldSectorEtherData ether = sector.ether();
        for ( Side side : Side.values() ) {
            TextureProfile profile = WorldRenderer.faceProfile(ether, side);
            if ( !profile.isInvisible() )
                sink.accept(Cubes.faceQuad(sector.bounds(), side, profile));
        }
    }
}