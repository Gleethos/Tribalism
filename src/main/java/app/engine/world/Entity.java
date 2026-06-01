package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;

/**
 *  An actor in the world. Entities live in a separate fast-lookup structure
 *  (see {@link World#entities()}); the world tree only ever stores their
 *  positional {@link WorldTreeEntityId} (id + bounds).
 *  <p>
 *  This is a sum type: every entity carries a {@code treeId} (and therefore a
 *  {@code long} id and a {@link BoundsF64}), and concrete variants add their own
 *  payload. For now there are two:
 *  <ul>
 *      <li>{@link CameraEntity} &mdash; a viewpoint into the world.</li>
 *      <li>{@link VoxelEntity} &mdash; a movable mini-world of its own, described
 *          by a nested {@link WorldSection}. This lets an entity have the full
 *          recursive shape machinery of a world section while moving freely
 *          relative to the world it belongs to.</li>
 *  </ul>
 */
public sealed interface Entity permits Entity.CameraEntity, Entity.VoxelEntity
{
    /** @return The positional handle of this entity within the world tree. */
    WorldTreeEntityId treeId();

    /** @return The unique id of this entity. */
    default long id() { return treeId().id(); }

    /** @return The entity's axis-aligned bounds in world space. */
    default BoundsF64 bounds() { return treeId().bounds(); }

    /** A viewpoint into the world. */
    record CameraEntity(
        WorldTreeEntityId treeId,
        CameraF64 camera
    ) implements Entity {
        public static CameraEntity of( long id, CameraF64 camera ) {
            return new CameraEntity(WorldTreeEntityId.of(id, BoundsF64.cube(camera.position(), 1)), camera);
        }
    }

    /**
     *  A movable entity that is itself a small world: its shape and material are
     *  described by a nested {@link WorldSection} (which may recurse into finer
     *  sub-sections just like the main world tree).
     */
    record VoxelEntity(
        WorldTreeEntityId treeId,
        WorldSection section
    ) implements Entity {
        public static VoxelEntity of( long id, WorldSection section ) {
            return new VoxelEntity(WorldTreeEntityId.of(id, section.bounds()), section);
        }
    }
}