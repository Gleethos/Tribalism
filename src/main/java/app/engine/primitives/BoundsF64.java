package app.engine.primitives;

import sprouts.Tuple;

/**
 *  An immutable, axis-aligned bounding box in 64-bit simulation space,
 *  defined by its {@code min} and {@code max} corners.
 *  <p>
 *  This is the spatial backbone of the world engine: every {@code WorldSection}
 *  occupies a cubic {@code BoundsF64}, and the recursive subdivision of the world
 *  tree is expressed purely in terms of {@link #subdivide(int)}.
 *  <p>
 *  Being a {@code record}, it carries full value semantics, which the persistent
 *  Sprouts collections rely upon.
 *
 *  @param min The corner with the smallest coordinate on every axis.
 *  @param max The corner with the largest coordinate on every axis.
 */
public record BoundsF64(
    VecF64 min,
    VecF64 max
) {
    public BoundsF64 {
        if ( min.x() > max.x() || min.y() > max.y() || min.z() > max.z() )
            throw new IllegalArgumentException(
                    "The 'min' corner " + min + " must not exceed the 'max' corner " + max + " on any axis."
                );
    }

    public static BoundsF64 of( VecF64 min, VecF64 max ) {
        return new BoundsF64(min, max);
    }

    /** @return A cube centered at {@code center} with the given edge {@code size}. */
    public static BoundsF64 cube( VecF64 center, double size ) {
        VecF64 half = VecF64.of(size / 2);
        return new BoundsF64(center.sub(half), center.add(half));
    }

    public VecF64 center() { return min.add(max).div(2); }

    /** @return The extent of the box along each axis, i.e. {@code max - min}. */
    public VecF64 size() { return max.sub(min); }

    public double width()  { return max.x() - min.x(); }
    public double height() { return max.y() - min.y(); }
    public double depth()  { return max.z() - min.z(); }

    public double volume() {
        VecF64 s = size();
        return s.x() * s.y() * s.z();
    }

    /** @return {@code true} if every edge has the same length (within a tiny epsilon). */
    public boolean isCube() {
        VecF64 s = size();
        double eps = 1e-9;
        return Math.abs(s.x() - s.y()) < eps && Math.abs(s.y() - s.z()) < eps;
    }

    public boolean contains( VecF64 point ) {
        return point.x() >= min.x() && point.x() <= max.x()
            && point.y() >= min.y() && point.y() <= max.y()
            && point.z() >= min.z() && point.z() <= max.z();
    }

    /** @return {@code true} if {@code other} lies entirely within this box. */
    public boolean contains( BoundsF64 other ) {
        return other.min.x() >= min.x() && other.max.x() <= max.x()
            && other.min.y() >= min.y() && other.max.y() <= max.y()
            && other.min.z() >= min.z() && other.max.z() <= max.z();
    }

    public boolean intersects( BoundsF64 other ) {
        return min.x() <= other.max.x() && max.x() >= other.min.x()
            && min.y() <= other.max.y() && max.y() >= other.min.y()
            && min.z() <= other.max.z() && max.z() >= other.min.z();
    }

    public BoundsF64 intersectionWith( BoundsF64 other ) {
        return BoundsF64.of(
                    VecF64.of(
                        Math.max(min.x(), other.min.x()),
                        Math.max(min.y(), other.min.y()),
                        Math.max(min.z(), other.min.z())
                    ),
                    VecF64.of(
                        Math.min(max.x(), other.max.x()),
                        Math.min(max.y(), other.max.y()),
                        Math.min(max.z(), other.max.z())
                    )
                );
    }

    /** @return The smallest box enclosing both this box and {@code other}. */
    public BoundsF64 union( BoundsF64 other ) {
        return BoundsF64.of(min.min(other.min), max.max(other.max));
    }

    public BoundsF64 add( VecF64 offset ) {
        return BoundsF64.of(min.add(offset), max.add(offset));
    }

    public BoundsF64 sub( VecF64 offset ) {
        return BoundsF64.of(min.sub(offset), max.sub(offset));
    }

    /**
     *  Splits this box into a regular {@code divisions x divisions x divisions} grid
     *  of equally sized sub-boxes.
     *  <p>
     *  The returned tuple is laid out in {@code x + y*divisions + z*divisions^2}
     *  order, which is exactly the linear ordering the world tree uses to store
     *  its {@code WorldSection} children. For the engine's {@code 8 x 8 x 8} nodes
     *  this yields {@code 512} sub-boxes.
     *
     *  @param divisions The number of cells along each axis (must be positive).
     *  @return A tuple of {@code divisions^3} sub-boxes.
     */
    public Tuple<BoundsF64> subdivide( int divisions ) {
        if ( divisions < 1 )
            throw new IllegalArgumentException("The number of divisions must be at least 1, but was " + divisions + ".");

        VecF64 step = size().div(divisions);
        BoundsF64[] cells = new BoundsF64[divisions * divisions * divisions];
        for ( int z = 0; z < divisions; z++ ) {
            for ( int y = 0; y < divisions; y++ ) {
                for ( int x = 0; x < divisions; x++ ) {
                    VecF64 cellMin = min.add(step.mul(VecF64.of(x, y, z)));
                    VecF64 cellMax = cellMin.add(step);
                    cells[x + y * divisions + z * divisions * divisions] = new BoundsF64(cellMin, cellMax);
                }
            }
        }
        return Tuple.of(BoundsF64.class, cells);
    }

    /**
     *  @return The single sub-box at grid coordinate {@code (x, y, z)} within a
     *          {@code divisions^3} subdivision of this box, without materializing
     *          the whole grid.
     */
    public BoundsF64 child( int x, int y, int z, int divisions ) {
        if ( x < 0 || y < 0 || z < 0 || x >= divisions || y >= divisions || z >= divisions )
            throw new IndexOutOfBoundsException("Cell (" + x + ", " + y + ", " + z + ") is outside a " + divisions + "^3 grid.");
        VecF64 step = size().div(divisions);
        VecF64 cellMin = min.add(step.mul(VecF64.of(x, y, z)));
        return new BoundsF64(cellMin, cellMin.add(step));
    }
}