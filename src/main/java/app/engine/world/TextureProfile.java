package app.engine.world;

import java.util.ArrayList;
import java.util.Arrays;
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
 *  <b>Representation.</b> Internally this is a plain {@code double[]} indexed by
 *  {@link Texture#ordinal()} &mdash; not a map. The key space is a small fixed enum, so a
 *  flat array makes the hot operations ({@link #intensityOf}, {@link #average}) tight
 *  loops with no hashing, boxing or bucket lookups. It is a {@code class} rather than a
 *  {@code record} purely so it can <i>encapsulate</i> that mutable array (defensively
 *  owned, never handed out) while still behaving as an immutable <b>value</b>:
 *  {@code equals}/{@code hashCode} compare the intensities element-wise, every mutator
 *  returns a new profile, and all intensities are kept clamped to {@code [0, 1]}.
 */
public final class TextureProfile
{
    private static final int COUNT = Texture.values().length;
    private static final TextureProfile _NONE = new TextureProfile(new double[COUNT]);

    /** A surface is treated as a solid, drawable voxel only when its {@link Texture#OPACITY} is at least this. */
    public static final double OPACITY_THRESHOLD = 0.25;

    /** Per-quality intensities in {@code [0, 1]}, indexed by {@link Texture#ordinal()}. Never exposed. */
    private final double[] _intensities;

    /** Takes ownership of {@code intensities}: callers must not retain or mutate it afterwards. */
    private TextureProfile( double[] intensities ) {
        _intensities = intensities;
    }

    /** The empty profile &mdash; every quality {@code 0}, i.e. invisible empty space. */
    public static TextureProfile none() { return _NONE; }

    /** @return A profile with a single {@code quality} set to {@code intensity} (clamped to {@code [0, 1]}). */
    public static TextureProfile of( Texture quality, double intensity ) {
        return _NONE.with(quality, intensity);
    }

    /** @return The intensity of {@code quality} in {@code [0, 1]}, or {@code 0} if absent. */
    public double intensityOf( Texture quality ) {
        return _intensities[quality.ordinal()];
    }

    /** @return A copy with {@code quality} set to {@code intensity} (clamped to {@code [0, 1]}). */
    public TextureProfile with( Texture quality, double intensity ) {
        double[] copy = _intensities.clone();
        copy[quality.ordinal()] = clamp(intensity);
        return new TextureProfile(copy);
    }

    /**
     *  @return {@code true} if this surface is opaque enough to be drawn as a solid
     *          voxel &mdash; its {@link Texture#OPACITY} is at least {@link #OPACITY_THRESHOLD}.
     */
    public boolean isOpaque() {
        return intensityOf(Texture.OPACITY) >= OPACITY_THRESHOLD;
    }

    /** @return {@code true} if every quality is {@code 0} (empty space / air). */
    public boolean isInvisible() {
        for ( double v : _intensities )
            if ( v > 0 )
                return false;
        return true;
    }

    /**
     *  Blends this profile towards {@code other} by {@code weight}, per quality.
     *  @param weight {@code 0} yields {@code this}, {@code 1} yields {@code other}.
     */
    public TextureProfile blend( TextureProfile other, double weight ) {
        double[] result = new double[COUNT];
        for ( int i = 0; i < COUNT; i++ ) {
            double a = _intensities[i], b = other._intensities[i];
            result[i] = a + (b - a) * weight;
        }
        return new TextureProfile(result);
    }

    /**
     *  Computes the per-quality average of many profiles. This is the core of
     *  level-of-detail aggregation: a parent sector's per-side appearance is the
     *  average of the matching-side profiles of its boundary children. It is a flat
     *  sum-then-divide over the backing arrays &mdash; deliberately allocation- and
     *  hash-free on this hot path.
     *
     *  @param samples The profiles to aggregate.
     *  @return The averaged profile, or {@link #none()} if there are no samples.
     */
    public static TextureProfile average( Iterable<TextureProfile> samples ) {
        double[] sums = new double[COUNT];
        int count = 0;
        for ( TextureProfile sample : samples ) {
            count++;
            double[] s = sample._intensities;
            for ( int i = 0; i < COUNT; i++ )
                sums[i] += s[i];
        }
        if ( count == 0 )
            return none();
        for ( int i = 0; i < COUNT; i++ )
            sums[i] /= count;
        return new TextureProfile(sums);
    }

    /** @return The qualities present in this profile with a non-zero intensity. */
    public List<Texture> presentQualities() {
        List<Texture> present = new ArrayList<>();
        Texture[] all = Texture.values();
        for ( int i = 0; i < COUNT; i++ )
            if ( _intensities[i] > 0 )
                present.add(all[i]);
        return present;
    }

    private static double clamp( double v ) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof TextureProfile other) ) return false;
        return Arrays.equals(_intensities, other._intensities);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_intensities);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TextureProfile[");
        Texture[] all = Texture.values();
        boolean first = true;
        for ( int i = 0; i < COUNT; i++ )
            if ( _intensities[i] > 0 ) {
                if ( !first ) sb.append(", ");
                sb.append(all[i]).append('=').append(_intensities[i]);
                first = false;
            }
        return sb.append(']').toString();
    }
}