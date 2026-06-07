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

@Title("World - infinite level-of-detail generation around cameras")
@Narrative('''

    A generator-backed world is one continuous level-of-detail octree. It starts as a single large
    coarse sector (described top-down by the generator, no sub-tree) and, on each update, refines
    detail toward every camera - full voxel chunks up close, progressively coarser sectors outward -
    collapsing detail back to coarse where a camera has moved away. So the world is effectively
    infinite and its memory is bounded by the cone of detail around the cameras, not by distance
    travelled. Refinement is budgeted, so these specs drive the world to a settled state before
    asserting; "how refined is point p" is read from the bounds size of sectorAt(p).

''')
class WorldGeneration_Spec extends Specification
{
    private static final long CAMERA = 1L
    private static final double CHUNK = 64d

    // Caves disabled (caveThreshold 2.0) and a very low sea level, so anything well below the
    // surface band is solid rock. chunkSize 64, detailDepth 1; generationDistance = the full-detail reach.
    private static WorldGenerator generator( double reach ) {
        return new WorldGenerator(new PerlinNoise(1L), -1000d, 0d, 24d, 0.015d, 6d, 1.5d, 2.0d, reach, 1, CHUNK)
    }

    private static CameraF64 cameraAt( VecF64 p ) {
        return new CameraF64(p, p.add(VecF64.of(0, 0, 1)), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
    }

    /** Drives the world with the cameras unmoved until refinement settles (no instance change). */
    private static World settled( World world ) {
        World previous
        do {
            previous = world
            world = world.update(EngineInputs.of(0.0))
        } while ( !world.is(previous) )
        return world
    }

    private static World builtAround( VecF64 eye, double reach ) {
        return settled(World.of(generator(reach)).createCamera(CAMERA, cameraAt(eye)))
    }

    /** How refined point {@code p} is: the world-space width of the deepest sector there (smaller = finer). */
    private static double detailWidth( World world, VecF64 p ) {
        return world.sectorAt(p).map { it.bounds().width() }.orElse(Double.POSITIVE_INFINITY)
    }

    def "A generator-backed world starts as a single large coarse sector."()
    {
        given:
            var world = World.of(generator(20))
        expect:
            world.generator().isPresent()
            world.root().isLeaf()
        and: 'The root is one big coarse sector covering kilometres, not a tiny empty chunk...'
            world.root().bounds().width() > 1000
        and: '...and it is real terrain (straddles the surface), not empty void.'
            !world.root().isVoid()
    }

    def "A hand-built world (no generator) never refines and reports no generator."()
    {
        given:
            var world = World.of(BoundsF64.cube(VecF64.zero(), 128)).createCamera(CAMERA, cameraAt(VecF64.of(0, -50, 0)))
        when:
            var updated = world.update(EngineInputs.of(0.0))
        then:
            world.generator().isEmpty()
            updated == world
    }

    def "Update refines full voxel detail around a camera, leaving distant terrain coarse."()
    {
        given: 'A camera deep underground at the origin (everything well below the surface is rock).'
            var built = builtAround(VecF64.of(0, -50, 0), 20)
        expect: 'The sector at the camera is refined down to chunk detail, and is solid rock...'
            detailWidth(built, VecF64.of(0, -50, 0)) <= CHUNK
            built.sectorAt(VecF64.of(0, -50, 0)).get().isSolidOpaque()
        and: '...while a far-away location is left coarse (a much larger sector).'
            detailWidth(built, VecF64.of(3000, -50, 3000)) >= 512
    }

    def "A larger reach refines detail further out than a small one does."()
    {
        given: 'A surface probe ~150 units from a camera at the origin (where coarse vs fine is visible).'
            var probe = VecF64.of(150, 0, 0)
        expect: 'A small reach leaves the probe coarse; a large reach refines it to finer detail.'
            detailWidth(builtAround(VecF64.of(0, 0, 0), 200), probe) < detailWidth(builtAround(VecF64.of(0, 0, 0), 20), probe)
    }

    def "Refinement is idempotent: re-updating with the camera unmoved changes nothing."()
    {
        given:
            var once = builtAround(VecF64.of(0, -50, 0), 96)
        when:
            var twice = once.update(EngineInputs.of(0.0))
        then:
            twice == once
    }

    def "Detail collapses behind a moved camera and re-refines losslessly on return."()
    {
        given: 'Detail refined around a camera deep underground at the origin.'
            var origin = VecF64.of(0, -50, 0)
            var world = builtAround(origin, 96)
        expect:
            detailWidth(world, origin) <= CHUNK
            world.sectorAt(origin).get().isSolidOpaque()

        when: 'The camera flies far away and the world settles there.'
            var far = VecF64.of(8000, -50, 8000)
            world = settled(world.createCamera(CAMERA, cameraAt(far)))
        then: 'The far terrain is now refined solid rock...'
            detailWidth(world, far) <= CHUNK
            world.sectorAt(far).get().isSolidOpaque()
        and: '...while the origin, now far behind, has collapsed back to a coarse sector.'
            detailWidth(world, origin) >= 512

        when: 'The camera returns to the origin and the world settles again.'
            world = settled(world.createCamera(CAMERA, cameraAt(origin)))
        then: 'The origin is re-refined to full detail - the collapse was lossless.'
            detailWidth(world, origin) <= CHUNK
            world.sectorAt(origin).get().isSolidOpaque()
    }

    def "The world grows its root outward to follow a camera far beyond it."()
    {
        given: 'A camera tens of thousands of units away - far outside the initial coarse root.'
            var far = VecF64.of(50000, -50, 50000)
        expect: 'A fresh world does not yet contain the far point.'
            !World.of(generator(96)).root().bounds().contains(far)
        when:
            var built = builtAround(far, 96)
        then: 'The root has grown to contain it, and detail is refined there.'
            built.root().bounds().contains(far)
            detailWidth(built, far) <= CHUNK
            built.sectorAt(far).get().isSolidOpaque()
    }
}