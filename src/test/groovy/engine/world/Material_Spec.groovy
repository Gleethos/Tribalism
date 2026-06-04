package engine.world

import app.engine.world.Material
import app.engine.world.MaterialId
import app.engine.world.Texture
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Material - the starter substance registry")
@Narrative('''

    A material is a substance with a stable integer id, a name and a default
    appearance (texture profile). The registry is a record-backed starter set that
    can grow to thousands; air is the null substance (an empty, invisible profile).

''')
class Material_Spec extends Specification
{
    def "Air is the invisible null substance."()
    {
        expect:
            Material.AIR.texture().isInvisible()
            Material.AIR.materialId() == MaterialId.of(0)
    }

    def "Solid materials are opaque and carry their characteristic qualities."()
    {
        expect:
            Material.ROCK.texture().intensityOf(Texture.OPACITY) == 1.0
            Material.WATER.texture().intensityOf(Texture.LIQUID) == 1.0
            Material.GRASS.texture().intensityOf(Texture.HAIRY) > 0
    }

    def "Material ids are unique and resolvable through the registry."()
    {
        expect: 'No two starter materials share an id.'
            Material.all().collect { it.id() }.toSet().size() == Material.all().size()
        and: 'A material round-trips through its id.'
            Material.byId(Material.ROCK.id()) == Material.ROCK
        and: 'An unknown id falls back to air.'
            Material.byId(99999) == Material.AIR
    }
}