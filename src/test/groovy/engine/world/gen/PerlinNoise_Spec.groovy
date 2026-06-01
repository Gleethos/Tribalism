package engine.world.gen

import app.engine.world.gen.PerlinNoise
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("PerlinNoise - the deterministic signal behind generation")
@Narrative('''

    Procedural generation must be reproducible, so the noise is fully determined
    by its seed. It must also stay within a sane range so downstream height-field
    maths behaves.

''')
class PerlinNoise_Spec extends Specification
{
    def "The same seed always yields the same value."()
    {
        given:
            var a = new PerlinNoise(123L)
            var b = new PerlinNoise(123L)
        expect:
            a.noise(1.5, 2.5, 3.5) == b.noise(1.5, 2.5, 3.5)
            a.fbm(1.5, 2.5, 3.5, 5, 0.5, 2.0) == b.fbm(1.5, 2.5, 3.5, 5, 0.5, 2.0)
    }

    def "Different seeds generally produce different values."()
    {
        given:
            var a = new PerlinNoise(1L)
            var b = new PerlinNoise(2L)
        expect:
            a.noise(0.3, 0.7, 0.9) != b.noise(0.3, 0.7, 0.9)
    }

    def "Noise stays within roughly [-1, 1] across many samples."()
    {
        given:
            var noise = new PerlinNoise(99L)
            var random = new Random(7)
        expect:
            (1..2000).every {
                double v = noise.noise(random.nextDouble() * 50, random.nextDouble() * 50, random.nextDouble() * 50)
                v >= -1.05 && v <= 1.05
            }
    }

    def "Noise is zero on the integer lattice (a property of gradient noise)."()
    {
        given:
            var noise = new PerlinNoise(5L)
        expect:
            Math.abs(noise.noise(3, -2, 7)) < 1e-9
    }
}