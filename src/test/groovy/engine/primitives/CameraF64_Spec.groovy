package engine.primitives

import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("CameraF64 - a viewpoint as pure data")
@Narrative('''

    A camera is just data describing a viewpoint and a frustum. From that data it
    derives its view and projection matrices. Rendering stays a separate, pluggable
    function of this state.

''')
class CameraF64_Spec extends Specification
{
    private static CameraF64 sampleCamera() {
        return new CameraF64(
                VecF64.of(0, 0, 10),   // position
                VecF64.of(0, 0, 0),    // target
                VecF64.of(0, 1, 0),    // up
                Math.toRadians(60),    // vertical fov
                16 / 9d,               // aspect
                0.1,                   // near
                1000                   // far
            )
    }

    def "The forward direction points from the camera towards its target."()
    {
        given:
            var camera = sampleCamera()
        expect: 'Looking from +Z towards the origin means facing -Z.'
            camera.forward() == VecF64.of(0, 0, -1)
    }

    def "Viewing its own position places the camera at the view-space origin."()
    {
        given:
            var camera = sampleCamera()
        when: 'We transform the camera position by its own view matrix.'
            var inView = camera.viewMatrix().transformPoint(camera.position())
        then: 'It lands on the origin of view space.'
            Math.abs(inView.x()) < 1e-9
            Math.abs(inView.y()) < 1e-9
            Math.abs(inView.z()) < 1e-9
    }

    def "Invalid frustum parameters are rejected."()
    {
        when:
            new CameraF64(VecF64.zero(), VecF64.of(0, 0, -1), VecF64.of(0, 1, 0), fov, aspect, near, far)
        then:
            thrown(IllegalArgumentException)
        where:
            fov               | aspect | near | far
            Math.toRadians(60)| 1.0    | -1   | 100   // negative near
            Math.toRadians(60)| 1.0    | 10   | 5     // far below near
            Math.toRadians(60)| 0      | 0.1  | 100   // zero aspect
            Math.PI           | 1.0    | 0.1  | 100   // degenerate fov
    }

    def "The 'with' builders change one field and keep value semantics."()
    {
        given:
            var camera = sampleCamera()
        expect:
            camera.withPosition(VecF64.of(1, 1, 1)).position() == VecF64.of(1, 1, 1)
            camera.withAspect(2.0).aspect() == 2.0
        and: 'Unrelated fields are preserved.'
            camera.withPosition(VecF64.of(1, 1, 1)).target() == camera.target()
    }
}