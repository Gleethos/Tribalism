package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.LightSource
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("LightSource - lights as a sum type")
@Narrative('''

    A light is a simple shape (sphere, cube or plane) plus an id, an intensity
    and a colour. As a sealed sum type it can be switched over exhaustively, and
    every variant exposes a position and bounds so the engine can place it in the
    world tree.

''')
class LightSource_Spec extends Specification
{
    def "Every light variant exposes a position and bounds for tree placement."()
    {
        expect:
            light.position() == expectedPosition
            light.bounds().contains(light.position())
            light.intensity() >= 0
        where:
            light << [
                new LightSource.Sphere(1L, VecF64.of(1, 2, 3), 2.0, 5.0, VecF64.of(1, 1, 1)),
                new LightSource.Cube(2L, BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(4, 4, 4)), 3.0, VecF64.of(1, 0, 0)),
                new LightSource.Plane(3L, VecF64.of(5, 5, 5), VecF64.of(0, 1, 0), 1.0, 2.0, VecF64.of(0, 0, 1))
            ]
            expectedPosition << [
                VecF64.of(1, 2, 3),
                VecF64.of(2, 2, 2),
                VecF64.of(5, 5, 5)
            ]
    }

    def "A sphere light's bounds enclose its full radius."()
    {
        given:
            var light = new LightSource.Sphere(1L, VecF64.of(0, 0, 0), 3.0, 1.0, VecF64.of(1, 1, 1))
        expect:
            light.bounds() == BoundsF64.of(VecF64.of(-3, -3, -3), VecF64.of(3, 3, 3))
    }

    def "Lights can be matched exhaustively as a sum type."()
    {
        given:
            LightSource light = new LightSource.Cube(7L, BoundsF64.of(VecF64.zero(), VecF64.one()), 1.0, VecF64.one())
        when: 'Groovy matches each case on the runtime class of the light.'
            var description = switch (light) {
                case LightSource.Sphere -> "sphere"
                case LightSource.Cube   -> "cube"
                case LightSource.Plane  -> "plane"
                default                 -> "unknown"
            }
        then:
            description == "cube"
    }

    def "A negative intensity or radius is rejected."()
    {
        when:
            new LightSource.Sphere(1L, VecF64.zero(), radius, intensity, VecF64.one())
        then:
            thrown(IllegalArgumentException)
        where:
            radius | intensity
            -1.0   | 1.0
            1.0    | -1.0
    }
}