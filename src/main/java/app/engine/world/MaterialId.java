package app.engine.world;

/**
 *  Identifies <i>what a sector is actually made of</i> &mdash; the gameplay-level
 *  substance, kept entirely separate from how the sector <i>looks</i> (its
 *  {@link TextureProfile appearance}).
 *  <p>
 *  It is a sum type, so the engine can scale to thousands of materials without an
 *  enum:
 *  <ul>
 *      <li>{@link Specific} &mdash; a single concrete material, a thin wrapper around
 *          an integer id (resolved against a material registry such as
 *          {@link Material}).</li>
 *      <li>{@link Diverse} &mdash; the null object: "many different materials". A
 *          super-sector aggregated from sub-sectors that disagree on their material
 *          is {@code Diverse}, because it is no longer one single thing.</li>
 *  </ul>
 *  A player never sees percentages: a leaf "block" is always exactly one
 *  {@link Specific} material, and only coarse level-of-detail aggregates become
 *  {@link Diverse}.
 */
public sealed interface MaterialId permits MaterialId.Specific, MaterialId.Diverse {

    /** A single concrete material, identified by its registry {@code id}. */
    record Specific(int id) implements MaterialId {}

    /** The "many materials" null object (see {@link MaterialId#diverse()}). */
    record Diverse() implements MaterialId {}

    /** The canonical {@link Diverse} instance. */
    MaterialId DIVERSE = new Diverse();

    /** @return The shared {@link Diverse} null object. */
    static MaterialId diverse() { return DIVERSE; }

    /** @return A {@link Specific} material id wrapping {@code id}. */
    static MaterialId of( int id ) { return new Specific(id); }

    /** @return {@code true} if this is the {@link Diverse} null object. */
    default boolean isDiverse() { return this instanceof Diverse; }

    /**
     *  Merges many material ids into the one that describes their union: the shared
     *  {@link Specific} id if they all agree, otherwise {@link #diverse()}. This is
     *  how a parent sector derives its single material from its children during
     *  level-of-detail aggregation.
     *
     *  @param ids The child material ids to merge.
     *  @return The common {@link Specific} id, or {@link #diverse()} if they differ
     *          (or there are none).
     */
    static MaterialId merge( Iterable<MaterialId> ids ) {
        MaterialId common = null;
        for ( MaterialId id : ids ) {
            if ( common == null )
                common = id;
            else if ( !common.equals(id) )
                return DIVERSE;
        }
        return common == null ? DIVERSE : common;
    }
}