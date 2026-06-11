package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
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
 *          appearance qualities <i>and</i> that face's {@link TextureProfile#inset() inset}
 *          (how far content is recessed behind it), stored per side because only the outer
 *          faces of a cube are ever seen. {@link #shrink} turns those six insets into a
 *          content-fitting box.</li>
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
    // sits on the hot rendering path, hence the lazy cache.
    private final Lazy<Boolean> _majorityOpaque = Lazy.of(this::computeMajorityOpaque);

    // Cached hash (0 = not yet computed; the recompute is benign). Ether hashes feed every sector
    // hash and value-keyed renderer cache, and hashing six profiles is not free - same scheme as
    // WorldSector's memoized hash.
    private int _hash;

    /** Takes ownership of {@code sides}: callers must not retain or mutate it afterwards. */
    private WorldSectorEtherData( MaterialId material, TextureProfile[] sides ) {
        _material = material;
        _sides = sides;
    }

    /** The interned uniform ether per material (see {@link #of(Material)}). */
    private static final java.util.concurrent.ConcurrentHashMap<Material, WorldSectorEtherData> UNIFORM =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Empty space: the air material with an invisible appearance on every side. */
    public static WorldSectorEtherData empty() {
        return of(Material.AIR);
    }

    /**
     *  @return The ether whose six faces all show {@code material}'s default appearance &mdash;
     *          <b>interned</b>: the same shared instance per material. A generated chunk holds hundreds
     *          of thousands of uniform leaves; sharing one instance per material means their memoized
     *          predicates, hashes and identity-fast equality are paid once per process, not per voxel.
     */
    public static WorldSectorEtherData of( Material material ) {
        return UNIFORM.computeIfAbsent(material, m -> uniform(m.materialId(), m.texture()));
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

    /** @return How far content is recessed behind {@code side}, in {@code [0, 1]} of the extent ({@code 0} = flush). */
    public double insetOf( Side side ) {
        return _sides[side.ordinal()].inset();
    }

    /**
     *  Shrinks {@code bounds} inward on each face by that face's {@link TextureProfile#inset() inset}, so a
     *  coarse level-of-detail box fits the matter inside it instead of drawing as a full cube. If opposing
     *  insets would cross, that axis collapses to a zero-width slab at their midpoint (never an inverted box).
     *
     *  @param bounds The full sector bounds to inset.
     *  @return The content-fitting sub-box (or {@code bounds} itself when no face is recessed).
     */
    public BoundsF64 shrink( BoundsF64 bounds ) {
        VecF64 min = bounds.min(), max = bounds.max(), size = bounds.size();
        double[] lo = { min.x(), min.y(), min.z() };
        double[] hi = { max.x(), max.y(), max.z() };
        double[] extent = { size.x(), size.y(), size.z() };
        Side[] negative = { Side.NEG_X, Side.NEG_Y, Side.NEG_Z };
        Side[] positive = { Side.POS_X, Side.POS_Y, Side.POS_Z };

        boolean any = false;
        for ( int axis = 0; axis < 3; axis++ ) {
            double negInset = insetOf(negative[axis]), posInset = insetOf(positive[axis]);
            if ( negInset == 0 && posInset == 0 )
                continue;
            any = true;
            double newLo = lo[axis] + negInset * extent[axis];
            double newHi = hi[axis] - posInset * extent[axis];
            if ( newLo > newHi ) {
                double mid = (newLo + newHi) / 2;
                newLo = newHi = mid;
            }
            lo[axis] = newLo;
            hi[axis] = newHi;
        }
        if ( !any )
            return bounds;
        return BoundsF64.of(VecF64.of(lo[0], lo[1], lo[2]), VecF64.of(hi[0], hi[1], hi[2]));
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
        // Equivalent to combined().isOpaque() - the average of the sides' OPACITY is the OPACITY of the
        // averaged profile - but without materializing the full 20-quality average. This predicate is
        // probed for every grid cell of every mesh build (hundreds of thousands of fresh leaf ethers
        // when a generated chunk is meshed), where combined()'s allocations dominated whole frames.
        double opacity = 0;
        for ( TextureProfile side : _sides )
            opacity += side.intensityOf(Texture.OPACITY);
        return opacity / _sides.length >= TextureProfile.OPACITY_THRESHOLD;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof WorldSectorEtherData other) ) return false;
        return _material.equals(other._material) && Arrays.equals(_sides, other._sides);
    }

    @Override
    public int hashCode() {
        int h = _hash;
        if ( h == 0 ) {
            h = 31 * _material.hashCode() + Arrays.hashCode(_sides);
            _hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return "WorldSectorEtherData[material=" + _material + ", sides=" + Arrays.toString(_sides) + ']';
    }
}