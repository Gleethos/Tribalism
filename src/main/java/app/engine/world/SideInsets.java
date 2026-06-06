package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;

import java.util.Arrays;

/**
 *  How far a sector's content is recessed from each of its six {@link Side faces},
 *  as a fraction in {@code [0, 1]} of the sector's extent along that axis.
 *  <p>
 *  An inset of {@code 0} means content reaches all the way to that face; an inset
 *  of {@code 1} means that whole half-axis is empty. This is what lets a coarse
 *  level-of-detail box <i>shrink to fit</i> the solid matter inside it: a sector
 *  whose top half is pure air gets a {@code POS_Y} inset of {@code 0.5}, so the
 *  rendered super-voxel stops at the content surface instead of either sticking out
 *  as a full block or leaving a hole.
 *  <p>
 *  Insets are derived bottom-up from a sector's sub-sectors (see
 *  {@link WorldSector#insets()}); a leaf has all insets {@code 0}.
 *  <p>
 *  <b>Representation.</b> Like {@link TextureProfile}, this is an immutable
 *  <b>value class</b> over a flat {@code double[]} indexed by {@link Side#ordinal()},
 *  not a map: {@link #forSide} is a direct array read with no hashing, which matters
 *  because it sits on the renderer's per-box hot path. The array is encapsulated
 *  (owned, never exposed) and every value is kept clamped to {@code [0, 1]}.
 */
public final class SideInsets
{
    private static final int SIDES = Side.values().length;
    private static final SideInsets _NONE = new SideInsets(new double[SIDES]);

    /** Per-face inset fractions in {@code [0, 1]}, indexed by {@link Side#ordinal()}. Never exposed. */
    private final double[] _values;

    /** Takes ownership of {@code values}: callers must not retain or mutate it afterwards. */
    private SideInsets( double[] values ) {
        _values = values;
    }

    /** No inset on any face &mdash; content reaches every face (a leaf, or a solid cube). */
    public static SideInsets none() { return _NONE; }

    /** @return The inset fraction on {@code side} in {@code [0, 1]}, or {@code 0} if unset. */
    public double forSide( Side side ) {
        return _values[side.ordinal()];
    }

    /** @return A copy with {@code side}'s inset set to {@code inset} (clamped to {@code [0, 1]}). */
    public SideInsets with( Side side, double inset ) {
        double[] copy = _values.clone();
        copy[side.ordinal()] = clamp(inset);
        return new SideInsets(copy);
    }

    /** @return {@code true} if every face has a zero inset. */
    public boolean isNone() {
        for ( double v : _values )
            if ( v > 0 )
                return false;
        return true;
    }

    /**
     *  Shrinks {@code bounds} inward on each face by that face's inset. If opposing
     *  insets would cross, the box collapses to a zero-width slab at their midpoint
     *  on that axis (never an inverted box).
     *
     *  @param bounds The full sector bounds to inset.
     *  @return The content-fitting sub-box.
     */
    public BoundsF64 shrink( BoundsF64 bounds ) {
        if ( isNone() )
            return bounds;
        VecF64 min = bounds.min();
        VecF64 max = bounds.max();
        VecF64 size = bounds.size();

        double[] lo = { min.x(), min.y(), min.z() };
        double[] hi = { max.x(), max.y(), max.z() };
        double[] extent = { size.x(), size.y(), size.z() };
        Side[] negative = { Side.NEG_X, Side.NEG_Y, Side.NEG_Z };
        Side[] positive = { Side.POS_X, Side.POS_Y, Side.POS_Z };

        for ( int axis = 0; axis < 3; axis++ ) {
            double newLo = lo[axis] + forSide(negative[axis]) * extent[axis];
            double newHi = hi[axis] - forSide(positive[axis]) * extent[axis];
            if ( newLo > newHi ) {
                double mid = (newLo + newHi) / 2;
                newLo = newHi = mid;
            }
            lo[axis] = newLo;
            hi[axis] = newHi;
        }
        return BoundsF64.of(VecF64.of(lo[0], lo[1], lo[2]), VecF64.of(hi[0], hi[1], hi[2]));
    }

    private static double clamp( double v ) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof SideInsets other) ) return false;
        return Arrays.equals(_values, other._values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_values);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SideInsets[");
        Side[] all = Side.values();
        boolean first = true;
        for ( int i = 0; i < SIDES; i++ )
            if ( _values[i] > 0 ) {
                if ( !first ) sb.append(", ");
                sb.append(all[i]).append('=').append(_values[i]);
                first = false;
            }
        return sb.append(']').toString();
    }
}