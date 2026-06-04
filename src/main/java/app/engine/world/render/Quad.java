package app.engine.world.render;

import app.engine.primitives.VecF64;
import app.engine.world.TextureProfile;

/**
 *  A single quadrilateral face in 64-bit world space, ready to be projected to a
 *  2D {@link java.awt.Polygon}. It is the common currency of the renderer: both a
 *  coarse LoD box and a meshed voxel surface decompose into {@code Quad}s, which
 *  are then back-face culled, depth-sorted and filled uniformly.
 *
 *  @param c0      First corner (corners wind consistently with {@code normal}).
 *  @param c1      Second corner.
 *  @param c2      Third corner.
 *  @param c3      Fourth corner.
 *  @param normal  The outward face normal (for culling and shading).
 *  @param profile The appearance qualities used to colour the face.
 */
public record Quad(
    VecF64 c0,
    VecF64 c1,
    VecF64 c2,
    VecF64 c3,
    VecF64 normal,
    TextureProfile profile
) {
    /** @return The centre of the face (used for depth sorting and back-face culling). */
    public VecF64 centroid() {
        return c0.add(c1).add(c2).add(c3).div(4);
    }
}