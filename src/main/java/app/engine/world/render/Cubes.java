package app.engine.world.render;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import app.engine.world.Side;
import app.engine.world.TextureProfile;

/**
 *  Small geometry helper shared by the box and mesh paths of the renderer: it
 *  turns an axis-aligned {@link BoundsF64} and a {@link Side} into the four world
 *  corners of that face. Keeping it in one place means a coarse LoD box and a
 *  meshed voxel face are built identically (same winding and outward normal).
 */
final class Cubes {

    private Cubes() {}

    // The four corners of each face, as corner indices into corners(): corner i has
    // bit 0 = x, bit 1 = y, bit 2 = z (0 = min, 1 = max). Order gives a consistent
    // outward-facing winding (matching Side.normal()).
    private static final int[][] FACE_CORNERS = {
            { 0, 1, 5, 4 }, // NEG_Y
            { 2, 3, 7, 6 }, // POS_Y
            { 0, 2, 6, 4 }, // NEG_X
            { 1, 3, 7, 5 }, // POS_X
            { 0, 1, 3, 2 }, // NEG_Z
            { 4, 5, 7, 6 }  // POS_Z
    };

    private static int faceIndex( Side side ) {
        return switch ( side ) {
            case NEG_Y -> 0; case POS_Y -> 1;
            case NEG_X -> 2; case POS_X -> 3;
            case NEG_Z -> 4; case POS_Z -> 5;
        };
    }

    private static VecF64 corner( BoundsF64 b, int i ) {
        VecF64 lo = b.min(), hi = b.max();
        return VecF64.of(
                (i & 1) == 0 ? lo.x() : hi.x(),
                (i & 2) == 0 ? lo.y() : hi.y(),
                (i & 4) == 0 ? lo.z() : hi.z()
        );
    }

    /** @return The face of {@code bounds} on {@code side}, as a {@link Quad} carrying {@code profile}. */
    static Quad faceQuad( BoundsF64 bounds, Side side, TextureProfile profile ) {
        int[] c = FACE_CORNERS[faceIndex(side)];
        return new Quad(
                corner(bounds, c[0]),
                corner(bounds, c[1]),
                corner(bounds, c[2]),
                corner(bounds, c[3]),
                side.normal(),
                profile
        );
    }
}