package engine.primitives

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("BoundsF64 - the spatial backbone")
@Narrative('''

    Every world sector occupies a cubic `BoundsF64`. The recursive subdivision
    of the world tree is expressed entirely through this type, so its
    `subdivide` and containment operations have to be exactly right.

''')
class BoundsF64_Spec extends Specification
{
    def "A bounding box exposes its center, size and volume."()
    {
        given:
            var box = BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(2, 4, 6))
        expect:
            box.center() == VecF64.of(1, 2, 3)
            box.size() == VecF64.of(2, 4, 6)
            box.volume() == 2 * 4 * 6
    }

    def "Creating a box with a min corner exceeding the max corner is rejected."()
    {
        when:
            BoundsF64.of(VecF64.of(1, 0, 0), VecF64.of(0, 1, 1))
        then:
            thrown(IllegalArgumentException)
    }

    def "A cube factory builds an axis-aligned cube around a center."()
    {
        given:
            var cube = BoundsF64.cube(VecF64.of(0, 0, 0), 2)
        expect:
            cube.min() == VecF64.of(-1, -1, -1)
            cube.max() == VecF64.of(1, 1, 1)
            cube.isCube()
    }

    def "Containment distinguishes points, fully-enclosed boxes and overlaps."()
    {
        given:
            var outer = BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(10, 10, 10))
            var inner = BoundsF64.of(VecF64.of(2, 2, 2), VecF64.of(4, 4, 4))
            var overlapping = BoundsF64.of(VecF64.of(8, 8, 8), VecF64.of(12, 12, 12))
        expect:
            outer.contains(VecF64.of(5, 5, 5))
            !outer.contains(VecF64.of(11, 5, 5))
        and:
            outer.contains(inner)
            !outer.contains(overlapping)
        and:
            outer.intersects(overlapping)
            outer.intersects(inner)
    }

    def "Subdividing produces a perfect grid in x + y*d + z*d^2 order."()
    {
        given:
            var box = BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(8, 8, 8))
        when: 'We split into an 8x8x8 grid (as a world tree node does).'
            var cells = box.subdivide(8)
        then: 'There are exactly 512 unit cells.'
            cells.size() == 512
            cells.get(0).size() == VecF64.of(1, 1, 1)
        and: 'The very first cell sits at the min corner.'
            cells.get(0) == BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(1, 1, 1))
        and: 'Index 1 steps along x, index 8 steps along y, index 64 steps along z.'
            cells.get(1).min() == VecF64.of(1, 0, 0)
            cells.get(8).min() == VecF64.of(0, 1, 0)
            cells.get(64).min() == VecF64.of(0, 0, 1)
        and: 'The single-cell accessor agrees with the materialized grid.'
            box.child(1, 0, 0, 8) == cells.get(1)
            box.child(0, 0, 1, 8) == cells.get(64)
    }

    def "The union of two boxes encloses both."()
    {
        given:
            var a = BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(1, 1, 1))
            var b = BoundsF64.of(VecF64.of(2, 2, 2), VecF64.of(3, 3, 3))
        expect:
            a.union(b) == BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(3, 3, 3))
    }
}