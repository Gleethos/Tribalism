package app.engine.world.render;

import sprouts.Tuple;

/**
 *  The occlusion-culled set of visible voxel faces of a sector, in world space.
 *  <p>
 *  Built once from an 8&times;8&times;8 block of leaf voxels (see
 *  {@link SectorMeshCache}): a voxel face is included only if it is exposed &mdash;
 *  the neighbouring voxel in that direction is empty (or lies outside the block).
 *  Faces buried between two opaque voxels are dropped, so a solid block collapses
 *  from up to {@code 512 * 6 = 3072} faces to just its outer shell.
 *
 *  @param quads The visible faces.
 */
public record SectorMesh(
    Tuple<Quad> quads
) {
    /** @return The number of visible faces in this mesh. */
    public int faceCount() {
        return quads.size();
    }
}