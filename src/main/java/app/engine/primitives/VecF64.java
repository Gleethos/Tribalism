package app.engine.primitives;

/**
 *  An immutable 3-dimensional vector of {@code double} (64-bit) components.
 *  The world engine performs all of its simulation in this 64-bit space;
 *  the conversion down to 32-bit floats only happens later, in the rendering step.
 *  <p>
 *  This type has full value semantics (see {@link #equals(Object)} and
 *  {@link #hashCode()}) so that it can be safely embedded inside records and
 *  the persistent Sprouts collections (which rely on structural equality).
 */
public final class VecF64
{
    private static final VecF64 _ZERO = new VecF64(0, 0, 0);
    private static final VecF64 _ONE  = new VecF64(1, 1, 1);

    private final double _x;
    private final double _y;
    private final double _z;

    private VecF64( double x, double y, double z ) {
        _x = x;
        _y = y;
        _z = z;
    }

    public static VecF64 of( double x, double y, double z ) {
        return new VecF64(x, y, z);
    }

    /** @return The zero vector {@code (0, 0, 0)}. */
    public static VecF64 zero() { return _ZERO; }

    /** @return The unit vector {@code (1, 1, 1)}. */
    public static VecF64 one() { return _ONE; }

    /** @return A vector whose every component equals {@code scalar}. */
    public static VecF64 of( double scalar ) { return new VecF64(scalar, scalar, scalar); }

    public double x() { return _x; }

    public double y() { return _y; }

    public double z() { return _z; }
    
    public VecF64 add( VecF64 other ) {
        return VecF64.of(_x + other._x, _y + other._y, _z + other._z);
    }

    public VecF64 add( double scalar ) {
        return VecF64.of(_x + scalar, _y + scalar, _z + scalar);
    }

    public VecF64 add( double x, double y, double z ) {
        return VecF64.of(_x + x, _y + y, _z + z);
    }
    
    public VecF64 sub( VecF64 other ) {
        return VecF64.of(_x - other._x, _y - other._y, _z - other._z);
    }

    public VecF64 sub( double scalar ) {
        return VecF64.of(_x - scalar, _y - scalar, _z - scalar);
    }

    public VecF64 sub( double x, double y, double z ) {
        return VecF64.of(_x - x, _y - y, _z - z);
    }
    
    public VecF64 mul( double scalar ) {
        return VecF64.of(_x * scalar, _y * scalar, _z * scalar);
    }

    public VecF64 mul( VecF64 other ) {
        return VecF64.of(_x * other._x, _y * other._y, _z * other._z);
    }

    public VecF64 mul( double x, double y, double z ) {
        return VecF64.of(_x * x, _y * y, _z * z);
    }
    
    public VecF64 div( double scalar ) {
        return VecF64.of(_x / scalar, _y / scalar, _z / scalar);
    }

    public VecF64 div( VecF64 other ) {
        return VecF64.of(_x / other._x, _y / other._y, _z / other._z);
    }

    public VecF64 div( double x, double y, double z ) {
        return VecF64.of(_x / x, _y / y, _z / z);
    }
    
    public double dot( VecF64 other ) {
        return _x * other._x + _y * other._y + _z * other._z;
    }
    
    public VecF64 cross( VecF64 other ) {
        return VecF64.of(
                    _y * other._z - _z * other._y,
                    _z * other._x - _x * other._z,
                    _x * other._y - _y * other._x
                );
    }
    
    public double length() {
        return Math.sqrt(_x * _x + _y * _y + _z * _z);
    }

    /**
     *  The squared length of this vector. Cheaper than {@link #length()} because
     *  it avoids the square root, which makes it the preferred choice for
     *  length comparisons and distance thresholds.
     */
    public double lengthSquared() {
        return _x * _x + _y * _y + _z * _z;
    }

    /** @return The Euclidean distance between this vector and {@code other}. */
    public double distance( VecF64 other ) {
        return sub(other).length();
    }

    /** @return The squared Euclidean distance, avoiding the square root. */
    public double distanceSquared( VecF64 other ) {
        return sub(other).lengthSquared();
    }

    /**
     *  Linearly interpolates between this vector and {@code other}.
     *  @param other The target vector.
     *  @param t The interpolation factor, where {@code 0} yields {@code this}
     *           and {@code 1} yields {@code other}.
     */
    public VecF64 lerp( VecF64 other, double t ) {
        return VecF64.of(
                    _x + (other._x - _x) * t,
                    _y + (other._y - _y) * t,
                    _z + (other._z - _z) * t
                );
    }

    public VecF64 normalize() {
        double len = length();
        if ( len == 0 )
            return _ZERO;
        return VecF64.of(_x / len, _y / len, _z / len);
    }
    
    public VecF64 negate() {
        return VecF64.of(-_x, -_y, -_z);
    }

    public VecF64 abs() {
        return VecF64.of(Math.abs(_x), Math.abs(_y), Math.abs(_z));
    }

    public VecF64 min( VecF64 other ) {
        return VecF64.of(Math.min(_x, other._x), Math.min(_y, other._y), Math.min(_z, other._z));
    }

    public VecF64 max( VecF64 other ) {
        return VecF64.of(Math.max(_x, other._x), Math.max(_y, other._y), Math.max(_z, other._z));
    }

    public VecF64 clamp( VecF64 min, VecF64 max ) {
        return VecF64.of(
                    Math.min(Math.max(_x, min._x), max._x),
                    Math.min(Math.max(_y, min._y), max._y),
                    Math.min(Math.max(_z, min._z), max._z)
                );
    }

    public VecF64 floor() {
        return VecF64.of(Math.floor(_x), Math.floor(_y), Math.floor(_z));
    }

    public VecF64 ceil() {
        return VecF64.of(Math.ceil(_x), Math.ceil(_y), Math.ceil(_z));
    }

    public VecF64 round() {
        return VecF64.of(Math.round(_x), Math.round(_y), Math.round(_z));
    }

    public VecF64 fract() {
        return VecF64.of(_x - Math.floor(_x), _y - Math.floor(_y), _z - Math.floor(_z));
    }

    public VecF64 mod( double scalar ) {
        return VecF64.of(_x % scalar, _y % scalar, _z % scalar);
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof VecF64 other) ) return false;
        return Double.compare(_x, other._x) == 0
            && Double.compare(_y, other._y) == 0
            && Double.compare(_z, other._z) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(_x);
        result = 31 * result + Double.hashCode(_y);
        result = 31 * result + Double.hashCode(_z);
        return result;
    }

    @Override
    public String toString() {
        return "VecF64[x=" + _x + ", y=" + _y + ", z=" + _z + "]";
    }
}
