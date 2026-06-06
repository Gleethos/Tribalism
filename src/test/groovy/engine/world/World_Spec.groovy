package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Entity
import app.engine.world.Material
import app.engine.world.World
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("World - the whole world as one value")
@Narrative('''

    A world is the root of the sector tree plus an id-to-entity lookup. Adding or
    removing an entity must keep the two in sync: the tree holds the positional
    handle, the lookup holds the actual entity.

''')
class World_Spec extends Specification
{
    private static BoundsF64 cube( double from, double to ) {
        return BoundsF64.of(VecF64.of(from, from, from), VecF64.of(to, to, to))
    }

    private static Entity.VoxelEntity smallVoxel( long id, double at ) {
        var bounds = BoundsF64.of(VecF64.of(at, at, at), VecF64.of(at + 0.5, at + 0.5, at + 0.5))
        return Entity.VoxelEntity.of(id, WorldSector.leaf(bounds, WorldSectorEtherData.of(Material.ROCK)))
    }

    def "A fresh world is empty."()
    {
        given:
            var world = World.of(cube(0, 64))
        expect:
            world.allEntities().isEmpty()
            world.entity(1L).isEmpty()
            world.root().isLeaf()
    }

    def "Adding an entity stores it in the lookup and falls it into the tree."()
    {
        given:
            var world = World.of(cube(0, 64))
            var entity = smallVoxel(42L, 1.2)
        when:
            var updated = world.withEntity(entity)
        then: 'The lookup now resolves the entity by id.'
            updated.entity(42L).get() == entity
            updated.allEntities().size() == 1
        and: 'The tree was actually grown to position it (the root is no longer a bare leaf).'
            !updated.root().isLeaf()
    }

    def "Removing an entity clears it from both the lookup and the tree."()
    {
        given:
            var entity = smallVoxel(7L, 1.2)
            var world = World.of(cube(0, 64)).withEntity(entity)
        when:
            var removed = world.withoutEntity(entity)
        then:
            removed.entity(7L).isEmpty()
            removed.allEntities().isEmpty()
        and: 'The positional handle is gone from the tree as well.'
            countTreeEntities(removed.root()) == 0
    }

    def "Moving an entity re-positions its handle in the tree."()
    {
        given:
            var original = smallVoxel(5L, 1.2)
            var world = World.of(cube(0, 64)).withEntity(original)
        and: 'The same entity, now sitting in a different cell.'
            var moved = smallVoxel(5L, 50.2)
        when:
            var updated = world.withMovedEntity(original, moved, World.DEFAULT_MAX_DEPTH)
        then: 'Still exactly one entity, at its new state, present once in the tree.'
            updated.entity(5L).get() == moved
            updated.allEntities().size() == 1
            countTreeEntities(updated.root()) == 1
    }

    private static int countTreeEntities( WorldSector sector ) {
        int count = sector.entities().size()
        if ( !sector.isLeaf() ) {
            var node = sector.children()
            for ( int i = 0; i < node.size(); i++ )
                count += countTreeEntities(node.sector(i))
        }
        return count
    }
}