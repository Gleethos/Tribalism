package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.EngineInputs
import app.engine.world.World
import app.engine.world.WorldTreeNode
import app.engine.world.gen.PerlinNoise
import app.engine.world.gen.WorldGenerator
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("World - generating terrain around cameras")
@Narrative('''

    The generator now lives inside the World. A world built with one starts as a grid
    of empty top-level "chunk" cells; each update generates the chunks within the
    generator's reach of any camera, so a camera always has something around it. Far
    chunks stay empty until a camera comes near, and an already-generated chunk is never
    regenerated.

''')
class WorldGeneration_Spec extends Specification
{
    private static final long CAMERA = 1L

    // Caves disabled (caveThreshold 2.0) and a very low sea level so deep cells are solid rock.
    private static WorldGenerator generator( double reach ) {
        return new WorldGenerator(new PerlinNoise(1L), -1000d, 0d, 24d, 0.015d, 6d, 1.5d, 2.0d, reach, 1)
    }

    private static CameraF64 cameraAt( VecF64 p ) {
        return new CameraF64(p, p.add(VecF64.of(0, 0, 1)), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
    }

    private static World worldWithCameraAt( VecF64 p, double reach ) {
        return World.of(BoundsF64.cube(VecF64.zero(), 128), generator(reach)).createCamera(CAMERA, cameraAt(p))
    }

    def "A generator-backed world starts as empty chunks and exposes its generator."()
    {
        given:
            var world = World.of(BoundsF64.cube(VecF64.zero(), 128), generator(20))
        expect: 'The generator is queryable...'
            world.generator().isPresent()
        and: '...and before any update every top-level chunk is still empty air.'
            !world.root().isLeaf()
            (0..<WorldTreeNode.SECTOR_COUNT).every { world.root().children().sector(it).isFullyTransparent() }
    }

    def "A hand-built world has no generator and never generates on update."()
    {
        given:
            var world = World.of(BoundsF64.cube(VecF64.zero(), 128)).createCamera(CAMERA, cameraAt(VecF64.of(0, -50, 0)))
        when:
            var updated = world.update(EngineInputs.of(0.0))
        then:
            world.generator().isEmpty()
            updated == world
            updated.root().isLeaf()
    }

    def "Update builds solid terrain in the chunk around a camera, leaving distant chunks empty."()
    {
        given: 'A camera deep underground (everything below the surface band is rock).'
            var world = worldWithCameraAt(VecF64.of(0, -50, 0), 20)
        when:
            var built = world.update(EngineInputs.of(0.0))
        then: 'The chunk the camera sits in (cell 4,0,4) is now solid rock...'
            built.root().children().sector(WorldTreeNode.indexOf(4, 0, 4)).isSolidOpaque()
        and: '...while a far corner chunk (cell 0,0,0), out of reach, is still empty air.'
            built.root().children().sector(WorldTreeNode.indexOf(0, 0, 0)).isFullyTransparent()
    }

    def "A larger reach generates more terrain than a smaller one."()
    {
        when:
            var near = world(20)
            var far  = world(400) // spans the whole region
        then: 'Both built something, and the larger reach built strictly more.'
            generatedCount(near) > 0
            generatedCount(far) > generatedCount(near)
    }

    def "Generation is idempotent: re-updating with the camera unmoved changes nothing."()
    {
        given:
            var once = worldWithCameraAt(VecF64.of(0, -50, 0), 64).update(EngineInputs.of(0.0))
        when:
            var twice = once.update(EngineInputs.of(0.0))
        then:
            twice == once
    }

    private static World world( double reach ) {
        return worldWithCameraAt(VecF64.of(0, -50, 0), reach).update(EngineInputs.of(0.0))
    }

    /** Counts top-level chunks that have actually been generated (no longer empty air). */
    private static int generatedCount( World world ) {
        return (0..<WorldTreeNode.SECTOR_COUNT).count { !world.root().children().sector(it).isFullyTransparent() }
    }
}