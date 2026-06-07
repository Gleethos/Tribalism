package app.engine.world;

import app.engine.util.Lazy;

import java.util.Arrays;

/**
 *  The "ether" of a {@link WorldSector}: what it looks like and what it is made of.
 *  <p>
 *  It carries two independent things:
 *  <ul>
 *      <li>a single {@link MaterialId material} for the whole cube &mdash; the
 *          gameplay substance (one per sector; {@link MaterialId#diverse() Diverse}
 *          once a coarse aggregate mixes several materials); and</li>
 *      <li>a {@link TextureProfile} per cube {@link Side face} &mdash; the visual
 *          appearance qualities, stored per side because only the outer faces of a
 *          cube are ever seen.</li>
 *  </ul>
 *  This replaces the old material-percentage model: appearance is now a set of
 *  independent {@link Texture} qualities (not a distribution that sums to one), and
 *  "what it is" is a single id rather than a mixture. Empty space (air) is the null
 *  object: an air material with {@link TextureProfile#none()} on every side.
 *  <p>
 *  <b>Representation.</b> The six faces are a plain {@code TextureProfile[]} indexed by
 *  {@link Side#ordinal()} &mdash; not a map &mdash; so {@link #sideOf} is a direct array
 *  read with no hashing, which matters because it sits on the hot aggregation path. It is
 *  a {@code class} rather than a {@code record} purely to <i>encapsulate</i> that array
 *  (owned, never exposed) while staying an immutable <b>value</b>: every mutator returns a
 *  new ether and {@code equals}/{@code hashCode} compare material plus the six faces.
 */
public final class WorldSectorEtherData
{
    private static final int SIDES = Side.values().length;

    private final MaterialId _material;
    /** Appearance per face, indexed by {@link Side#ordinal()}; entries are never null. Never exposed. */
    private final TextureProfile[] _sides;

    // Derived, memoized: whether this sector reads as a solid surface. Purely a function of
    // the fields above, so it is excluded from equals/hashCode and computed at most once. It
    // sits on the hot rendering path and combined() is not cheap, hence the lazy cache.
    private final Lazy<Boolean> _majorityOpaque = Lazy.of(this::computeMajorityOpaque);

    /** Takes ownership of {@code sides}: callers must not retain or mutate it afterwards. */
    private WorldSectorEtherData( MaterialId material, TextureProfile[] sides ) {
        _material = material;
        _sides = sides;
    }

    /** Empty space: the air material with an invisible appearance on every side. */
    public static WorldSectorEtherData empty() {
        return of(Material.AIR);
    }

    /** @return Ether whose six faces all show {@code material}'s default appearance. */
    public static WorldSectorEtherData of( Material material ) {
        return uniform(material.materialId(), material.texture());
    }

    /** @return Ether with the given {@code material} and the same {@code appearance} on all six faces. */
    public static WorldSectorEtherData uniform( MaterialId material, TextureProfile appearance ) {
        TextureProfile[] sides = new TextureProfile[SIDES];
        Arrays.fill(sides, appearance);
        return new WorldSectorEtherData(material, sides);
    }

    /** @return The single gameplay material id of the whole cube. */
    public MaterialId material() {
        return _material;
    }

    /** @return The appearance on the given {@code side}. */
    public TextureProfile sideOf( Side side ) {
        return _sides[side.ordinal()];
    }

    /** @return A copy of this ether with {@code side} replaced by {@code appearance}. */
    public WorldSectorEtherData withSide( Side side, TextureProfile appearance ) {
        TextureProfile[] copy = _sides.clone();
        copy[side.ordinal()] = appearance;
        return new WorldSectorEtherData(_material, copy);
    }

    /** @return A copy of this ether with a different whole-sector {@code material}. */
    public WorldSectorEtherData withMaterial( MaterialId material ) {
        return new WorldSectorEtherData(material, _sides); // the faces array is immutable-by-encapsulation, so it is shared.
    }

    /**
     *  @return {@code true} if the sector is fully transparent &mdash; invisible on
     *          every face (empty space / air). This is the "is this empty?" test the
     *          inset algorithm uses to peel off empty layers.
     */
    public boolean isInvisible() {
        for ( TextureProfile side : _sides )
            if ( !side.isInvisible() )
                return false;
        return true;
    }

    /**
     *  @return {@code true} if the sector is fully opaque on every face
     *          ({@link Texture#OPACITY} {@code == 1}). A leaf like this is a perfect
     *          occluder: nothing behind it can be seen through it (see
     *          {@link WorldSector#isSolidOpaque()}).
     */
    public boolean isFullyOpaque() {
        for ( TextureProfile side : _sides )
            if ( side.intensityOf(Texture.OPACITY) < 1.0 )
                return false;
        return true;
    }

    /**
     *  @return A single representative appearance for the whole sector: the average
     *          of all six side profiles. Useful for code (and tests) that want one
     *          appearance for the sector rather than one per face.
     */
    public TextureProfile combined() {
        return TextureProfile.average(Arrays.asList(_sides));
    }

    /**
     *  @return {@code true} if this sector reads as a solid, drawable surface &mdash; its
     *          {@link #combined() combined} appearance is {@link TextureProfile#isOpaque()
     *          opaque}. Gating on the averaged opacity keeps a mostly-empty coarse box from
     *          inflating past the true surface. Memoized, as it is queried per visible sector
     *          every frame and {@link #combined()} allocates and sums all six faces.
     */
    public boolean isMajorityOpaque() {
        return _majorityOpaque.get();
    }

    private boolean computeMajorityOpaque() {
        return combined().isOpaque();
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof WorldSectorEtherData other) ) return false;
        return _material.equals(other._material) && Arrays.equals(_sides, other._sides);
    }

    @Override
    public int hashCode() {
        return 31 * _material.hashCode() + Arrays.hashCode(_sides);
    }

    @Override
    public String toString() {
        return "WorldSectorEtherData[material=" + _material + ", sides=" + Arrays.toString(_sides) + ']';
    }
}