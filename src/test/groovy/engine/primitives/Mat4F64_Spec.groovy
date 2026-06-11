package engine.primitives

import app.engine.primitives.Mat4F64
import app.engine.primitives.VecF64
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Mat4F64 - the 4x4 transform primitive")
@Narrative('''

    Camera view and projection transforms are built from immutable 4x4 matrices.
    The trickiest piece is the general matrix inverse, so we verify it the robust
    way: a matrix multiplied by its inverse must yield the identity.

''')
class Mat4F64_Spec extends Specification
{
    private static boolean approxIdentity( Mat4F64 m ) {
        for ( int r = 0; r < 4; r++ )
            for ( int c = 0; c < 4; c++ ) {
                double expected = (r == c) ? 1 : 0
                if ( Math.abs(m.get(r, c) - expected) > 1e-9 )
                    return false
            }
        return true
    }

    def "The matrix is immutable - set returns a new matrix and leaves the original untouched."()
    {
        given:
            var original = Mat4F64.identity()
        when: 'We derive a changed matrix and also try to corrupt the leaked array.'
            var changed = original.set(0, 0, 42)
            var leaked = original.data()
            leaked[0] = 999
        then: 'The original still reads as identity (no aliasing through set or data()).'
            original.get(0, 0) == 1
        and: 'The returned matrix carries the change.'
            changed.get(0, 0) == 42
    }

    def "Translation moves a point and rotation preserves length."()
    {
        given:
            var t = Mat4F64.translation(VecF64.of(10, 20, 30))
        expect:
            t.transformPoint(VecF64.of(1, 2, 3)) == VecF64.of(11, 22, 33)
        and: 'A 90 degree rotation about Z maps +X onto +Y (within tolerance).'
            var r = Mat4F64.rotationZ(Math.PI / 2)
            var rotated = r.transformPoint(VecF64.of(1, 0, 0))
            Math.abs(rotated.x() - 0) < 1e-9
            Math.abs(rotated.y() - 1) < 1e-9
    }

    def "A direction ignores translation."()
    {
        given:
            var t = Mat4F64.translation(VecF64.of(10, 20, 30))
        expect:
            t.transformDirection(VecF64.of(1, 0, 0)) == VecF64.of(1, 0, 0)
    }

    def "A matrix multiplied by its inverse yields the identity."()
    {
        expect: 'This validates the general 4x4 inverse across several transforms.'
            approxIdentity(m.mul(m.inverse()))
            approxIdentity(m.inverse().mul(m))
        where:
            m << [
                Mat4F64.translation(VecF64.of(3, -5, 7)),
                Mat4F64.scale(VecF64.of(2, 4, 0.5)),
                Mat4F64.rotationX(0.7),
                Mat4F64.rotationY(-1.2),
                Mat4F64.rotationZ(2.1),
                Mat4F64.perspective(Math.toRadians(60), 16 / 9d, 0.1, 1000),
                Mat4F64.translation(VecF64.of(1, 2, 3)).mul(Mat4F64.rotationY(0.5)).mul(Mat4F64.scale(VecF64.of(2, 2, 2)))
            ]
    }

    def "A singular matrix cannot be inverted."()
    {
        given: 'A scale-to-zero matrix collapses a dimension and is non-invertible.'
            var singular = Mat4F64.scale(VecF64.of(1, 0, 1))
        when:
            singular.inverse()
        then:
            thrown(IllegalStateException)
    }

    def "Identity is the neutral element of multiplication."()
    {
        given:
            var m = Mat4F64.translation(VecF64.of(5, 6, 7))
        expect:
            m.mul(Mat4F64.identity()) == m
            Mat4F64.identity().mul(m) == m
    }

    def "The reversed-Z infinite projection maps the near plane to depth 1 and the far field towards 0."()
    {
        given: 'A reversed projection and view-space points along the forward (-z) axis.'
            var p = Mat4F64.perspectiveReversedInfinite(Math.toRadians(60), 1.0, 2.0)
            var depthAt = { double viewZ ->
                double clipZ = p.get(2, 2) * viewZ + p.get(2, 3)
                double clipW = p.get(3, 2) * viewZ
                return clipZ / clipW
            }
        expect: 'Depth is 1 exactly at the near plane...'
            Math.abs(depthAt(-2.0) - 1.0) < 1e-12
        and: '...falls monotonically with distance...'
            depthAt(-10.0) > depthAt(-100.0)
            depthAt(-100.0) > depthAt(-100000.0)
        and: '...approaching (but never reaching) 0 at extreme range - there is no far plane.'
            depthAt(-1.0e12) > 0.0
            depthAt(-1.0e12) < 1e-10
        and: 'The x/y rows match the classic projection (only depth changes).'
            var classic = Mat4F64.perspective(Math.toRadians(60), 1.0, 2.0, 1000.0)
            p.get(0, 0) == classic.get(0, 0)
            p.get(1, 1) == classic.get(1, 1)
    }
}