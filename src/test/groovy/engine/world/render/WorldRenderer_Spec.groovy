package engine.world.render
import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.World
import app.engine.world.gen.WorldGenerator
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
            int nonSky = 0
            for ( int y = 0; y < h; y++ )
                for ( int x = 0; x < w; x++ )
                    if ( (image.getRGB(x, y) & 0xFFFFFF) != (skyColor.getRGB() & 0xFFFFFF) )
                        nonSky++
            nonSky > (w * h) * 0.05
    }
}