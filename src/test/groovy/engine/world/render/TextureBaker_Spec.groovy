package engine.world.render

import app.engine.world.Material
import app.engine.world.Texture
import app.engine.world.TextureProfile
import app.engine.world.render.TextureBaker
import spock.lang.Specification
import spock.lang.Title

@Title("TextureBaker - procedural appearance tiles")
class TextureBaker_Spec extends Specification
{
    def "Bakes a tile of the requested size, fully opaque."()
    {
        when:
            int[] tile = TextureBaker.bake(Material.ROCK.texture(), 16)
        then: 'It is size*size pixels...'
            tile.length == 16 * 16
        and: '...all fully opaque (alpha 0xFF).'
            tile.every { ((it >>> 24) & 0xFF) == 0xFF }
    }

    def "Baking is deterministic: the same profile always yields the same tile."()
    {
        expect:
            (TextureBaker.bake(Material.GRASS.texture(), 16) as List) == (TextureBaker.bake(Material.GRASS.texture(), 16) as List)
    }

    def "A textured profile produces variation, not a flat fill."()
    {
        when:
            int[] tile = TextureBaker.bake(Material.ROCK.texture(), 16)
        then: 'More than one distinct colour appears (the noise modulated the base).'
            (tile as Set).size() > 1
    }

    def "Different appearances bake to visibly different tiles."()
    {
        expect:
            (TextureBaker.bake(Material.GRASS.texture(), 16) as List) != (TextureBaker.bake(Material.SAND.texture(), 16) as List)
    }

    def "An invisible (air) profile still bakes without error."()
    {
        expect:
            TextureBaker.bake(TextureProfile.none(), 8).length == 8 * 8
    }
}