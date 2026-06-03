package engine.world

import app.engine.world.Material
import app.engine.world.MaterialDistribution
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("MaterialDistribution - a mixture of material fractions")
@Narrative('''

    A material distribution is the building block of a sector's ether: a mapping
    from material to its fraction, e.g. 90% air, 5% soil, 5% rock. A sector
    stores one of these per cube face, and level-of-detail aggregation averages
    the matching faces of a sub-tree into a parent.

''')
class MaterialDistribution_Spec extends Specification
{
    def "An empty distribution reports zero for every material and is dominated by air."()
    {
        given:
            var dist = MaterialDistribution.empty()
        expect:
            dist.fractionOf(Material.ROCK) == 0
            dist.total() == 0
            dist.dominantMaterial() == Material.AIR
    }

    def "A mixture exposes its fractions and dominant material."()
    {
        given:
            var dist = MaterialDistribution.empty()
                                .with(Material.AIR, 0.90)
                                .with(Material.SOIL, 0.05)
                                .with(Material.ROCK, 0.05)
        expect:
            dist.fractionOf(Material.AIR) == 0.90
            Math.abs(dist.total() - 1.0) < 1e-12
            dist.dominantMaterial() == Material.AIR
    }

    def "Normalizing rescales the fractions so they sum to one."()
    {
        given:
            var dist = MaterialDistribution.empty()
                                .with(Material.SOIL, 2)
                                .with(Material.ROCK, 2)
        when:
            var normalized = dist.normalized()
        then:
            Math.abs(normalized.total() - 1.0) < 1e-12
            Math.abs(normalized.fractionOf(Material.SOIL) - 0.5) < 1e-12
    }

    def "Averaging distributions is the core of level-of-detail aggregation."()
    {
        given: 'One sample is all rock, the others all air.'
            var rock = MaterialDistribution.of(Material.ROCK)
            var air = MaterialDistribution.of(Material.AIR)
        when: 'We average them, as a parent face would.'
            var averaged = MaterialDistribution.average([rock, air, air, air])
        then: 'The result is a quarter rock, three quarters air.'
            Math.abs(averaged.fractionOf(Material.ROCK) - 0.25) < 1e-12
            Math.abs(averaged.fractionOf(Material.AIR) - 0.75) < 1e-12
            averaged.dominantMaterial() == Material.AIR
    }

    def "Averaging no samples yields an empty distribution."()
    {
        expect:
            MaterialDistribution.average([]) == MaterialDistribution.empty()
    }

    def "A negative fraction is rejected."()
    {
        when:
            MaterialDistribution.empty().with(Material.ROCK, -0.1)
        then:
            thrown(IllegalArgumentException)
    }
}