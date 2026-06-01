package engine.world.render

import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.render.WorldRenderer
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("WorldRenderer - the level-of-detail decision")
@Narrative('''

    The renderer chooses how deep to descend the tree based on how big a section
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

    def "A section at or behind the camera is treated as infinitely large (always refine)."()
    {
        expect:
            WorldRenderer.projectedEdgePixels(10, 0, 50) == Double.POSITIVE_INFINITY
            WorldRenderer.projectedEdgePixels(10, -5, 50) == Double.POSITIVE_INFINITY
    }
}