package app.engine.world;

import app.engine.primitives.BoundsF64;
import sprouts.Association;
import sprouts.Tuple;

import java.util.Optional;

/**
 *  The whole world as a single immutable value.
 *  <p>
 *  A world is just two things: the {@code root} of the spatial {@link WorldSector}
 *  tree, and an {@code entities} lookup from id to {@link Entity}. The tree is used
 *  purely for positional queries (it holds only {@link WorldTreeEntityId}s), while
 *  the association is the authoritative store of the actual entities. Keeping the
 *  two in sync is the job of {@link #withEntity} / {@link #withoutEntity}.
 *  <p>
 *  This value is what an update loop transforms from one tick to the next; nothing
 *  is ever mutated in place.
 *
 *  @param root     The root sector of the world tree.
 *  @param entities The lookup from entity id to the actual entity.
 */
public record World(
    WorldSector root,
    Association<Long, Entity> entities
) {
    /** A sensible default cap on how deep an entity may fall into the tree. */
    public static final int DEFAULT_MAX_DEPTH = 8;

    /** @return An empty world whose root covers {@code bounds}, made of nothing. */
    public static World of( BoundsF64 bounds ) {
        return new World(WorldSector.empty(bounds), Association.between(Long.class, Entity.class));
    }

    /** @return A world over the given {@code root} with no entities yet. */
    public static World of( WorldSector root ) {
        return new World(root, Association.between(Long.class, Entity.class));
    }

    public Optional<Entity> entity( long id ) {
        return entities.get(id);
    }

    public Tuple<Entity> allEntities() {
        return entities.values();
    }

    public World withRoot( WorldSector newRoot ) {
        return new World(newRoot, entities);
    }

    /**
     *  Adds (or replaces) an entity, keeping the tree and the lookup in sync: the
     *  entity's {@link WorldTreeEntityId} falls down into the tree, and the entity
     *  itself is stored in the lookup.
     */
    public World withEntity( Entity entity, int maxDepth ) {
        WorldSector newRoot = root.insert(entity.treeId(), maxDepth);
        return new World(newRoot, entities.put(entity.id(), entity));
    }

    public World withEntity( Entity entity ) {
        return withEntity(entity, DEFAULT_MAX_DEPTH);
    }

    /** Removes an entity from both the tree and the lookup. */
    public World withoutEntity( Entity entity, int maxDepth ) {
        WorldSector newRoot = root.remove(entity.treeId(), maxDepth);
        return new World(newRoot, entities.remove(entity.id()));
    }

    public World withoutEntity( Entity entity ) {
        return withoutEntity(entity, DEFAULT_MAX_DEPTH);
    }

    /**
     *  Re-places an entity whose bounds have changed: removes the old positional
     *  handle from the tree and inserts the updated one, then stores the new entity.
     *  This is the typical move performed during an update tick.
     *
     *  @param previous The entity as it currently sits in the world.
     *  @param updated  The new state of the same entity (same id, possibly new bounds).
     */
    public World withMovedEntity( Entity previous, Entity updated, int maxDepth ) {
        WorldSector newRoot = root.remove(previous.treeId(), maxDepth).insert(updated.treeId(), maxDepth);
        return new World(newRoot, entities.put(updated.id(), updated));
    }
}