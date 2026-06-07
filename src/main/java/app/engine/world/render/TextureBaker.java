package app.engine.world.render;

import app.engine.world.Texture;
import app.engine.world.TextureProfile;

import java.awt.Color;

/**
 *  Bakes a {@link TextureProfile} into a small, seamless, tileable RGBA tile that depicts
 *  its appearance procedurally &mdash; the "noise shader" the flat {@link TexturePalette}
 *  was only a stand-in for. The GPU backend uploads each baked tile into a texture array
 *  layer (one per appearance) and tiles it across surfaces with {@code GL_REPEAT}.
 *  <p>
 *  The tile's <b>base colour</b> is the profile's {@link TexturePalette#colorOf flat tint};
 *  its <b>pattern</b> is an intensity-weighted blend of the engine's own {@link Noise}
 *  functions picked by the profile's textural {@link Texture} qualities (grainy &rarr; speckle,
 *  liquid &rarr; smooth waves, fibrous &rarr; wood grain, crystalline &rarr; faceted cells,
 *  &hellip;), which modulates the base colour's brightness. Pure and deterministic: the same
 *  profile always bakes the same tile, so it is safe to memoize.
 *  <p>
 *  <b>Seamless tiling.</b> {@link Noise} samples an infinite lattice and does not tile on
 *  its own, so each sample is a 4-corner cross-fade over the tile {@link #PERIOD}
 *  ({@code v(x,y)}, {@code v(x-P,y)}, {@code v(x,y-P)}, {@code v(x-P,y-P)} blended by
 *  position): by construction the value at one edge equals the value at the opposite edge,
 *  so the tile repeats without a visible seam.
 */
public final class TextureBaker
{
    /** The tile's extent in noise-input units &mdash; the period over which the pattern repeats seamlessly. */
    private static final double PERIOD = 8.0;

    private TextureBaker() {}

    /**
     *  Bakes a seamless {@code size}&times;{@code size} ARGB tile depicting {@code profile}.
     *
     *  @param profile The appearance to depict.
     *  @param size    The tile edge in pixels.
     *  @return Row-major ARGB pixels ({@code 0xFFrrggbb}), fully opaque.
     */
    public static int[] bake( TextureProfile profile, int size ) {
        Color base = TexturePalette.colorOf(profile);
        int baseR = base.getRed(), baseG = base.getGreen(), baseB = base.getBlue();
        int[] pixels = new int[size * size];
        for ( int py = 0; py < size; py++ ) {
            for ( int px = 0; px < size; px++ ) {
                double nx = px / (double) size * PERIOD;
                double ny = py / (double) size * PERIOD;
                double n = tileable(profile, nx, ny);     // pattern value in ~[0, 1]
                double f = 0.70 + 0.60 * n;               // brightness modulation around the base colour
                int r = clamp((int) Math.round(baseR * f));
                int g = clamp((int) Math.round(baseG * f));
                int b = clamp((int) Math.round(baseB * f));
                pixels[px + py * size] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return pixels;
    }

    /** A seamless version of the profile's pattern: a 4-corner cross-fade over {@link #PERIOD}. */
    private static double tileable( TextureProfile profile, double x, double y ) {
        double v00 = noiseFor(profile, x,          y);
        double v10 = noiseFor(profile, x - PERIOD, y);
        double v01 = noiseFor(profile, x,          y - PERIOD);
        double v11 = noiseFor(profile, x - PERIOD, y - PERIOD);
        double fx = x / PERIOD, fy = y / PERIOD;
        double top = v00 * (1 - fx) + v10 * fx;
        double bottom = v01 * (1 - fx) + v11 * fx;
        return top * (1 - fy) + bottom * fy;
    }

    /** The intensity-weighted blend of the noise patterns the profile's qualities call for, in {@code [0, 1]}. */
    private static double noiseFor( TextureProfile profile, double x, double y ) {
        double sum = 0, weight = 0;
        // Each textural quality contributes its matching pattern, weighted by its intensity.
        sum += add(profile, Texture.GRAINY,      Noise.grainy(x, y));      weight += w(profile, Texture.GRAINY);
        sum += add(profile, Texture.ROUGH,       Noise.stochastic(x, y));  weight += w(profile, Texture.ROUGH);
        sum += add(profile, Texture.POWDERY,     Noise.spots(x, y));       weight += w(profile, Texture.POWDERY);
        sum += add(profile, Texture.CRYSTALLINE, Noise.faceted(x, y));     weight += w(profile, Texture.CRYSTALLINE);
        sum += add(profile, Texture.LIQUID,      Noise.smoothWaves(x, y)); weight += w(profile, Texture.LIQUID);
        sum += add(profile, Texture.WET,         Noise.haze(x, y));        weight += w(profile, Texture.WET);
        sum += add(profile, Texture.MOLTEN,      Noise.marble(x, y));      weight += w(profile, Texture.MOLTEN);
        sum += add(profile, Texture.FIBROUS,     Noise.wood(x, y));        weight += w(profile, Texture.FIBROUS);
        sum += add(profile, Texture.HAIRY,       Noise.cells(x, y));       weight += w(profile, Texture.HAIRY);
        sum += add(profile, Texture.MOSSY,       Noise.cells(x, y));       weight += w(profile, Texture.MOSSY);
        sum += add(profile, Texture.LEAFY,       Noise.foliage(x, y));     weight += w(profile, Texture.LEAFY);
        sum += add(profile, Texture.SPIKY,       Noise.tissue(x, y));      weight += w(profile, Texture.SPIKY);
        sum += add(profile, Texture.SHATTERED,   Noise.mosaic(x, y));      weight += w(profile, Texture.SHATTERED);
        sum += add(profile, Texture.POROUS,      Noise.cells(x, y));       weight += w(profile, Texture.POROUS);
        sum += add(profile, Texture.LAYERED,     Noise.layered(x, y));     weight += w(profile, Texture.LAYERED);
        sum += add(profile, Texture.VEINED,      Noise.marble(x, y));      weight += w(profile, Texture.VEINED);
        sum += add(profile, Texture.METALLIC,    Noise.smoothWaves(x, y)); weight += w(profile, Texture.METALLIC);
        sum += add(profile, Texture.REFLECTIVE,  Noise.smoothWaves(x, y)); weight += w(profile, Texture.REFLECTIVE);
        sum += add(profile, Texture.EMISSIVE,    Noise.clouds(x, y));      weight += w(profile, Texture.EMISSIVE);
        if ( weight <= 0 )
            return Noise.stochastic(x, y); // featureless surface: a subtle default speckle
        return clamp01(sum / weight);
    }

    private static double w( TextureProfile profile, Texture quality ) {
        return profile.intensityOf(quality);
    }

    private static double add( TextureProfile profile, Texture quality, double value ) {
        return profile.intensityOf(quality) * value;
    }

    private static double clamp01( double v ) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int clamp( int v ) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}