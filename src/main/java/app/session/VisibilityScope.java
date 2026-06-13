package app.session;

/**
 *  How much of the world a {@link SessionParticipant} may see — the lever behind fog of war and
 *  message visibility (see {@code VISION.md} §7.1/§7.2). {@link Omniscient} participants (the GM,
 *  the AI when running as GM) see everything; a {@link Fogged} participant sees only through their
 *  character (a view cone + explored memory, computed by the engine).
 */
public sealed interface VisibilityScope permits VisibilityScope.Omniscient, VisibilityScope.Fogged {

    record Omniscient() implements VisibilityScope {}

    /** Sees the world through the character with this id (fog of war applies). */
    record Fogged(long characterId) implements VisibilityScope {}

    VisibilityScope OMNISCIENT = new Omniscient();

    static VisibilityScope fogged( long characterId ) { return new Fogged(characterId); }

    default boolean isOmniscient() { return this instanceof Omniscient; }
}
