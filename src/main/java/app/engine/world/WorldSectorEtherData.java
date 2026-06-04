package app.engine.world;

import sprouts.Association;

import java.util.ArrayList;
import java.util.List;

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
 *  object: a {@code Specific} air material with {@link TextureProfile#none()} on
 *  every side, hence invisible.
 *  <p>
 *  A face absent from the association reads back as {@link TextureProfile#none()}.
 *  The record carries full value semantics by delegating to its fields.
 *
 *  @param material A single gameplay material id for the whole sector.
 *  @param sides    A mapping from cube face to the appearance on that face.
 */
public record WorldSectorEtherData(
    MaterialId material,
    Association<Side, TextureProfile> sides
) {
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
        Association<Side, TextureProfile> sides = Association.between(Side.class, TextureProfile.class);
        for ( Side side : Side.values() )
            sides = sides.put(side, appearance);
        return new WorldSectorEtherData(material, sides);
    }

    /** @return The appearance on the given {@code side} (empty/invisible if unset). */
    public TextureProfile sideOf( Side side ) {
        return sides.get(side).orElse(TextureProfile.none());
    }

    /** @return A copy of this ether with {@code side} replaced by {@code appearance}. */
    public WorldSectorEtherData withSide( Side side, TextureProfile appearance ) {
        return new WorldSectorEtherData(material, sides.put(side, appearance));
    }

    /** @return A copy of this ether with a different whole-sector {@code material}. */
    public WorldSectorEtherData withMaterial( MaterialId material ) {
        return new WorldSectorEtherData(material, sides);
    }

    /**
     *  @return {@code true} if the sector is fully transparent &mdash; invisible on
     *          every face (empty space / air). This is the "is this empty?" test the
     *          inset algorithm uses to peel off empty layers.
     */
    public boolean isInvisible() {
        for ( Side side : Side.values() )
            if ( !sideOf(side).isInvisible() )
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
        for ( Side side : Side.values() )
            if ( sideOf(side).intensityOf(Texture.OPACITY) < 1.0 )
                return false;
        return true;
    }

    /**
     *  @return A single representative appearance for the whole sector: the average
     *          of all six side profiles. Useful for code (and tests) that want one
     *          appearance for the sector rather than one per face.
     */
    public TextureProfile combined() {
        List<TextureProfile> all = new ArrayList<>(Side.values().length);
        for ( Side side : Side.values() )
            all.add(sideOf(side));
        return TextureProfile.average(all);
    }
}