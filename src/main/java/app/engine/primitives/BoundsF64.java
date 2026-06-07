package app.engine.primitives;

import sprouts.Tuple;

import java.util.Objects;

/**
 *  An immutable, axis-aligned bounding box in 64-bit simulation space,
 *  defined by its {@code min} and {@code max} corners.
 *  <p>
 *  This is the spatial backbone of the world engine: every {@code WorldSector}
 *  occupies a cubic {@code BoundsF64}, and the recursive subdivision of the world
 *  tree is expressed purely in terms of {@link #subdivide(int)}.
 *  <p>
 *  It is an immutable <b>value</b> (with {@code equals}/{@code hashCode} over its two
 *  corners, which the persistent Sprouts collections rely upon). It is a {@code class}
 *  rather than a {@code record} so it can cache its {@link #center() centre}: the centre
 *  is read very heavily (per visible sector, every frame, for distance ordering) but is
 *  cheap to derive, so &mdash; unlike the expensive lazily-cached values elsewhere &mdash;
 *  it is computed <b>eagerly</b> once in the constructor and returned as a plain field.
 *  (A {@code Lazy} wrapper would cost two extra allocations per box, more than the single
 *  vector it would defer; eager is strictly cheaper for a value created in this volume.)
 */
public final class BoundsF64
{
    private final VecF64 _min;
    private final VecF64 _max;
    private final VecF64 _center;

    public BoundsF64( VecF64 min, VecF64 max ) {
        if ( min.x() > max.x() || min.y() > max.y() || min.z() > max.z() )
            throw new IllegalArgumentException(
                    "The 'min' corner " + min + " must not exceed the 'max' corner " + max + " on any axis."
                );
        _min = min;
        _max = max;
        // Eagerly derived (see class note): one vector, computed directly to avoid intermediates.
        _center = VecF64.of((min.x() + max.x()) / 2, (min.y() + max.y()) / 2, (min.z() + max.z()) / 2);
    }

    public static BoundsF64 of( VecF64 min, VecF64 max ) {
        return new BoundsF64(min, max);
    }

    /** @return A cube centered at {@code center} with the given edge {@code size}. */
    public static BoundsF64 cube( VecF64 center, double size ) {
        VecF64 half = VecF64.of(size / 2);
        return new BoundsF64(center.sub(half), center.add(half));
    }

    public VecF64 min() { return _min; }
    public VecF64 max() { return _max; }

    /** @return The centre point of the box (cached). */
    public VecF64 center() { return _center; }

    /** @return The extent of the box along each axis, i.e. {@code max - min}. */
    public VecF64 size() { return _max.sub(_min); }

    public double width()  { return _max.x() - _min.x(); }
    public double height() { return _max.y() - _min.y(); }
    public double depth()  { return _max.z() - _min.z(); }

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
        return point.x() >= _min.x() && point.x() <= _max.x()
            && point.y() >= _min.y() && point.y() <= _max.y()
            && point.z() >= _min.z() && point.z() <= _max.z();
    }

    /** @return {@code true} if {@code other} lies entirely within this box. */
    public boolean contains( BoundsF64 other ) {
        return other._min.x() >= _min.x() && other._max.x() <= _max.x()
            && other._min.y() >= _min.y() && other._max.y() <= _max.y()
            && other._min.z() >= _min.z() && other._max.z() <= _max.z();
    }

    public boolean intersects( BoundsF64 other ) {
        return _min.x() <= other._max.x() && _max.x() >= other._min.x()
            && _min.y() <= other._max.y() && _max.y() >= other._min.y()
            && _min.z() <= other._max.z() && _max.z() >= other._min.z();
    }

    public BoundsF64 intersectionWith( BoundsF64 other ) {
        return BoundsF64.of(
                    VecF64.of(
                        Math.max(_min.x(), other._min.x()),
                        Math.max(_min.y(), other._min.y()),
                        Math.max(_min.z(), other._min.z())
                    ),
                    VecF64.of(
                        Math.min(_max.x(), other._max.x()),
                        Math.min(_max.y(), other._max.y()),
                        Math.min(_max.z(), other._max.z())
                    )
                );
    }

    /** @return The smallest box enclosing both this box and {@code other}. */
    public BoundsF64 union( BoundsF64 other ) {
        return BoundsF64.of(_min.min(other._min), _max.max(other._max));
    }

    public BoundsF64 add( VecF64 offset ) {
        return BoundsF64.of(_min.add(offset), _max.add(offset));
    }

    public BoundsF64 sub( VecF64 offset ) {
        return BoundsF64.of(_min.sub(offset), _max.sub(offset));
    }

    /**
     *  Splits this box into a regular {@code divisions x divisions x divisions} grid
     *  of equally sized sub-boxes.
     *  <p>
     *  The returned tuple is laid out in {@code x + y*divisions + z*divisions^2}
     *  order, which is exactly the linear ordering the world tree uses to store
     *  its {@code WorldSector} children. For the engine's {@code 8 x 8 x 8} nodes
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
                    VecF64 cellMin = _min.add(step.mul(VecF64.of(x, y, z)));
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
        VecF64 cellMin = _min.add(step.mul(VecF64.of(x, y, z)));
        return new BoundsF64(cellMin, cellMin.add(step));
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof BoundsF64 other) ) return false;
        return _min.equals(other._min) && _max.equals(other._max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_min, _max);
    }

    @Override
    public String toString() {
        return "BoundsF64[min=" + _min + ", max=" + _max + ']';
    }
}