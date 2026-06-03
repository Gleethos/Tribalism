package engine.world.gen

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.gen.PerlinNoise
import app.engine.world.gen.WorldGenerator
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("WorldGenerator - adaptive procedural landscapes")
@Narrative('''

    The generator turns noise into a sector tree. Material is a height-field
    function of position, and - crucially - homogeneous regions collapse into a
    single leaf voxel so detail only appears around surfaces.

''')
class WorldGenerator_Spec extends Specification
{
    // A generator with caves disabled (caveThreshold > 1) for predictable structure.
    private static WorldGenerator solidGenerator() {
        return new WorldGenerator(new PerlinNoise(42L), -1000d, 0d, 24d, 0.015d, 6d, 1.5d, 2.0d)
    }

    private static BoundsF64 cubeAround( VecF64 center, double edge ) {
        return BoundsF64.cube(center, edge)
    }

    def "Material is air high above the surface and rock far below it."()
    {
        given:
            var gen = solidGenerator()
        expect:
            gen.materialAt(VecF64.of(0, 1000, 0)) == Material.AIR
            gen.materialAt(VecF64.of(0, -1000, 0)) == Material.ROCK
    }

    def "There is a grass band at the very surface."()
    {
        given: 'At x=z=0 the surface height is exactly the base level (noise is 0 on the lattice).'
            var gen = solidGenerator()
        expect:
            gen.surfaceHeightAt(0, 0) == 0
            gen.materialAt(VecF64.of(0, -0.5, 0)) == Material.GRASS
    }

    def "A region entirely above ground generates a single air leaf."()
    {
        given:
            var gen = solidGenerator()
        when:
            var sector = gen.generate(cubeAround(VecF64.of(0, 200, 0), 16), 2)
        then:
            sector.isLeaf()
            sector.ether().dominantMaterial() == Material.AIR
    }

    def "A region entirely underground generates a single rock leaf."()
    {
        given:
            var gen = solidGenerator()
        when:
            var sector = gen.generate(cubeAround(VecF64.of(0, -200, 0), 16), 2)
        then:
            sector.isLeaf()
            sector.ether().dominantMaterial() == Material.ROCK
    }

    def "A region straddling the surface is subdivided for detail."()
    {
        given:
            var gen = solidGenerator()
        when: 'This cube spans from well below to well above the surface.'
            var sector = gen.generate(cubeAround(VecF64.of(0, 0, 0), 64), 1)
        then:
            !sector.isLeaf()
            sector.children().sectors().size() == 512
    }

    def "Generation is reproducible for a given seed."()
    {
        given:
            var region = cubeAround(VecF64.of(0, 0, 0), 32)
        when:
            var first = WorldGenerator.withSeed(2024L).generate(region, 1)
            var second = WorldGenerator.withSeed(2024L).generate(region, 1)
        then:
            first == second
    }
}