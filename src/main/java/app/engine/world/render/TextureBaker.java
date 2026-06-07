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

    /**
     *  The intensity-weighted blend of the noise patterns the profile's qualities call for, in {@code [0, 1]}.
     *  Qualities the profile does not have ({@code intensity == 0}) are skipped without evaluating their
     *  (relatively expensive) noise function &mdash; a zero weight contributed nothing to the blend anyway &mdash;
     *  which keeps a typical few-quality material cheap to bake even at a high tile resolution.
     */
    private static double noiseFor( TextureProfile profile, double x, double y ) {
        Blend blend = new Blend();
        // Each textural quality contributes its matching pattern, weighted by its intensity.
        blend.add(profile, Texture.GRAINY,      x, y, Noise::grainy);
        blend.add(profile, Texture.ROUGH,       x, y, Noise::stochastic);
        blend.add(profile, Texture.POWDERY,     x, y, Noise::spots);
        blend.add(profile, Texture.CRYSTALLINE, x, y, Noise::faceted);
        blend.add(profile, Texture.LIQUID,      x, y, Noise::smoothWaves);
        blend.add(profile, Texture.WET,         x, y, Noise::haze);
        blend.add(profile, Texture.MOLTEN,      x, y, Noise::marble);
        blend.add(profile, Texture.FIBROUS,     x, y, Noise::wood);
        blend.add(profile, Texture.HAIRY,       x, y, Noise::cells);
        blend.add(profile, Texture.MOSSY,       x, y, Noise::cells);
        blend.add(profile, Texture.LEAFY,       x, y, Noise::foliage);
        blend.add(profile, Texture.SPIKY,       x, y, Noise::tissue);
        blend.add(profile, Texture.SHATTERED,   x, y, Noise::mosaic);
        blend.add(profile, Texture.POROUS,      x, y, Noise::cells);
        blend.add(profile, Texture.LAYERED,     x, y, Noise::layered);
        blend.add(profile, Texture.VEINED,      x, y, Noise::marble);
        blend.add(profile, Texture.METALLIC,    x, y, Noise::smoothWaves);
        blend.add(profile, Texture.REFLECTIVE,  x, y, Noise::smoothWaves);
        blend.add(profile, Texture.EMISSIVE,    x, y, Noise::clouds);
        if ( blend.weight <= 0 )
            return Noise.stochastic(x, y); // featureless surface: a subtle default speckle
        return clamp01(blend.sum / blend.weight);
    }

    /** A running intensity-weighted sum that evaluates a quality's pattern only when the profile has it. */
    private static final class Blend
    {
        double sum;
        double weight;

        void add( TextureProfile profile, Texture quality, double x, double y, Pattern pattern ) {
            double intensity = profile.intensityOf(quality);
            if ( intensity <= 0 )
                return; // absent quality: skip the noise evaluation, it would add 0 to both sum and weight
            sum += intensity * pattern.at(x, y);
            weight += intensity;
        }
    }

    /** A named {@link Noise} pattern, picked per textural quality. */
    @FunctionalInterface
    private interface Pattern { double at( double x, double y ); }

    private static double clamp01( double v ) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int clamp( int v ) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}