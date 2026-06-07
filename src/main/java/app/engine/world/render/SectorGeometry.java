package app.engine.world.render;

import app.engine.world.World;
import app.engine.world.WorldSector;

import java.util.function.Consumer;

/**
 *  Turns a render unit &mdash; a whole chunk-sized sub-tree handed down by
 *  {@link World#collectSectorsForRendering} &mdash; into the {@link Quad}s of its visible
 *  surface, shared by every renderer backend so they show identical geometry.
 *  <p>
 *  The unit's sub-tree is meshed as one unified, greedy-meshed surface by
 *  {@link SectorMeshCache} (which culls every interior face, including the boundaries between
 *  sub-blocks) at the <b>level of detail</b> the visibility walk chose for it: a near unit is
 *  meshed finely (down to its leaves), a distant one coarsely (from big blocks of branch
 *  sectors). This is the single seam both backends use to go from "a sector to draw, at this
 *  detail" to "the quads of its surface".
 *  <p>
 *  Each backend supplies a {@code sink} that consumes the quads (the software path
 *  projects and fills them; the GPU path appends them to a retained vertex buffer).
 */
public final class SectorGeometry
{
    private SectorGeometry() {}

    /**
     *  Emits the surface quads of a render unit (a whole sub-tree) to {@code sink}, meshed at the
     *  requested level of detail.
     *
     *  @param sector         The render unit (sector, solid region, or leaf) to mesh.
     *  @param meshCache      The cache that meshes (and memoizes) the unit's surface.
     *  @param resolutionCap  The grid resolution to mesh at (see {@link SectorMeshCache#meshOf(WorldSector, int)});
     *                        smaller is coarser. The walk derives this from the unit's projected screen size.
     *  @param sink           Receives each quad of the unit's visible surface.
     */
    public static void emitChunk( WorldSector sector, SectorMeshCache meshCache, int resolutionCap, Consumer<Quad> sink ) {
        for ( Quad quad : meshCache.meshOf(sector, resolutionCap).quads() )
            sink.accept(quad);
    }
}