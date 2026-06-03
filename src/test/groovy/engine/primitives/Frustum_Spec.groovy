package engine.primitives

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.Frustum
import app.engine.primitives.VecF64
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Frustum - the six culling planes of a view volume")
@Narrative('''

    A frustum is extracted from a camera's view-projection matrix. It can cheaply
    tell whether an axis-aligned box might be visible, so a tree walk can prune
    any sector (and its whole sub-tree) that lies entirely outside the view.

''')
class Frustum_Spec extends Specification
{
    // Sits at +Z, looks towards the origin (i.e. facing -Z). near 0.5, far 100,
    // so the far plane is at z = 10 - 100 = -90.
    private static CameraF64 camera() {
        return new CameraF64(
                VecF64.of(0, 0, 10), VecF64.zero(), VecF64.of(0, 1, 0),
                Math.toRadians(60), 1.0, 0.5, 100
            )
    }

    private static BoundsF64 boxAt( double x, double y, double z, double size = 2 ) {
        return BoundsF64.cube(VecF64.of(x, y, z), size)
    }

    def "A box in front of the camera, near the view centre, is kept."()
    {
        given:
            var frustum = camera().frustum()
        expect:
            frustum.intersects(boxAt(0, 0, 0))
            frustum.intersects(boxAt(0, 0, -50))
    }

    def "Boxes outside the view volume are culled."()
    {
        given:
            var frustum = camera().frustum()
        expect: 'Behind the camera...'
            !frustum.intersects(boxAt(0, 0, 500))
        and: '...far off to the side...'
            !frustum.intersects(boxAt(1000, 0, 0))
        and: '...and beyond the far plane.'
            !frustum.intersects(boxAt(0, 0, -200))
    }

    def "Containment of points mirrors the culling test."()
    {
        given:
            var frustum = camera().frustum()
        expect:
            frustum.contains(VecF64.zero())
            !frustum.contains(VecF64.of(0, 0, 100)) // behind the camera
    }

    def "Culling never rejects a box that straddles a frustum plane (conservative)."()
    {
        given: 'A huge box around the origin clearly overlaps the view volume.'
            var frustum = camera().frustum()
        expect:
            frustum.intersects(BoundsF64.cube(VecF64.zero(), 400))
    }

    def "The camera's cached frustum matches one built from its view-projection matrix."()
    {
        given:
            var camera = camera()
            var explicit = Frustum.of(camera.viewProjectionMatrix())
        expect: 'Both agree on visibility for a sample of boxes.'
            [boxAt(0, 0, 0), boxAt(0, 0, 500), boxAt(1000, 0, 0)].every {
                camera.frustum().intersects(it) == explicit.intersects(it)
            }
    }
}