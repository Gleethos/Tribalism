package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import sprouts.Association;

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
 *
 *  @param values A mapping from face to its inset fraction (absent = {@code 0}).
 */
public record SideInsets(
    Association<Side, Double> values
) {
    private static final SideInsets _NONE =
            new SideInsets(Association.between(Side.class, Double.class));

    /** No inset on any face &mdash; content reaches every face (a leaf, or a solid cube). */
    public static SideInsets none() { return _NONE; }

    public SideInsets {
        for ( var entry : values )
            if ( entry.second() < 0 || entry.second() > 1 )
                throw new IllegalArgumentException(
                        "The inset for " + entry.first() + " must be in [0, 1], but was " + entry.second() + "."
                    );
    }

    /** @return The inset fraction on {@code side} in {@code [0, 1]}, or {@code 0} if unset. */
    public double forSide( Side side ) {
        return values.get(side).orElse(0.0);
    }

    /** @return A copy with {@code side}'s inset set to {@code inset} (clamped to {@code [0, 1]}). */
    public SideInsets with( Side side, double inset ) {
        double clamped = inset < 0 ? 0 : (inset > 1 ? 1 : inset);
        return new SideInsets(values.put(side, clamped));
    }

    /** @return {@code true} if every face has a zero inset. */
    public boolean isNone() {
        for ( var entry : values )
            if ( entry.second() > 0 )
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
        if ( this.equals(_NONE) )
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
}