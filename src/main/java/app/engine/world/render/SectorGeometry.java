package app.engine.world.render;

import app.engine.world.World;
import app.engine.world.WorldSector;

import java.util.function.Consumer;

/**
 *  Turns a render unit &mdash; a whole chunk-sized sub-tree handed down by
 *  {@link World#collectSectorsForRendering} &mdash; into the {@link Quad}s of its visible
 *  surface, shared by every renderer backend so they show identical geometry.
 *  <p>
 *  There is no coarse-box level of detail: the unit's whole sub-tree is meshed as one
 *  unified, greedy-meshed surface by {@link SectorMeshCache} (which culls every interior
 *  face, including the boundaries between the chunk's sub-blocks). This is the single seam
 *  both backends use to go from "a sector to draw" to "the quads of its surface".
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
     *  @param sector    The render unit (chunk-sized sector, solid region, or leaf) to mesh.
     *  @param meshCache The cache that meshes (and memoizes) the unit's surface.
     *  @param sink      Receives each quad of the unit's visible surface.
     */
    public static void emitChunk( WorldSector sector, SectorMeshCache meshCache, Consumer<Quad> sink ) {
        for ( Quad quad : meshCache.meshOf(sector).quads() )
            sink.accept(quad);
    }
}