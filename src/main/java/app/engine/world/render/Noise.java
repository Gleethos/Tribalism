package app.engine.world.render;

/**
 *  Engine-owned procedural noise: a small toolkit of deterministic 2D noise functions
 *  used by {@link TextureBaker} to give each appearance a procedural surface pattern.
 *  <p>
 *  It is built from three classic primitives &mdash; a fast integer hash, smooth
 *  value noise, fractal Brownian motion (fBm), and Worley (cellular) noise &mdash; on top
 *  of which sit named pattern functions ({@link #grainy}, {@link #marble}, {@link #wood},
 *  {@link #cells}, &hellip;). Every function takes a coordinate and returns a value in
 *  {@code [0, 1]}, is pure and deterministic, and seeds itself purely from its integer
 *  lattice, so the same coordinate always yields the same value. (Seamless tiling is the
 *  caller's concern; {@link TextureBaker} cross-fades these over a period.)
 *  <p>
 *  These are our own implementations &mdash; the well-known techniques, not a dependency.
 */
public final class Noise
{
    private Noise() {}

    // ---- primitives -------------------------------------------------------------

    /** @return A deterministic pseudo-random value in {@code [0, 1)} for an integer lattice point. */
    private static double hash( int x, int y ) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    /** Smoothly interpolated value noise on the integer lattice, in {@code [0, 1]}. */
    public static double valueNoise( double x, double y ) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = smoothStep(x - x0), fy = smoothStep(y - y0);
        double v00 = hash(x0, y0),     v10 = hash(x0 + 1, y0);
        double v01 = hash(x0, y0 + 1), v11 = hash(x0 + 1, y0 + 1);
        double top = v00 + (v10 - v00) * fx;
        double bottom = v01 + (v11 - v01) * fx;
        return top + (bottom - top) * fy;
    }

    /** Fractal Brownian motion: {@code octaves} of {@link #valueNoise} with halving amplitude, in {@code [0, 1]}. */
    public static double fractal( double x, double y, int octaves ) {
        double sum = 0, amplitude = 1, frequency = 1, total = 0;
        for ( int i = 0; i < octaves; i++ ) {
            sum += valueNoise(x * frequency, y * frequency) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return sum / total;
    }

    /** @return The distances to the closest and second-closest Worley feature points, {@code [F1, F2]}. */
    private static double[] worley( double x, double y ) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        double f1 = Double.POSITIVE_INFINITY, f2 = Double.POSITIVE_INFINITY;
        for ( int oy = -1; oy <= 1; oy++ )
            for ( int ox = -1; ox <= 1; ox++ ) {
                int gx = cx + ox, gy = cy + oy;
                double px = gx + hash(gx, gy);
                double py = gy + hash(gy, gx ^ 0x5bd1e995);
                double dx = px - x, dy = py - y;
                double d = Math.sqrt(dx * dx + dy * dy);
                if ( d < f1 ) { f2 = f1; f1 = d; }
                else if ( d < f2 ) { f2 = d; }
            }
        return new double[]{ f1, f2 };
    }

    /** @return A random value in {@code [0, 1)} keyed to the nearest Worley cell (flat, faceted regions). */
    private static double nearestCellValue( double x, double y ) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        double best = Double.POSITIVE_INFINITY;
        int bestX = cx, bestY = cy;
        for ( int oy = -1; oy <= 1; oy++ )
            for ( int ox = -1; ox <= 1; ox++ ) {
                int gx = cx + ox, gy = cy + oy;
                double px = gx + hash(gx, gy);
                double py = gy + hash(gy, gx ^ 0x5bd1e995);
                double dx = px - x, dy = py - y;
                double d = dx * dx + dy * dy;
                if ( d < best ) { best = d; bestX = gx; bestY = gy; }
            }
        return hash(bestX * 7 + 1, bestY * 7 + 3);
    }

    // ---- named patterns (all in [0, 1]) -----------------------------------------

    /** A busy, high-frequency speckle &mdash; the default rough/stone surface. */
    public static double stochastic( double x, double y ) {
        return clamp01((Math.sin(fractal(x, y, 3) * 12.0) + 1) / 2);
    }

    /** Fine sandy/soil granularity. */
    public static double grainy( double x, double y ) {
        return Math.abs(2 * valueNoise(x * 2, y * 2) - 1);
    }

    /** Smooth, low-frequency undulation &mdash; liquids, metal sheen. */
    public static double smoothWaves( double x, double y ) {
        return fractal(x * 0.5, y * 0.5, 4);
    }

    /** Soft rounded blobs &mdash; powder, snow. */
    public static double spots( double x, double y ) {
        return sigmoid((fractal(x, y, 4) - 0.5) * 8);
    }

    /** Sharp polygonal facets along cell edges &mdash; crystal. */
    public static double faceted( double x, double y ) {
        double[] f = worley(x, y);
        return clamp01((f[1] - f[0]) * 2.0);
    }

    /** A hazy damp mottling. */
    public static double haze( double x, double y ) {
        return fractal(x, y, 5);
    }

    /** Turbulent veins &mdash; marble, lava, mineral veins. */
    public static double marble( double x, double y ) {
        double turbulence = fractal(x, y, 4);
        return Math.sin((x + y + turbulence * 6) * Math.PI) * 0.5 + 0.5;
    }

    /** Distorted growth bands &mdash; wood grain, bark. */
    public static double wood( double x, double y ) {
        double rings = x * 0.7 + fractal(x, y, 3) * 2.0;
        return frac(rings);
    }

    /** Cellular bulges &mdash; moss, pores, hair clumps. */
    public static double cells( double x, double y ) {
        return clamp01(worley(x, y)[0]);
    }

    /** Pointed cellular spikes. */
    public static double tissue( double x, double y ) {
        return clamp01(1 - worley(x, y)[0]);
    }

    /** Flat shattered plates, each a random shade. */
    public static double mosaic( double x, double y ) {
        return nearestCellValue(x, y);
    }

    /** Quantized horizontal strata &mdash; sediment, layered rock. */
    public static double layered( double x, double y ) {
        return Math.floor(fractal(x, y, 4) * 5) / 4.0;
    }

    /** Soft billowing density &mdash; clouds, glow. */
    public static double clouds( double x, double y ) {
        return sigmoid((fractal(x, y, 6) - 0.5) * 7);
    }

    /** Leafy, blotchy organic variation. */
    public static double foliage( double x, double y ) {
        return fractal(x * 1.3, y * 1.3, 4);
    }

    // ---- helpers ----------------------------------------------------------------

    private static double smoothStep( double t ) {
        return t * t * (3 - 2 * t);
    }

    private static double sigmoid( double t ) {
        return 1 / (1 + Math.exp(-t));
    }

    private static double frac( double v ) {
        return v - Math.floor(v);
    }

    private static double clamp01( double v ) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}