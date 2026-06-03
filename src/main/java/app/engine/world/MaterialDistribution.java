package app.engine.world;

import sprouts.Association;
import sprouts.Pair;

/**
 *  A mixture of {@link Material} fractions, for example {@code 90% air, 5% soil,
 *  5% rock}. This is the building block of a sector's
 *  {@link WorldSectorEtherData ether}: a sector stores one distribution per
 *  {@link Side face}.
 *  <p>
 *  Fractions are stored in an immutable {@link Association}; a material absent
 *  from the association simply contributes {@code 0}. The record carries full
 *  value semantics by delegating to the association.
 *
 *  @param fractions A mapping from material to its (non-negative) fraction.
 */
public record MaterialDistribution(
    Association<Material, Double> fractions
) {
    private static final MaterialDistribution _EMPTY =
            new MaterialDistribution(Association.between(Material.class, Double.class));

    /** A distribution of nothing (every material at fraction {@code 0}). */
    public static MaterialDistribution empty() { return _EMPTY; }

    /** @return A distribution made entirely ({@code 100%}) of a single material. */
    public static MaterialDistribution of( Material material ) {
        return _EMPTY.with(material, 1.0);
    }

    /** @return A distribution with a single {@code material} set to {@code fraction}. */
    public static MaterialDistribution of( Material material, double fraction ) {
        return _EMPTY.with(material, fraction);
    }

    public MaterialDistribution {
        for ( Pair<Material, Double> entry : fractions ) {
            if ( entry.second() < 0 )
                throw new IllegalArgumentException(
                        "The fraction for material " + entry.first() + " must not be negative, but was " + entry.second() + "."
                    );
        }
    }

    /** @return The fraction made of {@code material}, or {@code 0} if absent. */
    public double fractionOf( Material material ) {
        return fractions.get(material).orElse(0.0);
    }

    /** @return A copy of this distribution with {@code material} set to {@code fraction}. */
    public MaterialDistribution with( Material material, double fraction ) {
        if ( fraction < 0 )
            throw new IllegalArgumentException("The fraction must not be negative, but was " + fraction + ".");
        return new MaterialDistribution(fractions.put(material, fraction));
    }

    /** @return The sum of all material fractions (typically {@code 1} when normalized). */
    public double total() {
        double sum = 0;
        for ( Pair<Material, Double> entry : fractions )
            sum += entry.second();
        return sum;
    }

    /**
     *  @return The material that makes up the largest fraction, or
     *          {@link Material#AIR} for an empty distribution.
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
     *  @return This distribution rescaled so that all fractions sum to {@code 1}.
     *          An empty distribution (total {@code 0}) is returned unchanged.
     */
    public MaterialDistribution normalized() {
        double total = total();
        if ( total == 0 )
            return this;
        Association<Material, Double> scaled = Association.between(Material.class, Double.class);
        for ( Pair<Material, Double> entry : fractions )
            scaled = scaled.put(entry.first(), entry.second() / total);
        return new MaterialDistribution(scaled);
    }

    /**
     *  Blends this distribution towards {@code other} by {@code weight}, per material.
     *  @param weight {@code 0} yields {@code this}, {@code 1} yields {@code other}.
     */
    public MaterialDistribution blend( MaterialDistribution other, double weight ) {
        Association<Material, Double> result = Association.between(Material.class, Double.class);
        for ( Material material : Material.values() ) {
            double a = fractionOf(material);
            double b = other.fractionOf(material);
            double mixed = a + (b - a) * weight;
            if ( mixed != 0 )
                result = result.put(material, mixed);
        }
        return new MaterialDistribution(result);
    }

    /**
     *  Computes the per-material average of many distributions. This is the core
     *  of level-of-detail aggregation: a parent sector's per-side ether is the
     *  average of the matching-side distributions of its boundary children.
     *
     *  @param samples The distributions to aggregate.
     *  @return The averaged distribution, or {@link #empty()} if there are no samples.
     */
    public static MaterialDistribution average( Iterable<MaterialDistribution> samples ) {
        Association<Material, Double> sums = Association.between(Material.class, Double.class);
        int count = 0;
        for ( MaterialDistribution sample : samples ) {
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
        return new MaterialDistribution(averaged);
    }
}