package app.engine.world;

import sprouts.Association;
import sprouts.Pair;

/**
 *  Describes <i>what a {@code WorldSector} is made of</i> as a mixture of
 *  {@link Material} fractions, for example {@code 90% air, 5% soil, 5% rock}.
 *  <p>
 *  This "ether data" is the basis for two things:
 *  <ul>
 *      <li>Rendering: the mixture determines the colour, shape and reflective
 *          properties of a sector.</li>
 *      <li>Level of detail: a parent sector computes its own ether data by
 *          {@link #average(java.lang.Iterable) averaging} the ether data of its
 *          children, which lets a whole sub-tree collapse into a single
 *          representative voxel.</li>
 *  </ul>
 *  Fractions are stored in an immutable {@link Association}; a material absent
 *  from the association simply contributes {@code 0}. The record carries full
 *  value semantics by delegating to the association.
 *
 *  @param fractions A mapping from material to its (non-negative) fraction of the sector.
 */
public record WorldSectorEtherData(
    Association<Material, Double> fractions
) {
    private static final WorldSectorEtherData _EMPTY =
            new WorldSectorEtherData(Association.between(Material.class, Double.class));

    /** A sector made of nothing (every material at fraction {@code 0}). */
    public static WorldSectorEtherData empty() { return _EMPTY; }

    /** @return A sector made entirely ({@code 100%}) of a single material. */
    public static WorldSectorEtherData of( Material material ) {
        return _EMPTY.with(material, 1.0);
    }

    public static WorldSectorEtherData of( Material material, double fraction ) {
        return _EMPTY.with(material, fraction);
    }

    public WorldSectorEtherData {
        for ( Pair<Material, Double> entry : fractions ) {
            if ( entry.second() < 0 )
                throw new IllegalArgumentException(
                        "The fraction for material " + entry.first() + " must not be negative, but was " + entry.second() + "."
                    );
        }
    }

    /** @return The fraction of the sector made of {@code material}, or {@code 0} if absent. */
    public double fractionOf( Material material ) {
        return fractions.get(material).orElse(0.0);
    }

    /** @return A copy of this ether data with {@code material} set to {@code fraction}. */
    public WorldSectorEtherData with( Material material, double fraction ) {
        if ( fraction < 0 )
            throw new IllegalArgumentException("The fraction must not be negative, but was " + fraction + ".");
        return new WorldSectorEtherData(fractions.put(material, fraction));
    }

    /** @return The sum of all material fractions (typically {@code 1} when normalized). */
    public double total() {
        double sum = 0;
        for ( Pair<Material, Double> entry : fractions )
            sum += entry.second();
        return sum;
    }

    /**
     *  @return The material that makes up the largest fraction of this sector,
     *          or {@link Material#AIR} for empty ether data.
     */
    public Material dominantMaterial() {
        Material dominant = Material.AIR;
        double best = -1;
        for ( Pair<Material, Double> entry : fractions ) {
            if ( entry.second() > best ) {
                best = entry.second();
                dominant = entry.first();
            }
        }
        return dominant;
    }

    /**
     *  @return This ether data rescaled so that all fractions sum to {@code 1}.
     *          Empty ether data (total {@code 0}) is returned unchanged.
     */
    public WorldSectorEtherData normalized() {
        double total = total();
        if ( total == 0 )
            return this;
        Association<Material, Double> scaled = Association.between(Material.class, Double.class);
        for ( Pair<Material, Double> entry : fractions )
            scaled = scaled.put(entry.first(), entry.second() / total);
        return new WorldSectorEtherData(scaled);
    }

    /**
     *  Blends this ether data towards {@code other} by {@code weight}, per material.
     *  @param weight {@code 0} yields {@code this}, {@code 1} yields {@code other}.
     */
    public WorldSectorEtherData blend( WorldSectorEtherData other, double weight ) {
        Association<Material, Double> result = Association.between(Material.class, Double.class);
        for ( Material material : Material.values() ) {
            double a = fractionOf(material);
            double b = other.fractionOf(material);
            double mixed = a + (b - a) * weight;
            if ( mixed != 0 )
                result = result.put(material, mixed);
        }
        return new WorldSectorEtherData(result);
    }

    /**
     *  Computes the per-material average of many ether data samples. This is the
     *  core of level-of-detail aggregation: a parent sector's ether data is the
     *  average of its children's.
     *
     *  @param samples The ether data of the children to aggregate.
     *  @return The averaged ether data, or {@link #empty()} if there are no samples.
     */
    public static WorldSectorEtherData average( Iterable<WorldSectorEtherData> samples ) {
        Association<Material, Double> sums = Association.between(Material.class, Double.class);
        int count = 0;
        for ( WorldSectorEtherData sample : samples ) {
            count++;
            for ( Material material : Material.values() ) {
                double f = sample.fractionOf(material);
                if ( f != 0 )
                    sums = sums.put(material, sums.get(material).orElse(0.0) + f);
            }
        }
        if ( count == 0 )
            return empty();
        Association<Material, Double> averaged = Association.between(Material.class, Double.class);
        for ( Pair<Material, Double> entry : sums )
            averaged = averaged.put(entry.first(), entry.second() / count);
        return new WorldSectorEtherData(averaged);
    }
}