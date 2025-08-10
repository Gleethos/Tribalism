package app.engine.primitives;

public final class Mat4F64
{
    public static Mat4F64 of( double[] data ) {
        return new Mat4F64(data);
    }

    private final double[] _data;

    private Mat4F64( double[] data ) {
        _data = data;
    }

    public double[] data() {
        return _data;
    }

    public double get( int row, int col ) {
        return _data[row * 4 + col];
    }

    public Mat4F64 set( int row, int col, double value ) {
        _data[row * 4 + col] = value;
        return this;
    }

    public Mat4F64 mul( Mat4F64 other ) {
        double[] data = new double[16];
        for ( int row = 0; row < 4; row++ ) {
            for ( int col = 0; col < 4; col++ ) {
                double sum = 0;
                for ( int i = 0; i < 4; i++ ) {
                    sum += get(row, i) * other.get(i, col);
                }
                data[row * 4 + col] = sum;
            }
        }
        return new Mat4F64(data);
    }

    public Mat4F64 mul( double scalar ) {
        double[] data = new double[16];
        for ( int i = 0; i < 16; i++ ) {
            data[i] = _data[i] * scalar;
        }
        return new Mat4F64(data);
    }

    public Mat4F64 add( Mat4F64 other ) {
        double[] data = new double[16];
        for ( int i = 0; i < 16; i++ ) {
            data[i] = _data[i] + other._data[i];
        }
        return new Mat4F64(data);
    }

    public Mat4F64 sub( Mat4F64 other ) {
        double[] data = new double[16];
        for ( int i = 0; i < 16; i++ ) {
            data[i] = _data[i] - other._data[i];
        }
        return new Mat4F64(data);
    }

    public Mat4F64 transpose() {
        double[] data = new double[16];
        for ( int row = 0; row < 4; row++ ) {
            for ( int col = 0; col < 4; col++ ) {
                data[row * 4 + col] = get(col, row);
            }
        }
        return new Mat4F64(data);
    }

    public Mat4F64 inverse() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
