package app.engine.world;

import app.engine.primitives.BoundsF64;

/**
 *  A lightweight, positional handle to an entity, stored inside the world tree.
 *  <p>
 *  Deliberately, this is <i>not</i> the entity itself. The real entities live in
 *  a separate, fast-lookup data structure. The tree only ever holds an entity's
 *  unique {@code id} together with its {@code bounds}. This separation means the
 *  spatial tree only has to be touched when an entity's bounding box actually
 *  changes (position or size) &mdash; ordinary state changes to an entity never
 *  ripple into a tree traversal.
 *
 *  @param id     The unique identifier of the entity.
 *  @param bounds The entity's axis-aligned bounding box in world space.
 */
public record WorldTreeEntityId(
    long id,
    BoundsF64 bounds
) {
    public static WorldTreeEntityId of( long id, BoundsF64 bounds ) {
        return new WorldTreeEntityId(id, bounds);
    }
}