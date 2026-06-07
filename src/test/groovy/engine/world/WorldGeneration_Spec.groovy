package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.EngineInputs
import app.engine.world.World
import app.engine.world.gen.PerlinNoise
import app.engine.world.gen.WorldGenerator
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("World - infinite generation around cameras")
@Narrative('''

    The generator lives inside the World, which is now effectively infinite: it starts as a
    single empty chunk and, on each update, streams chunks in around every camera within the
    generator's reach - growing the spatial tree's root outward (re-rooting) whenever a camera
    roams beyond it. Already-generated chunks are never rebuilt.

    Generation is budgeted: a single update only builds the nearest few chunks, so flying never
    stalls; the rest stream in over following ticks. These specs therefore drive the world to a
    settled state (repeated updates with the camera unmoved) before asserting on what exists.

''')
class WorldGeneration_Spec extends Specification
{
    private static final long CAMERA = 1L

    // Caves disabled (caveThreshold 2.0) and a very low sea level, so anything well below the
    // surface band is solid rock. chunkSize 64, detailDepth 1.
    private static WorldGenerator generator( double reach ) {
        return new WorldGenerator(new PerlinNoise(1L), -1000d, 0d, 24d, 0.015d, 6d, 1.5d, 2.0d, reach, 1, 64d)
    }

    private static CameraF64 cameraAt( VecF64 p ) {
        return new CameraF64(p, p.add(VecF64.of(0, 0, 1)), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
    }

    /** Drives the world with the camera unmoved until generation settles (no chunk is left to build). */
    private static World settled( World world ) {
        World previous
        do {
            previous = world
            world = world.update(EngineInputs.of(0.0))
        } while ( !world.is(previous) ) // update returns the SAME instance once nothing is left to stream in
        return world
    }

    private static World builtAround( VecF64 eye, double reach ) {
        return settled(World.of(generator(reach)).createCamera(CAMERA, cameraAt(eye)))
    }

    def "A generator-backed world starts as a single empty chunk and exposes its generator."()
    {
        given:
            var world = World.of(generator(20))
        expect:
            world.generator().isPresent()
            world.root().isLeaf()
            world.root().isFullyTransparent()
        and: 'Nothing has been generated yet.'
            !world.isGenerated(VecF64.zero())
    }

    def "A hand-built world (no generator) never generates and reports nothing generated."()
    {
        given:
            var world = World.of(BoundsF64.cube(VecF64.zero(), 128)).createCamera(CAMERA, cameraAt(VecF64.of(0, -50, 0)))
        when:
            var updated = world.update(EngineInputs.of(0.0))
        then:
            world.generator().isEmpty()
            updated == world
            !updated.isGenerated(VecF64.of(0, -50, 0))
    }

    def "Update builds solid terrain around a camera, leaving distant space untouched."()
    {
        given: 'A camera deep underground (everything well below the surface band is rock).'
            var built = builtAround(VecF64.of(0, -50, 0), 20)
        expect: 'The chunk around the camera is generated, and the voxel there is solid rock...'
            built.isGenerated(VecF64.of(0, -50, 0))
            built.sectorAt(VecF64.of(0, -50, 0)).get().isSolidOpaque()
        and: '...while a far-away point was never generated.'
            !built.isGenerated(VecF64.of(1000, -50, 1000))
            built.sectorAt(VecF64.of(1000, -50, 1000)).isEmpty()
    }

    def "The world grows its root outward to follow a camera far from the origin."()
    {
        given: 'A camera thousands of units from the origin - far outside the initial one-chunk root.'
            var far = VecF64.of(5000, -50, 5000)
        expect: 'A fresh generator world is tiny (a single 64-unit chunk that cannot hold the far point).'
            !World.of(generator(64)).root().bounds().contains(far)
        when:
            var built = builtAround(far, 64)
        then: 'The root has grown to contain the far region, which is now generated solid rock.'
            built.root().bounds().contains(far)
            built.root().bounds().width() > 64
            built.isGenerated(far)
            built.sectorAt(far).get().isSolidOpaque()
    }

    def "A larger reach generates terrain a smaller reach does not."()
    {
        given: 'A point ~150 units from the camera: out of a reach of 20, within a reach of 200.'
            var probe = VecF64.of(150, -50, 0)
        expect:
            !builtAround(VecF64.of(0, -50, 0), 20).isGenerated(probe)
            builtAround(VecF64.of(0, -50, 0), 200).isGenerated(probe)
    }

    def "Generation is idempotent: re-updating with the camera unmoved changes nothing."()
    {
        given:
            var once = builtAround(VecF64.of(0, -50, 0), 96)
        when:
            var twice = once.update(EngineInputs.of(0.0))
        then:
            twice == once
    }
}