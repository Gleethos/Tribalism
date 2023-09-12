package app.engine;

public final class VecF64
{
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
    
    public VecF64 normalize() {
        double len = length();
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
}
