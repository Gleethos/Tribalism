package app.engine.primitives;

import java.util.Arrays;

/**
 *  An immutable {@code 4 x 4} matrix of {@code double} components, stored in
 *  row-major order (so {@code get(row, col) == data[row * 4 + col]}).
 *  <p>
 *  These matrices live in the engine's 64-bit simulation space and are used to
 *  build the view and projection transforms of a {@link CameraF64}. Every
 *  operation returns a new matrix; instances never mutate, which keeps them
 *  safe to share across threads and to embed in records.
 */
public final class Mat4F64
{
    private static final Mat4F64 _IDENTITY = new Mat4F64(new double[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    });

    private final double[] _data;

    private Mat4F64( double[] data ) {
        if ( data.length != 16 )
            throw new IllegalArgumentException("A 4x4 matrix needs exactly 16 components, but got " + data.length + ".");
        _data = data;
    }

    /** @param data 16 components in row-major order; the array is copied defensively. */
    public static Mat4F64 of( double[] data ) {
        return new Mat4F64(data.clone());
    }

    public static Mat4F64 identity() { return _IDENTITY; }

    public static Mat4F64 translation( VecF64 t ) {
        return new Mat4F64(new double[]{
                1, 0, 0, t.x(),
                0, 1, 0, t.y(),
                0, 0, 1, t.z(),
                0, 0, 0, 1
        });
    }

    public static Mat4F64 scale( VecF64 s ) {
        return new Mat4F64(new double[]{
                s.x(), 0,     0,     0,
                0,     s.y(), 0,     0,
                0,     0,     s.z(), 0,
                0,     0,     0,     1
        });
    }

    public static Mat4F64 rotationX( double radians ) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Mat4F64(new double[]{
                1, 0,  0, 0,
                0, c, -s, 0,
                0, s,  c, 0,
                0, 0,  0, 1
        });
    }

    public static Mat4F64 rotationY( double radians ) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Mat4F64(new double[]{
                 c, 0, s, 0,
                 0, 1, 0, 0,
                -s, 0, c, 0,
                 0, 0, 0, 1
        });
    }

    public static Mat4F64 rotationZ( double radians ) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Mat4F64(new double[]{
                c, -s, 0, 0,
                s,  c, 0, 0,
                0,  0, 1, 0,
                0,  0, 0, 1
        });
    }

    /**
     *  A right-handed perspective projection matrix mapping the view frustum into
     *  the OpenGL clip cube with {@code z} in {@code [-1, 1]}.
     *
     *  @param fovYRadians The vertical field of view, in radians.
     *  @param aspect The viewport aspect ratio ({@code width / height}).
     *  @param near The distance to the near clipping plane (positive).
     *  @param far The distance to the far clipping plane (positive, > near).
     */
    public static Mat4F64 perspective( double fovYRadians, double aspect, double near, double far ) {
        double f = 1.0 / Math.tan(fovYRadians / 2.0);
        return new Mat4F64(new double[]{
                f / aspect, 0,  0,                            0,
                0,          f,  0,                            0,
                0,          0,  (far + near) / (near - far),  (2 * far * near) / (near - far),
                0,          0, -1,                            0
        });
    }

    /**
     *  A right-handed <b>reversed-Z</b> perspective projection with an <b>infinite</b> far plane,
     *  mapping depth into {@code [0, 1]} with {@code 1} at the near plane and {@code 0} at infinity.
     *  <p>
     *  This is the projection for a floating-point depth buffer: floats are densest near zero, which
     *  reversed-Z hands to the <i>far</i> field, yielding near-constant <i>relative</i> depth precision
     *  over arbitrarily large view ranges (no z-fighting at the horizon, no far-plane tradeoff) where
     *  the classic {@code [-1, 1]} mapping of {@link #perspective} degrades quadratically with distance.
     *  A renderer using it must flip its conventions accordingly: depth test {@code GREATER}, clear
     *  depth {@code 0}, and clip-space depth declared as {@code [0, 1]} (e.g.
     *  {@code glClipControl(..., GL_ZERO_TO_ONE)}). Culling keeps using the finite
     *  {@link #perspective} frustum; this matrix only shapes what the GPU rasterizes.
     *
     *  @param fovYRadians The vertical field of view, in radians.
     *  @param aspect The viewport aspect ratio ({@code width / height}).
     *  @param near The distance to the near clipping plane (positive).
     */
    public static Mat4F64 perspectiveReversedInfinite( double fovYRadians, double aspect, double near ) {
        double f = 1.0 / Math.tan(fovYRadians / 2.0);
        return new Mat4F64(new double[]{
                f / aspect, 0,  0,  0,
                0,          f,  0,  0,
                0,          0,  0,  near,
                0,          0, -1,  0
        });
    }

    /**
     *  A right-handed orthographic projection matrix mapping the given box into
     *  the OpenGL clip cube.
     */
    public static Mat4F64 orthographic( double left, double right, double bottom, double top, double near, double far ) {
        return new Mat4F64(new double[]{
                2 / (right - left), 0,                  0,                -(right + left) / (right - left),
                0,                  2 / (top - bottom), 0,                -(top + bottom) / (top - bottom),
                0,                  0,                 -2 / (far - near), -(far + near)   / (far - near),
                0,                  0,                  0,                 1
        });
    }

    /**
     *  A right-handed "look-at" view matrix placing the camera at {@code eye},
     *  oriented to look towards {@code center} with the given {@code up} hint.
     */
    public static Mat4F64 lookAt( VecF64 eye, VecF64 center, VecF64 up ) {
        VecF64 f = center.sub(eye).normalize();
        VecF64 s = f.cross(up).normalize();
        VecF64 u = s.cross(f);
        return new Mat4F64(new double[]{
                 s.x(),  s.y(),  s.z(), -s.dot(eye),
                 u.x(),  u.y(),  u.z(), -u.dot(eye),
                -f.x(), -f.y(), -f.z(),  f.dot(eye),
                 0,      0,      0,      1
        });
    }

    /** @return A copy of the 16 components in row-major order. */
    public double[] data() {
        return _data.clone();
    }

    public double get( int row, int col ) {
        return _data[row * 4 + col];
    }

    /** @return A new matrix equal to this one but with {@code (row, col)} set to {@code value}. */
    public Mat4F64 set( int row, int col, double value ) {
        double[] data = _data.clone();
        data[row * 4 + col] = value;
        return new Mat4F64(data);
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

    /**
     *  Transforms a point by this matrix, treating it as the homogeneous
     *  coordinate {@code (x, y, z, 1)} and performing the perspective divide
     *  by the resulting {@code w} component.
     */
    public VecF64 transformPoint( VecF64 p ) {
        double x = _data[0]  * p.x() + _data[1]  * p.y() + _data[2]  * p.z() + _data[3];
        double y = _data[4]  * p.x() + _data[5]  * p.y() + _data[6]  * p.z() + _data[7];
        double z = _data[8]  * p.x() + _data[9]  * p.y() + _data[10] * p.z() + _data[11];
        double w = _data[12] * p.x() + _data[13] * p.y() + _data[14] * p.z() + _data[15];
        if ( w != 0 && w != 1 )
            return VecF64.of(x / w, y / w, z / w);
        return VecF64.of(x, y, z);
    }

    /**
     *  Transforms a direction by this matrix, ignoring translation
     *  (i.e. treating it as the homogeneous coordinate {@code (x, y, z, 0)}).
     */
    public VecF64 transformDirection( VecF64 d ) {
        return VecF64.of(
                _data[0] * d.x() + _data[1] * d.y() + _data[2]  * d.z(),
                _data[4] * d.x() + _data[5] * d.y() + _data[6]  * d.z(),
                _data[8] * d.x() + _data[9] * d.y() + _data[10] * d.z()
        );
    }

    public double determinant() {
        double det = 0;
        for ( int col = 0; col < 4; col++ )
            det += get(0, col) * cofactor(0, col);
        return det;
    }

    /**
     *  @return The inverse of this matrix.
     *  @throws IllegalStateException if the matrix is singular (non-invertible).
     */
    public Mat4F64 inverse() {
        double det = determinant();
        if ( det == 0 )
            throw new IllegalStateException("This matrix is singular and therefore cannot be inverted.");
        double invDet = 1.0 / det;

        // The inverse is the transpose of the cofactor matrix (the adjugate),
        // scaled by 1/det. We write cofactor(row, col) into element (col, row).
        double[] inv = new double[16];
        for ( int row = 0; row < 4; row++ )
            for ( int col = 0; col < 4; col++ )
                inv[col * 4 + row] = cofactor(row, col) * invDet;
        return new Mat4F64(inv);
    }

    /** The signed determinant of the 3x3 minor left after deleting {@code row} and {@code col}. */
    private double cofactor( int row, int col ) {
        int[] r = otherIndices(row);
        int[] c = otherIndices(col);
        double det3 =
                get(r[0], c[0]) * (get(r[1], c[1]) * get(r[2], c[2]) - get(r[1], c[2]) * get(r[2], c[1]))
              - get(r[0], c[1]) * (get(r[1], c[0]) * get(r[2], c[2]) - get(r[1], c[2]) * get(r[2], c[0]))
              + get(r[0], c[2]) * (get(r[1], c[0]) * get(r[2], c[1]) - get(r[1], c[1]) * get(r[2], c[0]));
        return ((row + col) % 2 == 0 ? 1 : -1) * det3;
    }

    /** The three indices in {@code [0, 4)} other than {@code k}, in ascending order. */
    private static int[] otherIndices( int k ) {
        int[] result = new int[3];
        int i = 0;
        for ( int v = 0; v < 4; v++ )
            if ( v != k )
                result[i++] = v;
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof Mat4F64 other) ) return false;
        return Arrays.equals(_data, other._data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_data);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Mat4F64[");
        for ( int row = 0; row < 4; row++ ) {
            sb.append('[');
            for ( int col = 0; col < 4; col++ ) {
                sb.append(get(row, col));
                if ( col < 3 ) sb.append(", ");
            }
            sb.append(']');
            if ( row < 3 ) sb.append(", ");
        }
        return sb.append(']').toString();
    }
}