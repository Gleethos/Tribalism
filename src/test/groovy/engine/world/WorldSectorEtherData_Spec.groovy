package engine.world

import app.engine.world.Material
import app.engine.world.MaterialDistribution
import app.engine.world.Side
import app.engine.world.WorldSectorEtherData
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("WorldSectorEtherData - what a sector is made of, per face")
@Narrative('''

    A sector stores a material distribution per cube face (six sides) rather
    than a single whole-sector mixture. Since materials are a visual property
    and only the outer faces of a cube are ever seen, level-of-detail
    aggregation summarizes each face from only the children on that face.

''')
class WorldSectorEtherData_Spec extends Specification
{
    def "Empty ether has empty, air-dominated distributions on all six sides."()
    {
        given:
            var ether = WorldSectorEtherData.empty()
        expect:
            Side.values().every { ether.sideOf(it).total() == 0 }
            Side.values().every { ether.sideOf(it).dominantMaterial() == Material.AIR }
            ether.dominantMaterial() == Material.AIR
    }

    def "Uniform ether carries the same distribution on every side."()
    {
        given:
            var ether = WorldSectorEtherData.of(Material.ROCK)
        expect:
            Side.values().every { ether.sideOf(it).dominantMaterial() == Material.ROCK }
            ether.dominantMaterial() == Material.ROCK
    }

    def "Each side can carry its own distribution."()
    {
        given: 'A sector that is grass on top, rock on the bottom, untouched elsewhere.'
            var ether = WorldSectorEtherData.empty()
                                .withSide(Side.POS_Y, MaterialDistribution.of(Material.GRASS))
                                .withSide(Side.NEG_Y, MaterialDistribution.of(Material.ROCK))
        expect:
            ether.sideOf(Side.POS_Y).dominantMaterial() == Material.GRASS
            ether.sideOf(Side.NEG_Y).dominantMaterial() == Material.ROCK
            ether.sideOf(Side.POS_X).dominantMaterial() == Material.AIR
        and: 'The combined view averages all six sides into one mixture.'
            ether.combined().fractionOf(Material.GRASS) > 0
            ether.combined().fractionOf(Material.ROCK) > 0
    }

    def "Replacing a side leaves the others untouched (value semantics)."()
    {
        given:
            var base = WorldSectorEtherData.of(Material.SOIL)
        when:
            var changed = base.withSide(Side.POS_Y, MaterialDistribution.of(Material.GRASS))
        then:
            changed.sideOf(Side.POS_Y).dominantMaterial() == Material.GRASS
            changed.sideOf(Side.NEG_Y).dominantMaterial() == Material.SOIL
            base.sideOf(Side.POS_Y).dominantMaterial() == Material.SOIL
    }
}