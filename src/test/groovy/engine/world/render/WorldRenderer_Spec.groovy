package engine.world.render
import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.Side
import app.engine.world.Texture
import app.engine.world.TextureProfile
import app.engine.world.World
import app.engine.world.WorldSectorEtherData
import app.engine.world.gen.WorldGenerator
import app.engine.world.render.TexturePalette
import app.engine.world.render.WorldRenderer
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

import java.awt.image.BufferedImage

@Title("WorldRenderer - the level-of-detail decision")
@Narrative('''

    The renderer chooses how deep to descend the tree based on how big a sector
    would appear on screen. That decision is a pure function of edge length,
    distance and focal length, so we can test it directly without any drawing.

''')
class WorldRenderer_Spec extends Specification
{
    def "Focal length follows the camera's field of view and viewport height."()
    {
        given: 'A 90 degree vertical fov over a 100px tall viewport.'
            var camera = new CameraF64(VecF64.of(0, 0, 10), VecF64.zero(), VecF64.of(0, 1, 0),
                                       Math.toRadians(90), 1.0, 0.1, 100)
        expect: 'focal = (height/2) / tan(fov/2) = 50 / tan(45) = 50.'
            Math.abs(WorldRenderer.focalLengthPx(camera, 100) - 50) < 1e-9
    }

    def "Projected size shrinks with distance and grows with edge length."()
    {
        expect:
            WorldRenderer.projectedEdgePixels(10, 100, 50) == 5
        and: 'Twice as far away appears half as big.'
            WorldRenderer.projectedEdgePixels(10, 200, 50) == 2.5
        and: 'Twice as large appears twice as big.'
            WorldRenderer.projectedEdgePixels(20, 100, 50) == 10
    }

    def "A sector at or behind the camera is treated as infinitely large (always refine)."()
    {
        expect:
            WorldRenderer.projectedEdgePixels(10, 0, 50) == Double.POSITIVE_INFINITY
            WorldRenderer.projectedEdgePixels(10, -5, 50) == Double.POSITIVE_INFINITY
    }

    def "Rendering a generated world actually draws terrain, not just sky."()
    {
        given: 'A small generated world viewed by a camera looking down at the surface.'
            int w = 240, h = 160
            var skyColor = new java.awt.Color(135, 180, 235)
            var world = World.of(WorldGenerator.withSeed(1337L).generate(BoundsF64.cube(VecF64.zero(), 128), 2))
            var camera = new CameraF64(VecF64.of(96, 40, 96), VecF64.zero(), VecF64.of(0, 1, 0),
                                       Math.toRadians(60), (double) w / h, 0.5, 2000)
            var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            var g = image.createGraphics()
        when:
            new WorldRenderer(28.0, VecF64.of(-0.4, -1.0, -0.3), skyColor).render(g, world, camera, w, h)
            g.dispose()
        then: 'A meaningful fraction of pixels differ from the sky colour (terrain was drawn).'
            countNonSky(image, skyColor) > (w * h) * 0.05
    }

    def "A camera looking away from the world draws only sky (frustum culling)."()
    {
        given: 'The same world, but the camera faces away from it so nothing is in view.'
            int w = 240, h = 160
            var skyColor = new java.awt.Color(135, 180, 235)
            var world = World.of(WorldGenerator.withSeed(1337L).generate(BoundsF64.cube(VecF64.zero(), 128), 2))
            var lookingAway = new CameraF64(VecF64.of(96, 40, 96), VecF64.of(300, 40, 300), VecF64.of(0, 1, 0),
                                            Math.toRadians(60), (double) w / h, 0.5, 2000)
            var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            var g = image.createGraphics()
        when:
            new WorldRenderer(28.0, VecF64.of(-0.4, -1.0, -0.3), skyColor).render(g, world, lookingAway, w, h)
            g.dispose()
        then: 'The whole world is behind the camera, so every sector is culled and nothing is drawn.'
            countNonSky(image, skyColor) == 0
    }

    def "An invisible face of an otherwise-opaque LoD cube falls back to the combined appearance (no holes)."()
    {
        given: 'An LoD ether whose top aggregated to near-invisible, with solid rock elsewhere.'
            var ether = WorldSectorEtherData.of(Material.ROCK)
                                .withSide(Side.POS_Y, TextureProfile.of(Texture.OPACITY, 0.1))
        expect: 'A solid side keeps its own appearance...'
            WorldRenderer.faceProfile(ether, Side.NEG_Y).intensityOf(Texture.OPACITY) == 1.0
        and: '...while the near-invisible top falls back to the combined profile instead of being skipped.'
            !ether.sideOf(Side.POS_Y).isInvisible()
            WorldRenderer.faceProfile(ether, Side.POS_Y).intensityOf(Texture.OPACITY) > TexturePalette.VISIBILITY_THRESHOLD
    }

    def "Majority-opaque decides whether a sector is drawn at all."()
    {
        expect: 'A solid rock cube is drawn...'
            WorldRenderer.isMajorityOpaque(WorldSectorEtherData.of(Material.ROCK))
        and: '...empty air is not.'
            !WorldRenderer.isMajorityOpaque(WorldSectorEtherData.empty())
    }

    def "The texture palette derives a plausible colour from appearance qualities."()
    {
        expect: 'Grass reads green-dominant, water blue-dominant.'
            var grass = TexturePalette.colorOf(Material.GRASS.texture())
            var water = TexturePalette.colorOf(Material.WATER.texture())
            grass.green > grass.red && grass.green > grass.blue
            water.blue > water.red && water.blue > water.green
    }

    private static int countNonSky( BufferedImage image, java.awt.Color skyColor ) {
        int nonSky = 0
        for ( int y = 0; y < image.height; y++ )
            for ( int x = 0; x < image.width; x++ )
                if ( (image.getRGB(x, y) & 0xFFFFFF) != (skyColor.getRGB() & 0xFFFFFF) )
                    nonSky++
        return nonSky
    }
}