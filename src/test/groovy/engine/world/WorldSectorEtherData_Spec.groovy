package engine.world

import app.engine.world.Material
import app.engine.world.MaterialId
import app.engine.world.Side
import app.engine.world.Texture
import app.engine.world.TextureProfile
import app.engine.world.WorldSectorEtherData
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("WorldSectorEtherData - a sector's material (one per cube) and per-side appearance")
@Narrative('''

    The "ether" of a sector carries one gameplay material id for the whole cube,
    plus a texture profile per face (appearance is per side, since only the outer
    faces are seen). Empty space is the air material with an invisible profile.

''')
class WorldSectorEtherData_Spec extends Specification
{
    def "Empty ether is the air material, invisible on all six sides."()
    {
        given:
            var ether = WorldSectorEtherData.empty()
        expect:
            ether.material() == MaterialId.of(0)
            Side.values().every { ether.sideOf(it).isInvisible() }
    }

    def "A material gives one id for the cube and its appearance on every side."()
    {
        given:
            var ether = WorldSectorEtherData.of(Material.ROCK)
        expect: 'A single material id for the whole cube...'
            ether.material() == Material.ROCK.materialId()
        and: '...and the material appearance on each face.'
            Side.values().every { ether.sideOf(it).intensityOf(Texture.OPACITY) == 1.0 }
    }

    def "Each side can carry its own appearance while the cube keeps one material."()
    {
        given: 'A block whose top reads mossy, the rest as plain rock.'
            var ether = WorldSectorEtherData.of(Material.ROCK)
                                .withSide(Side.POS_Y, TextureProfile.of(Texture.MOSSY, 1.0))
        expect:
            ether.material() == Material.ROCK.materialId()
            ether.sideOf(Side.POS_Y).intensityOf(Texture.MOSSY) == 1.0
            ether.sideOf(Side.NEG_Y).intensityOf(Texture.MOSSY) == 0.0
        and: 'The combined view averages all six side profiles.'
            ether.combined().intensityOf(Texture.MOSSY) > 0
    }

    def "The whole-cube material can be replaced (e.g. to Diverse on aggregation)."()
    {
        given:
            var ether = WorldSectorEtherData.of(Material.ROCK)
        when:
            var diverse = ether.withMaterial(MaterialId.diverse())
        then:
            diverse.material().isDiverse()
        and: 'Replacing the material leaves the per-side appearance untouched.'
            diverse.sideOf(Side.POS_X) == ether.sideOf(Side.POS_X)
    }
}