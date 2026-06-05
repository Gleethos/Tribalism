package engine.world

import app.engine.world.CoverageGrid
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("CoverageGrid - the screen-space 'already blocked' buffer for occlusion culling")
@Narrative('''

    Solid occluders mark the tiles their silhouette fully covers; a later sector is
    occluded only if its whole screen rectangle is inside covered tiles. Marking is
    conservative-inner and testing is conservative-outer, so culling never hides
    something visible.

''')
class CoverageGrid_Spec extends Specification
{
    // A square occluder covering [0,50] x [0,50] in screen space.
    private static double[][] square( double size ) {
        return [[0d, 0d], [size, 0d], [size, size], [0d, size]] as double[][]
    }

    def "An empty grid never reports anything on-screen as occluded."()
    {
        given:
            var grid = new CoverageGrid(100, 100, 10)
        expect:
            !grid.isOccluded(10, 10, 40, 40)
            !grid.isOccluded(0, 0, 100, 100)
    }

    def "Anything entirely off-screen counts as occluded (it cannot be visible)."()
    {
        given:
            var grid = new CoverageGrid(100, 100, 10)
        expect:
            grid.isOccluded(200, 200, 300, 300)
            grid.isOccluded(-50, -50, -10, -10)
    }

    def "After a solid occluder is marked, rectangles inside its silhouette are occluded."()
    {
        given:
            var grid = new CoverageGrid(100, 100, 10)
        when:
            grid.markOccluder(square(50))
        then:
            grid.isOccluded(minX, minY, maxX, maxY) == occluded
        where:
            minX | minY | maxX | maxY || occluded
            5    | 5    | 45   | 45   || true   // wholly inside the covered region
            0    | 0    | 50   | 50   || true   // exactly the covered region
            5    | 5    | 55   | 45   || false  // pokes past the covered region in x
            45   | 45   | 80   | 80   || false  // mostly outside the covered region
            60   | 60   | 90   | 90   || false  // entirely outside the covered region
    }

    def "Marking is conservative: tiles only partly inside the occluder are not covered."()
    {
        given: 'An occluder that does not align to tile boundaries.'
            var grid = new CoverageGrid(100, 100, 10)
        when: 'A 25-wide square: only tiles fully within [0,25] (i.e. up to x=20) get covered.'
            grid.markOccluder(square(25))
        then: 'A rectangle reaching into the partially-covered tile [20,30] is NOT occluded.'
            !grid.isOccluded(5, 5, 28, 28)
        and: 'But one staying within the fully-covered tiles is.'
            grid.isOccluded(2, 2, 19, 19)
    }
}