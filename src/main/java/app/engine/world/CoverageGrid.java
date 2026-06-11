package app.engine.world;

import java.util.Arrays;
import java.util.Comparator;

/**
 *  A coarse screen-space "already blocked" buffer for software occlusion culling.
 *  <p>
 *  The screen is divided into a grid of square tiles. As the renderer walks the
 *  world <b>near&nbsp;&rarr;&nbsp;far</b>, every fully-solid occluder marks the tiles
 *  its silhouette fully covers ({@link #markOccluder}); a later (farther) sector can
 *  then be skipped if its whole screen rectangle falls inside already-covered tiles
 *  ({@link #isOccluded}).
 *  <p>
 *  Both operations are deliberately <b>conservative</b> so culling never hides
 *  something visible:
 *  <ul>
 *      <li>marking is <i>inner</i> — only tiles entirely within an occluder's convex
 *          silhouette are flagged, so we never claim more coverage than truly exists;</li>
 *      <li>testing is <i>outer</i> — a sector is culled only if <b>every</b> tile its
 *          (over-estimated) screen rectangle touches is covered.</li>
 *  </ul>
 *  Because the walk is front-to-back, anything already marked is necessarily closer
 *  than whatever is being tested, so no depth values are needed.
 */
public final class CoverageGrid
{
    private final int _tile;
    private final int _cols;
    private final int _rows;
    private final int _width;
    private final int _height;
    private final boolean[] _covered;

    public CoverageGrid( int width, int height, int tile ) {
        _width  = width;
        _height = height;
        _tile   = Math.max(1, tile);
        _cols   = Math.max(1, (width  + _tile - 1) / _tile);
        _rows   = Math.max(1, (height + _tile - 1) / _tile);
        _covered = new boolean[_cols * _rows];
    }

    /**
     *  @return {@code true} if the given screen rectangle is entirely within
     *          already-covered tiles (its on-screen part is fully blocked), or lies
     *          entirely off-screen. Such a sector cannot be visible.
     */
    public boolean isOccluded( double minX, double minY, double maxX, double maxY ) {
        double clampedMinX = Math.max(0, minX);
        double clampedMinY = Math.max(0, minY);
        double clampedMaxX = Math.min(_width, maxX);
        double clampedMaxY = Math.min(_height, maxY);
        if ( clampedMinX >= clampedMaxX || clampedMinY >= clampedMaxY )
            return true; // entirely off-screen: not visible

        int txMin = (int) (clampedMinX / _tile);
        int tyMin = (int) (clampedMinY / _tile);
        int txMax = (int) ((clampedMaxX - 1e-6) / _tile);
        int tyMax = (int) ((clampedMaxY - 1e-6) / _tile);
        for ( int ty = tyMin; ty <= tyMax; ty++ )
            for ( int tx = txMin; tx <= txMax; tx++ )
                if ( !_covered[ty * _cols + tx] )
                    return false;
        return true;
    }

    /**
     *  Marks the tiles fully inside the convex silhouette (2D convex hull) of the
     *  given projected points &mdash; the screen footprint of a solid occluder.
     *
     *  @param points The occluder's projected corners as {@code [x, y]} pairs.
     */
    public void markOccluder( double[][] points ) {
        double[][] hull = convexHull(points);
        if ( hull.length < 3 )
            return;

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for ( double[] p : hull ) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        if ( maxX - minX < _tile || maxY - minY < _tile )
            return; // too small to contain even one whole tile: nothing could be marked. This is the
                    // common case for the THOUSANDS of small far-field occluders tested every frame,
                    // so bailing before any tile/point-in-polygon work matters.
        int txMin = Math.max(0, (int) (minX / _tile));
        int tyMin = Math.max(0, (int) (minY / _tile));
        int txMax = Math.min(_cols - 1, (int) (maxX / _tile));
        int tyMax = Math.min(_rows - 1, (int) (maxY / _tile));

        for ( int ty = tyMin; ty <= tyMax; ty++ ) {
            for ( int tx = txMin; tx <= txMax; tx++ ) {
                if ( _covered[ty * _cols + tx] )
                    continue;
                double left = tx * _tile, right = (tx + 1) * _tile;
                double top  = ty * _tile, bottom = (ty + 1) * _tile;
                if ( inside(hull, left, top) && inside(hull, right, top)
                  && inside(hull, right, bottom) && inside(hull, left, bottom) )
                    _covered[ty * _cols + tx] = true;
            }
        }
    }

    /** Point-in-convex-polygon: inside iff every edge cross-product shares one sign. */
    private static boolean inside( double[][] poly, double px, double py ) {
        int sign = 0;
        for ( int i = 0; i < poly.length; i++ ) {
            double[] a = poly[i];
            double[] b = poly[(i + 1) % poly.length];
            double cross = (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
            if ( cross != 0 ) {
                int s = cross > 0 ? 1 : -1;
                if ( sign == 0 )      sign = s;
                else if ( s != sign ) return false;
            }
        }
        return true;
    }

    /** Andrew's monotone chain convex hull (CCW). */
    private static double[][] convexHull( double[][] input ) {
        int n = input.length;
        if ( n < 3 )
            return input;
        double[][] pts = input.clone();
        Arrays.sort(pts, Comparator.<double[]>comparingDouble(p -> p[0]).thenComparingDouble(p -> p[1]));

        double[][] hull = new double[2 * n][];
        int k = 0;
        for ( int i = 0; i < n; i++ ) {
            while ( k >= 2 && cross(hull[k - 2], hull[k - 1], pts[i]) <= 0 ) k--;
            hull[k++] = pts[i];
        }
        for ( int i = n - 2, lower = k + 1; i >= 0; i-- ) {
            while ( k >= lower && cross(hull[k - 2], hull[k - 1], pts[i]) <= 0 ) k--;
            hull[k++] = pts[i];
        }
        return Arrays.copyOf(hull, Math.max(0, k - 1));
    }

    private static double cross( double[] o, double[] a, double[] b ) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }
}