package app.engine.primitives;

/**
 *  The six clipping planes of a camera's view volume, ready for cheap
 *  visibility tests against axis-aligned boxes.
 *  <p>
 *  The planes are extracted directly from a world-to-clip
 *  {@link Mat4F64 view-projection matrix} using the Gribb&ndash;Hartmann method:
 *  each row combination of the matrix yields one plane of the OpenGL clip cube
 *  ({@code -w <= x,y,z <= w}). Every plane is normalized and oriented so that its
 *  normal points <i>inward</i>; a point is inside the frustum exactly when it
 *  lies on the positive side of all six planes.
 *  <p>
 *  This is the data behind frustum culling: {@link #intersects(BoundsF64)} lets a
 *  tree walk prune any sector whose bounds fall entirely outside the view, so the
 *  renderer never descends into geometry it could not possibly draw.
 */
public final class Frustum
{
    private static final int LEFT   = 0;
    private static final int RIGHT  = 1;
    private static final int BOTTOM = 2;
    private static final int TOP    = 3;
    private static final int NEAR   = 4;
    private static final int FAR    = 5;

    // Each plane as (a, b, c, d): a*x + b*y + c*z + d >= 0 means "inside".
    private final double[][] _planes;

    private Frustum( double[][] planes ) {
        _planes = planes;
    }

    /**
     *  Extracts the six frustum planes from a world-to-clip transform
     *  (typically {@link CameraF64#viewProjectionMatrix()}).
     */
    public static Frustum of( Mat4F64 viewProjection ) {
        Mat4F64 m = viewProjection;
        // Row r of m: clip.{x,y,z,w} = row_r . (x, y, z, 1). The clip-cube
        // inequalities (e.g. x >= -w  ->  w + x >= 0) become row3 ± row_k.
        double[][] planes = new double[6][];
        planes[LEFT]   = row(m, 3, +1, 0); // w + x
        planes[RIGHT]  = row(m, 3, -1, 0); // w - x
        planes[BOTTOM] = row(m, 3, +1, 1); // w + y
        planes[TOP]    = row(m, 3, -1, 1); // w - y
        planes[NEAR]   = row(m, 3, +1, 2); // w + z
        planes[FAR]    = row(m, 3, -1, 2); // w - z
        for ( double[] plane : planes )
            normalize(plane);
        return new Frustum(planes);
    }

    /** @return The plane {@code row[wRow] + sign * row[axisRow]} as {@code (a, b, c, d)}. */
    private static double[] row( Mat4F64 m, int wRow, int sign, int axisRow ) {
        return new double[]{
                m.get(wRow, 0) + sign * m.get(axisRow, 0),
                m.get(wRow, 1) + sign * m.get(axisRow, 1),
                m.get(wRow, 2) + sign * m.get(axisRow, 2),
                m.get(wRow, 3) + sign * m.get(axisRow, 3)
        };
    }

    /** Scales a plane so its normal {@code (a, b, c)} is unit length (a no-op if degenerate). */
    private static void normalize( double[] plane ) {
        double length = Math.sqrt(plane[0] * plane[0] + plane[1] * plane[1] + plane[2] * plane[2]);
        if ( length > 0 ) {
            plane[0] /= length;
            plane[1] /= length;
            plane[2] /= length;
            plane[3] /= length;
        }
    }

    /**
     *  Conservative box-vs-frustum test: returns {@code false} only when {@code bounds}
     *  lies entirely outside the view volume, and {@code true} when it is inside or
     *  straddles a plane.
     *  <p>
     *  For each plane we test the box's <i>positive vertex</i> &mdash; the corner
     *  furthest along the plane normal. If even that corner is behind the plane, the
     *  whole box is, so the box is culled. This never produces a false negative (a
     *  visible box is never rejected); it may keep a box that grazes a frustum edge,
     *  which is the correct, safe trade-off for culling.
     *
     *  @param bounds The axis-aligned box to test.
     *  @return {@code true} if the box might be visible, {@code false} if it is certainly outside.
     */
    public boolean intersects( BoundsF64 bounds ) {
        VecF64 min = bounds.min();
        VecF64 max = bounds.max();
        for ( double[] plane : _planes ) {
            double px = plane[0] >= 0 ? max.x() : min.x();
            double py = plane[1] >= 0 ? max.y() : min.y();
            double pz = plane[2] >= 0 ? max.z() : min.z();
            if ( plane[0] * px + plane[1] * py + plane[2] * pz + plane[3] < 0 )
                return false; // positive vertex is behind this plane: box is fully outside.
        }
        return true;
    }

    /** @return {@code true} if {@code point} lies inside (or on) all six frustum planes. */
    public boolean contains( VecF64 point ) {
        for ( double[] plane : _planes )
            if ( plane[0] * point.x() + plane[1] * point.y() + plane[2] * point.z() + plane[3] < 0 )
                return false;
        return true;
    }
}