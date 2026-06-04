package app.engine.world;

import sprouts.Association;
import sprouts.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 *  The visual appearance of one surface: a set of {@link Texture} qualities, each
 *  with an independent intensity in {@code [0, 1]}.
 *  <p>
 *  This is the building block of a sector's {@link WorldSectorEtherData ether}: a
 *  sector stores one profile per cube {@link Side face}. Unlike the old
 *  material-fraction model the intensities are <b>not</b> a distribution &mdash;
 *  they need not sum to anything, so a surface can be both fully {@link Texture#GRAINY}
 *  and fully {@link Texture#LIQUID} at once. A profile with every quality at
 *  {@code 0} is {@link #none()}, the null object: empty, invisible space (air).
 *  <p>
 *  Qualities absent from the association read back as {@code 0}. The record carries
 *  full value semantics by delegating to the association.
 *
 *  @param intensities A mapping from appearance quality to its intensity in {@code [0, 1]}.
 */
public record TextureProfile(
    Association<Texture, Double> intensities
) {
    private static final TextureProfile _NONE =
            new TextureProfile(Association.between(Texture.class, Double.class));

    /** The empty profile &mdash; every quality {@code 0}, i.e. invisible empty space. */
    public static TextureProfile none() { return _NONE; }

    /** @return A profile with a single {@code quality} set to {@code intensity}. */
    public static TextureProfile of( Texture quality, double intensity ) {
        return _NONE.with(quality, intensity);
    }

    public TextureProfile {
        for ( Pair<Texture, Double> entry : intensities ) {
            double v = entry.second();
            if ( v < 0 || v > 1 )
                throw new IllegalArgumentException(
                        "The intensity of " + entry.first() + " must be in [0, 1], but was " + v + "."
                    );
        }
    }

    /** @return The intensity of {@code quality} in {@code [0, 1]}, or {@code 0} if absent. */
    public double intensityOf( Texture quality ) {
        return intensities.get(quality).orElse(0.0);
    }

    /**
     *  @return A copy with {@code quality} set to {@code intensity} (clamped to
     *          {@code [0, 1]}). Setting it to {@code 0} keeps it as a stored zero.
     */
    public TextureProfile with( Texture quality, double intensity ) {
        double clamped = intensity < 0 ? 0 : (intensity > 1 ? 1 : intensity);
        return new TextureProfile(intensities.put(quality, clamped));
    }

    /** @return {@code true} if every quality is {@code 0} (empty space / air). */
    public boolean isInvisible() {
        for ( Pair<Texture, Double> entry : intensities )
            if ( entry.second() > 0 )
                return false;
        return true;
    }

    /**
     *  Blends this profile towards {@code other} by {@code weight}, per quality.
     *  @param weight {@code 0} yields {@code this}, {@code 1} yields {@code other}.
     */
    public TextureProfile blend( TextureProfile other, double weight ) {
        Association<Texture, Double> result = Association.between(Texture.class, Double.class);
        for ( Texture quality : Texture.values() ) {
            double a = intensityOf(quality);
            double b = other.intensityOf(quality);
            double mixed = a + (b - a) * weight;
            if ( mixed != 0 )
                result = result.put(quality, mixed);
        }
        return new TextureProfile(result);
    }

    /**
     *  Computes the per-quality average of many profiles. This is the core of
     *  level-of-detail aggregation: a parent sector's per-side appearance is the
     *  average of the matching-side profiles of its boundary children.
     *
     *  @param samples The profiles to aggregate.
     *  @return The averaged profile, or {@link #none()} if there are no samples.
     */
    public static TextureProfile average( Iterable<TextureProfile> samples ) {
        Association<Texture, Double> sums = Association.between(Texture.class, Double.class);
        int count = 0;
        for ( TextureProfile sample : samples ) {
            count++;
            for ( Texture quality : Texture.values() ) {
                double v = sample.intensityOf(quality);
                if ( v != 0 )
                    sums = sums.put(quality, sums.get(quality).orElse(0.0) + v);
            }
        }
        if ( count == 0 )
            return none();
        Association<Texture, Double> averaged = Association.between(Texture.class, Double.class);
        for ( Pair<Texture, Double> entry : sums )
            averaged = averaged.put(entry.first(), entry.second() / count);
        return new TextureProfile(averaged);
    }

    /** @return The qualities present in this profile with a non-zero intensity. */
    public List<Texture> presentQualities() {
        List<Texture> present = new ArrayList<>();
        for ( Pair<Texture, Double> entry : intensities )
            if ( entry.second() > 0 )
                present.add(entry.first());
        return present;
    }
}