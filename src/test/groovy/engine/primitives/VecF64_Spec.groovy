package engine.primitives

import app.engine.primitives.VecF64
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("VecF64 - the 64-bit vector primitive")
@Narrative('''

    The whole world engine simulates in 64-bit space, so the most fundamental
    primitive is `VecF64`, an immutable 3D vector of doubles. It needs the usual
    vector algebra, but just as importantly it needs proper value semantics, so
    that it can live inside records and the persistent Sprouts collections.

''')
class VecF64_Spec extends Specification
{
    def "Two vectors with equal components are value-equal and share a hash code."()
    {
        given:
            var a = VecF64.of(1, 2, 3)
            var b = VecF64.of(1, 2, 3)
            var c = VecF64.of(1, 2, 4)
        expect: 'Structural equality holds, which the Sprouts collections rely on.'
            a == b
            a.hashCode() == b.hashCode()
        and: 'Different components are not equal.'
            a != c
    }

    def "Basic arithmetic produces the expected vectors."()
    {
        given:
            var a = VecF64.of(1, 2, 3)
            var b = VecF64.of(4, 5, 6)
        expect:
            a.add(b) == VecF64.of(5, 7, 9)
            b.sub(a) == VecF64.of(3, 3, 3)
            a.mul(2) == VecF64.of(2, 4, 6)
            b.div(2) == VecF64.of(2, 2.5, 3)
    }

    def "The dot and cross products follow their definitions."()
    {
        given:
            var x = VecF64.of(1, 0, 0)
            var y = VecF64.of(0, 1, 0)
        expect: 'Orthogonal unit vectors have a zero dot product.'
            x.dot(y) == 0
        and: 'Their cross product is the third basis vector.'
            x.cross(y) == VecF64.of(0, 0, 1)
    }

    def "Length, lengthSquared and distance are consistent."()
    {
        given:
            var v = VecF64.of(3, 4, 0)
        expect:
            v.length() == 5
            v.lengthSquared() == 25
            VecF64.of(0, 0, 0).distance(v) == 5
            VecF64.of(0, 0, 0).distanceSquared(v) == 25
    }

    def "Normalizing yields a unit vector, and the zero vector stays zero."()
    {
        expect:
            Math.abs(VecF64.of(0, 0, 5).normalize().length() - 1) < 1e-12
        and: 'Normalizing zero must not produce NaN.'
            VecF64.of(0, 0, 0).normalize() == VecF64.zero()
    }

    def "Linear interpolation moves from one vector to the other."()
    {
        given:
            var a = VecF64.of(0, 0, 0)
            var b = VecF64.of(10, 20, 30)
        expect:
            a.lerp(b, 0) == a
            a.lerp(b, 1) == b
            a.lerp(b, 0.5) == VecF64.of(5, 10, 15)
    }
}