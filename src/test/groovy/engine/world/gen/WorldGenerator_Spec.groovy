package engine.world.gen

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.MaterialId
import app.engine.world.Side
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
        return new WorldGenerator(new PerlinNoise(42L), -1000d, 0d, 24d, 0.015d, 6d, 1.5d, 2.0d, 96d, 1, 64d)
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
            sector.ether().material() == Material.AIR.materialId()
    }

    def "A region entirely underground generates a single rock leaf."()
    {
        given:
            var gen = solidGenerator()
        when:
            var sector = gen.generate(cubeAround(VecF64.of(0, -200, 0), 16), 2)
        then:
            sector.isLeaf()
            sector.ether().material() == Material.ROCK.materialId()
    }

    def "A region straddling the surface is subdivided for detail."()
    {
        given:
            var gen = solidGenerator()
        when: 'This cube spans from well below to well above the surface.'
            var sector = gen.generate(cubeAround(VecF64.of(0, 0, 0), 64), 1)
        then:
            !sector.isLeaf()
            sector.children().size() == 512
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

    // ---- Top-down LoD description (etherOf) --------------------------------------

    def "etherOf is the faithful top-down summary: it equals a one-level bottom-up build."()
    {
        given: 'A generator and a region (homogeneous or straddling the surface).'
            var gen = solidGenerator()
        expect: 'Describing the region top-down matches aggregating a one-level build of it bottom-up.'
            gen.etherOf(region) == gen.generate(region, 1).ether()
        where:
            region << [
                    cubeAround(VecF64.of(0,    0, 0), 256), // straddles the surface (mixed)
                    cubeAround(VecF64.of(0, 2000, 0), 256), // entirely air
                    cubeAround(VecF64.of(0,-2000, 0), 256)  // entirely rock
            ]
    }

    def "etherOf describes a coarse region directly, without building a sub-tree."()
    {
        given: 'A large region straddling the surface (air well above, rock well below).'
            var gen = solidGenerator()
        when: 'We ask only for its ether - no WorldSector tree is built.'
            var ether = gen.etherOf(cubeAround(VecF64.of(0, 0, 0), 256))
        then: 'It mixes materials, so the whole-cube material is Diverse...'
            ether.material() == MaterialId.diverse()
        and: '...the bottom face reads as solid rock while the top is open air (directionally correct)...'
            ether.sideOf(Side.NEG_Y).isOpaque()
            ether.sideOf(Side.POS_Y).isInvisible()
        and: '...so opposite faces differ, exactly as the bottom-up aggregate would.'
            ether.sideOf(Side.NEG_Y) != ether.sideOf(Side.POS_Y)
    }

    def "A homogeneous region's top-down ether is a single uniform material."()
    {
        given:
            var gen = solidGenerator()
        when:
            var ether = gen.etherOf(cubeAround(VecF64.of(0, -2000, 0), 256))
        then: 'Solid rock throughout: a Specific material, opaque and identical on every face.'
            ether.material() == Material.ROCK.materialId()
            Side.values().every { ether.sideOf(it) == ether.sideOf(Side.POS_X) }
            ether.sideOf(Side.POS_X).isOpaque()
    }

    def "etherOf is deterministic for a given seed."()
    {
        given:
            var region = cubeAround(VecF64.of(0, 0, 0), 128)
        expect:
            WorldGenerator.withSeed(7L).etherOf(region) == WorldGenerator.withSeed(7L).etherOf(region)
    }

    // ---- Top-down LoD shape (per-side insets carried in the ether) ---------------

    def "The ether recesses the top of a surface-straddling region (air above) but not its solid bottom."()
    {
        given: 'A region from well below to well above the surface (surface height is ~0 here).'
            var gen = solidGenerator()
        when: 'Both the faithful and the cheap top-down summaries carry per-side insets.'
            var faithful = gen.etherOf(cubeAround(VecF64.of(0, 0, 0), 256))
            var coarse   = gen.representativeEtherOf(cubeAround(VecF64.of(0, 0, 0), 256))
        then: 'The top is recessed - empty air layers peel inward from +Y...'
            faithful.insetOf(Side.POS_Y) > 0
            coarse.insetOf(Side.POS_Y) > 0
        and: '...while the solid bottom and the (terrain-spanning) sides are flush.'
            faithful.insetOf(Side.NEG_Y) == 0
            faithful.insetOf(Side.NEG_X) == 0
            faithful.insetOf(Side.POS_X) == 0
        and: 'Both summaries agree on the shape (same per-side insets).'
            Side.values().every { faithful.insetOf(it) == coarse.insetOf(it) }
    }

    def "A homogeneous region has no insets (nothing is recessed)."()
    {
        given:
            var gen = solidGenerator()
        expect: 'Deep solid rock and high open air are both uniform: their content fills (or air is never drawn).'
            Side.values().every { gen.representativeEtherOf(cubeAround(VecF64.of(0, -2000, 0), 256)).insetOf(it) == 0 }
            Side.values().every { gen.representativeEtherOf(cubeAround(VecF64.of(0,  2000, 0), 256)).insetOf(it) == 0 }
    }

    def "The ether's insets are deterministic for a given seed."()
    {
        given:
            var region = cubeAround(VecF64.of(0, 0, 0), 128)
        expect:
            WorldGenerator.withSeed(7L).representativeEtherOf(region) == WorldGenerator.withSeed(7L).representativeEtherOf(region)
    }
}