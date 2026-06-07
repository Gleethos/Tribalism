package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.Entity
import app.engine.world.Material
import app.engine.world.ScreenId
import app.engine.world.World
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("World - screens and cameras")
@Narrative('''

    The world knows about its screens (id -> Screen) and its cameras (camera entities,
    by id). Screens are created, bound to a camera by id, resized and destroyed through
    the World; a screen references its camera one-way so one camera can drive several
    screens. Cameras live only in the entity lookup (they have no voxel presence), so
    creating one never grows the spatial tree.

''')
class WorldScreens_Spec extends Specification
{
    private static final ScreenId SCREEN = ScreenId.of(7L)

    private static CameraF64 camera() {
        return new CameraF64(VecF64.of(0, 0, 10), VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
    }

    private static World freshWorld() {
        return World.of(BoundsF64.cube(VecF64.zero(), 128))
    }

    def "A created screen is unbound and queryable by id."()
    {
        when:
            var world = freshWorld().createScreen(SCREEN, 800, 600)
        then:
            world.screen(SCREEN).isPresent()
            world.screen(SCREEN).get().width() == 800
            world.screen(SCREEN).get().height() == 600
            !world.screen(SCREEN).get().isBound()
            world.screen(SCREEN).get().camera().isEmpty()
            world.allScreens().size() == 1
        and: 'An unknown id resolves to nothing.'
            world.screen(ScreenId.of(999L)).isEmpty()
    }

    def "Creating a camera stores it as an entity but does not grow the spatial tree."()
    {
        when:
            var world = freshWorld().createCamera(1L, camera())
        then: 'It is retrievable as a camera entity...'
            world.camera(1L).isPresent()
            world.camera(1L).get().camera() == camera()
            world.entity(1L).isPresent()
        and: '...yet the root is still a bare leaf - cameras are not placed positionally.'
            world.root().isLeaf()
    }

    def "camera() returns nothing for a non-camera entity."()
    {
        given:
            var voxel = Entity.VoxelEntity.of(2L, WorldSector.leaf(BoundsF64.cube(VecF64.of(1,1,1), 0.5), WorldSectorEtherData.of(Material.ROCK)))
        when:
            var world = freshWorld().withEntity(voxel)
        then:
            world.entity(2L).isPresent()
            world.camera(2L).isEmpty()
    }

    def "Binding points a screen at a camera by id; the binding survives a resize."()
    {
        given:
            var world = freshWorld().createCamera(1L, camera()).createScreen(SCREEN, 800, 600)
        when:
            world = world.bindScreenToCamera(SCREEN, 1L)
        then:
            world.screen(SCREEN).get().isBound()
            world.screen(SCREEN).get().camera().getAsLong() == 1L
        when: 'The screen is resized.'
            world = world.resizeScreen(SCREEN, 1024, 768)
        then: 'The new size is stored and the camera binding is preserved.'
            world.screen(SCREEN).get().width() == 1024
            world.screen(SCREEN).get().height() == 768
            world.screen(SCREEN).get().camera().getAsLong() == 1L
    }

    def "One camera can be bound to several screens."()
    {
        given:
            var a = ScreenId.of(1L)
            var b = ScreenId.of(2L)
        when:
            var world = freshWorld().createCamera(1L, camera())
                                    .createScreen(a, 800, 600).bindScreenToCamera(a, 1L)
                                    .createScreen(b, 640, 480).bindScreenToCamera(b, 1L)
        then:
            world.screen(a).get().camera().getAsLong() == 1L
            world.screen(b).get().camera().getAsLong() == 1L
    }

    def "Destroying a screen removes it; destroying a camera removes the entity."()
    {
        given:
            var world = freshWorld().createCamera(1L, camera()).createScreen(SCREEN, 800, 600).bindScreenToCamera(SCREEN, 1L)
        expect:
            world.destroyScreen(SCREEN).screen(SCREEN).isEmpty()
            world.destroyCamera(1L).camera(1L).isEmpty()
    }

    def "Binding a screen that does not exist is rejected."()
    {
        when:
            freshWorld().createCamera(1L, camera()).bindScreenToCamera(ScreenId.of(404L), 1L)
        then:
            thrown(IllegalArgumentException)
    }

    def "Collecting for a screen yields nothing when it is unknown, unbound, or its camera is gone."()
    {
        given:
            var world = freshWorld().createScreen(SCREEN, 200, 200)
            var sink = { s, v, r -> } as app.engine.world.SectorDrawCollector
        expect: 'Unknown screen.'
            world.collectSectorsForRendering(ScreenId.of(999L), 64.0, sink) == World.RenderStats.NONE
        and: 'Known but unbound screen.'
            world.collectSectorsForRendering(SCREEN, 64.0, sink) == World.RenderStats.NONE
        and: 'Bound to a camera that was never created (dangling).'
            world.bindScreenToCamera(SCREEN, 123L).collectSectorsForRendering(SCREEN, 64.0, sink) == World.RenderStats.NONE
    }
}