package app.engine.world;

import sprouts.Association;

import java.util.ArrayList;
import java.util.List;

/**
 *  Describes <i>what a {@link WorldSector} is made of</i>, stored as one
 *  {@link MaterialDistribution} per cube {@link Side face} rather than a single
 *  whole-sector mixture.
 *  <p>
 *  Storing material per side is what makes cheap, correct level-of-detail
 *  possible: materials are primarily a <i>visual</i> property and only the outer
 *  faces of a cube are ever seen, so a super-sector summarizes each of its faces
 *  from only the matching faces of the sub-sectors lying on that face &mdash;
 *  never the hidden interior (see {@link WorldSector#aggregated()}).
 *  <p>
 *  A face absent from the association reads back as {@link MaterialDistribution#empty()}.
 *  The record carries full value semantics by delegating to the association.
 *
 *  @param sides A mapping from cube face to the material mixture on that face.
 */
public record WorldSectorEtherData(
    Association<Side, MaterialDistribution> sides
) {
    private static final WorldSectorEtherData _EMPTY =
            new WorldSectorEtherData(Association.between(Side.class, MaterialDistribution.class));

    /** Ether made of nothing on every side. */
    public static WorldSectorEtherData empty() { return _EMPTY; }

    /** @return Ether whose six sides all carry the same {@code distribution}. */
    public static WorldSectorEtherData uniform( MaterialDistribution distribution ) {
        Association<Side, MaterialDistribution> sides = Association.between(Side.class, MaterialDistribution.class);
        for ( Side side : Side.values() )
            sides = sides.put(side, distribution);
        return new WorldSectorEtherData(sides);
    }

    /** @return Ether made entirely ({@code 100%}) of a single material on every side. */
    public static WorldSectorEtherData of( Material material ) {
        return uniform(MaterialDistribution.of(material));
    }

    /** @return The material distribution on the given {@code side} (empty if unset). */
    public MaterialDistribution sideOf( Side side ) {
        return sides.get(side).orElse(MaterialDistribution.empty());
    }

    /** @return A copy of this ether with {@code side} replaced by {@code distribution}. */
    public WorldSectorEtherData withSide( Side side, MaterialDistribution distribution ) {
        return new WorldSectorEtherData(sides.put(side, distribution));
    }

    /**
     *  @return A single representative mixture for the whole sector: the average
     *          of all six side distributions. Useful for code (and tests) that
     *          want one material for the sector rather than one per face.
     */
    public MaterialDistribution combined() {
        List<MaterialDistribution> all = new ArrayList<>(Side.values().length);
        for ( Side side : Side.values() )
            all.add(sideOf(side));
        return MaterialDistribution.average(all);
    }

    /** @return The dominant material of the {@link #combined() combined} mixture, or {@link Material#AIR}. */
    public Material dominantMaterial() {
        return combined().dominantMaterial();
    }
}