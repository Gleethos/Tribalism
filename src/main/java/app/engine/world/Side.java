package app.engine.world;

import app.engine.primitives.VecF64;

/**
 *  One of the six faces of a cubic {@link WorldSector}, named by the axis and
 *  sign of its outward normal ({@code NEG_X} = the {@code -x} face, {@code POS_Y}
 *  = the {@code +y} (top) face, and so on).
 *  <p>
 *  Sides matter because a sector's material is stored <i>per side</i> (see
 *  {@link WorldSectorEtherData}): since materials are primarily a visual concern
 *  and only the outer faces of a cube are ever seen, the level-of-detail
 *  aggregation summarizes each face of a super-sector from only the matching
 *  faces of the sub-sectors lying on that face &mdash; never the hidden interior.
 */
public enum Side {
    NEG_X(0, false),
    POS_X(0, true),
    NEG_Y(1, false),
    POS_Y(1, true),
    NEG_Z(2, false),
    POS_Z(2, true);

    private final int     _axis;
    private final boolean _positive;

    Side( int axis, boolean positive ) {
        _axis     = axis;
        _positive = positive;
    }

    /** @return The axis this face is perpendicular to: {@code 0 = x, 1 = y, 2 = z}. */
    public int axis() { return _axis; }

    /** @return {@code true} for the positive-end face of its {@link #axis()} (e.g. {@code POS_Y}), {@code false} for the negative end. */
    public boolean isPositive() { return _positive; }

    /** @return The unit outward normal of this face. */
    public VecF64 normal() {
        double s = _positive ? 1.0 : -1.0;
        return switch ( _axis ) {
            case 0  -> VecF64.of(s, 0, 0);
            case 1  -> VecF64.of(0, s, 0);
            default -> VecF64.of(0, 0, s);
        };
    }

    /** @return The face on the opposite side of the cube. */
    public Side opposite() {
        return switch ( this ) {
            case NEG_X -> POS_X; case POS_X -> NEG_X;
            case NEG_Y -> POS_Y; case POS_Y -> NEG_Y;
            case NEG_Z -> POS_Z; case POS_Z -> NEG_Z;
        };
    }
}