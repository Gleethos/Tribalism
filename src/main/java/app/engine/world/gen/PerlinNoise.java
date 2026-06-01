package app.engine.world.gen;

import java.util.Random;

/**
 *  A deterministic 3D Perlin (gradient) noise source, plus fractal Brownian
 *  motion ({@link #fbm}) built on top of it.
 *  <p>
 *  This is the raw signal the {@link WorldGenerator} samples to decide what each
 *  region of the world is made of. It is seeded, so the same seed always yields
 *  the same landscape &mdash; which is exactly what makes generation reproducible
 *  and testable.
 */
public final class PerlinNoise
{
    private final int[] _permutation; // length 512, a doubled, shuffled 0..255 table

    public PerlinNoise( long seed ) {
        int[] p = new int[256];
        for ( int i = 0; i < 256; i++ )
            p[i] = i;
        // Fisher-Yates shuffle, seeded, so the gradient table is deterministic.
        Random random = new Random(seed);
        for ( int i = 255; i > 0; i-- ) {
            int j = random.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        _permutation = new int[512];
        for ( int i = 0; i < 512; i++ )
            _permutation[i] = p[i & 255];
    }

    /** @return Smooth gradient noise at {@code (x, y, z)}, roughly in {@code [-1, 1]}. */
    public double noise( double x, double y, double z ) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;

        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int[] p = _permutation;
        int aaa = p[p[p[xi    ] + yi    ] + zi    ];
        int aba = p[p[p[xi    ] + yi + 1] + zi    ];
        int aab = p[p[p[xi    ] + yi    ] + zi + 1];
        int abb = p[p[p[xi    ] + yi + 1] + zi + 1];
        int baa = p[p[p[xi + 1] + yi    ] + zi    ];
        int bba = p[p[p[xi + 1] + yi + 1] + zi    ];
        int bab = p[p[p[xi + 1] + yi    ] + zi + 1];
        int bbb = p[p[p[xi + 1] + yi + 1] + zi + 1];

        double x1 = lerp(grad(aaa, xf, yf, zf),         grad(baa, xf - 1, yf, zf),         u);
        double x2 = lerp(grad(aba, xf, yf - 1, zf),     grad(bba, xf - 1, yf - 1, zf),     u);
        double y1 = lerp(x1, x2, v);
        double x3 = lerp(grad(aab, xf, yf, zf - 1),     grad(bab, xf - 1, yf, zf - 1),     u);
        double x4 = lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u);
        double y2 = lerp(x3, x4, v);

        return lerp(y1, y2, w);
    }

    /**
     *  Fractal Brownian motion: sums several octaves of {@link #noise} at
     *  increasing frequency and decreasing amplitude, producing natural-looking
     *  detail at multiple scales.
     *
     *  @param octaves     The number of noise layers to sum (>= 1).
     *  @param persistence How quickly amplitude falls off per octave (e.g. {@code 0.5}).
     *  @param lacunarity  How quickly frequency grows per octave (e.g. {@code 2.0}).
     *  @return The summed noise, normalized to roughly {@code [-1, 1]}.
     */
    public double fbm( double x, double y, double z, int octaves, double persistence, double lacunarity ) {
        double total = 0;
        double frequency = 1;
        double amplitude = 1;
        double maxAmplitude = 0;
        for ( int i = 0; i < octaves; i++ ) {
            total += noise(x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return maxAmplitude == 0 ? 0 : total / maxAmplitude;
    }

    private static double fade( double t ) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp( double a, double b, double t ) {
        return a + t * (b - a);
    }

    private static double grad( int hash, double x, double y, double z ) {
        // Pick one of 12 gradient directions from the low 4 bits of the hash.
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}