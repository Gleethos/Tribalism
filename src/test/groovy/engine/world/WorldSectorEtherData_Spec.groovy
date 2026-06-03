package engine.world

import app.engine.world.Material
import app.engine.world.WorldSectorEtherData
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("WorldSectorEtherData - what a sector is made of")
@Narrative('''

    A sector stores a mixture of material fractions, e.g. 90% air, 5% soil,
    5% rock. This drives rendering and, crucially, the level-of-detail
    aggregation where a parent averages the materials of its children.

''')
class WorldSectorEtherData_Spec extends Specification
{
    def "An empty sector reports zero for every material and is dominated by air."()
    {
        given:
            var ether = WorldSectorEtherData.empty()
        expect:
            ether.fractionOf(Material.ROCK) == 0
            ether.total() == 0
            ether.dominantMaterial() == Material.AIR
    }

    def "A mixture exposes its fractions and dominant material."()
    {
        given:
            var ether = WorldSectorEtherData.empty()
                                .with(Material.AIR, 0.90)
                                .with(Material.SOIL, 0.05)
                                .with(Material.ROCK, 0.05)
        expect:
            ether.fractionOf(Material.AIR) == 0.90
            Math.abs(ether.total() - 1.0) < 1e-12
            ether.dominantMaterial() == Material.AIR
    }

    def "Normalizing rescales the fractions so they sum to one."()
    {
        given:
            var ether = WorldSectorEtherData.empty()
                                .with(Material.SOIL, 2)
                                .with(Material.ROCK, 2)
        when:
            var normalized = ether.normalized()
        then:
            Math.abs(normalized.total() - 1.0) < 1e-12
            Math.abs(normalized.fractionOf(Material.SOIL) - 0.5) < 1e-12
    }

    def "Averaging children is the core of level-of-detail aggregation."()
    {
        given: 'One child is all rock, the other is all air.'
            var rock = WorldSectorEtherData.of(Material.ROCK)
            var air = WorldSectorEtherData.of(Material.AIR)
        when: 'We average them, as a parent voxel would.'
            var averaged = WorldSectorEtherData.average([rock, air, air, air])
        then: 'The parent is a quarter rock, three quarters air.'
            Math.abs(averaged.fractionOf(Material.ROCK) - 0.25) < 1e-12
            Math.abs(averaged.fractionOf(Material.AIR) - 0.75) < 1e-12
            averaged.dominantMaterial() == Material.AIR
    }

    def "Averaging no samples yields empty ether data."()
    {
        expect:
            WorldSectorEtherData.average([]) == WorldSectorEtherData.empty()
    }

    def "A negative fraction is rejected."()
    {
        when:
            WorldSectorEtherData.empty().with(Material.ROCK, -0.1)
        then:
            thrown(IllegalArgumentException)
    }
}