package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Side
import app.engine.world.SideInsets
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("SideInsets - how far content is recessed from each face")
@Narrative('''

    Side insets are per-face fractions in [0,1] of a sector's extent. They let a
    coarse LoD box shrink to fit its content. This spec covers the value type
    itself: clamping, lookup, and shrinking a bounding box.

''')
class SideInsets_Spec extends Specification
{
    private static BoundsF64 cube( double from, double to ) {
        return BoundsF64.of(VecF64.of(from, from, from), VecF64.of(to, to, to))
    }

    def "The 'none' insets are zero on every face and shrink nothing."()
    {
        given:
            var none = SideInsets.none()
        expect:
            none.isNone()
            Side.values().every { none.forSide(it) == 0.0 }
            none.shrink(cube(0, 8)) == cube(0, 8)
    }

    def "Insets are clamped into [0,1]."()
    {
        expect:
            SideInsets.none().with(Side.POS_Y, 2.5).forSide(Side.POS_Y) == 1.0
            SideInsets.none().with(Side.POS_Y, -1.0).forSide(Side.POS_Y) == 0.0
    }

    def "A single-face inset moves only that face inward by inset * extent."()
    {
        given: 'A cube spanning 0..8 (extent 8) on every axis.'
            var box = cube(0, 8)
        when:
            var shrunk = SideInsets.none().with(side, fraction).shrink(box)
        then: 'Only the inset face moves, by inset * extent.'
            onAxis(shrunk.min(), side.axis()) == (expMinAxis as double)
            onAxis(shrunk.max(), side.axis()) == (expMaxAxis as double)
        where:
            side       | fraction || expMinAxis | expMaxAxis
            Side.NEG_X | 0.25     || 2.0        | 8.0
            Side.POS_X | 0.25     || 0.0        | 6.0
            Side.NEG_Y | 0.5      || 4.0        | 8.0
            Side.POS_Y | 0.5      || 0.0        | 4.0
            Side.NEG_Z | 1.0      || 8.0        | 8.0
            Side.POS_Z | 0.125    || 0.0        | 7.0
    }

    def "Opposing insets that cross collapse to a zero-width slab, never an inverted box."()
    {
        given: 'Both Y faces inset by 0.8 (1.6 total > 1).'
            var insets = SideInsets.none().with(Side.NEG_Y, 0.8).with(Side.POS_Y, 0.8)
        when:
            var shrunk = insets.shrink(cube(0, 8))
        then: 'The Y axis collapses to the midpoint (4), still a valid box.'
            shrunk.min().y() == 4.0
            shrunk.max().y() == 4.0
            shrunk.min().y() <= shrunk.max().y()
    }

    private static double onAxis( VecF64 v, int axis ) {
        return axis == 0 ? v.x() : (axis == 1 ? v.y() : v.z())
    }
}