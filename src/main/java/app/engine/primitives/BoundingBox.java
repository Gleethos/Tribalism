package app.engine.primitives;

public class BoundingBox
{
    private final VecF64 _min;
    private final VecF64 _max;

    private BoundingBox( VecF64 min, VecF64 max ) {
        _min = min;
        _max = max;
    }

    public static BoundingBox of( VecF64 min, VecF64 max ) {
        // input validation
        if ( min.x() > max.x() || min.y() > max.y() || min.z() > max.z() ) {
            throw new IllegalArgumentException("min must be less than max");
        }

        return new BoundingBox(min, max);
    }

    public VecF64 min() { return _min; }

    public VecF64 max() { return _max; }

    public VecF64 center() { return _min.add(_max).div(2); }

    public VecF64 size() { return _max.sub(_min); }

    public boolean contains( VecF64 point ) {
        return point.x() >= _min.x() && point.x() <= _max.x()
            && point.y() >= _min.y() && point.y() <= _max.y()
            && point.z() >= _min.z() && point.z() <= _max.z();
    }

    public boolean intersects( BoundingBox other ) {
        return _min.x() <= other._max.x() && _max.x() >= other._min.x()
            && _min.y() <= other._max.y() && _max.y() >= other._min.y()
            && _min.z() <= other._max.z() && _max.z() >= other._min.z();
    }

    public BoundingBox intersectionWith( BoundingBox other ) {
        return BoundingBox.of(
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

    public BoundingBox add( VecF64 offset ) {
        return BoundingBox.of(_min.add(offset), _max.add(offset));
    }

    public BoundingBox sub( VecF64 offset ) {
        return BoundingBox.of(_min.sub(offset), _max.sub(offset));
    }

}
