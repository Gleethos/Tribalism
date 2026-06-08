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
 *  A profile also carries an {@link #inset() inset} in {@code [0, 1]}: how far the
 *  sector's content is recessed behind <i>this</i> face, as a fraction of the sector's
 *  extent along that axis. It rides on the per-side profile rather than in a separate
 *  type, so a coarse level-of-detail box can {@link WorldSectorEtherData#shrink shrink}
 *  to fit its matter (a box straddling the surface stops at the terrain instead of
 *  sticking up as a full cube). The inset is geometry, not appearance, so it is ignored
 *  by colour and by greedy-mesh face merging (see {@link #sameAppearance}); a material's
 *  intrinsic texture simply has inset {@code 0}.
 *  <p>
 *  <b>Representation.</b> Internally this is a plain {@code double[]} indexed by
 *  {@link Texture#ordinal()} (plus the scalar inset) &mdash; not a map. The key space is a
 *  small fixed enum, so a flat array makes the hot operations ({@link #intensityOf},
 *  {@link #average}) tight loops with no hashing, boxing or bucket lookups. It is a
 *  {@code class} rather than a {@code record} purely so it can <i>encapsulate</i> that
 *  mutable array (defensively owned, never handed out) while still behaving as an immutable
 *  <b>value</b>: {@code equals}/{@code hashCode} compare the intensities (and inset)
 *  element-wise, every mutator returns a new profile, and all values are clamped to
 *  {@code [0, 1]}.
 */
public final class TextureProfile
{
    private static final int COUNT = Texture.values().length;
    private static final TextureProfile _NONE = new TextureProfile(new double[COUNT], 0.0);

    /** A surface is treated as a solid, drawable voxel only when its {@link Texture#OPACITY} is at least this. */
    public static final double OPACITY_THRESHOLD = 0.25;

    /** Per-quality intensities in {@code [0, 1]}, indexed by {@link Texture#ordinal()}. Never exposed. */
    private final double[] _intensities;
    /** How far content is recessed behind this face, in {@code [0, 1]} of the sector's extent (geometry, not appearance). */
    private final double _inset;

    /** Takes ownership of {@code intensities}: callers must not retain or mutate it afterwards. */
    private TextureProfile( double[] intensities, double inset ) {
        _intensities = intensities;
        _inset = clamp(inset);
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

    /** @return How far this face's content is recessed, in {@code [0, 1]} of the sector's extent ({@code 0} = flush with the face). */
    public double inset() {
        return _inset;
    }

    /** @return A copy with {@code quality} set to {@code intensity} (clamped to {@code [0, 1]}). */
    public TextureProfile with( Texture quality, double intensity ) {
        double[] copy = _intensities.clone();
        copy[quality.ordinal()] = clamp(intensity);
        return new TextureProfile(copy, _inset);
    }

    /** @return A copy with this face's content recessed by {@code inset} (clamped to {@code [0, 1]}). */
    public TextureProfile withInset( double inset ) {
        return new TextureProfile(_intensities, clamp(inset)); // intensities are immutable-by-encapsulation, so shared.
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
        return new TextureProfile(result, _inset + (other._inset - _inset) * weight);
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
        double insetSum = 0;
        int count = 0;
        for ( TextureProfile sample : samples ) {
            count++;
            insetSum += sample._inset;
            double[] s = sample._intensities;
            for ( int i = 0; i < COUNT; i++ )
                sums[i] += s[i];
        }
        if ( count == 0 )
            return none();
        for ( int i = 0; i < COUNT; i++ )
            sums[i] /= count;
        return new TextureProfile(sums, insetSum / count);
    }

    /**
     *  @return {@code true} if {@code other} has the same appearance (per-quality intensities) as this,
     *          <i>ignoring</i> the {@link #inset()}. The greedy mesher merges adjacent coplanar faces by
     *          this, not {@link #equals}, so faces that look identical but recede differently still merge
     *          into one rectangle (the inset is geometry the per-cell mesh does not use; it is consumed
     *          only when a sector is drawn as a single box &mdash; see {@link WorldSectorEtherData#shrink}).
     */
    public boolean sameAppearance( TextureProfile other ) {
        return this == other || Arrays.equals(_intensities, other._intensities);
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
        return _inset == other._inset && Arrays.equals(_intensities, other._intensities);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(_intensities) + Double.hashCode(_inset);
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
        if ( _inset > 0 ) {
            if ( !first ) sb.append(", ");
            sb.append("inset=").append(_inset);
        }
        return sb.append(']').toString();
    }
}