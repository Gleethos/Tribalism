package engine.world

import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.CameraFlight
import app.engine.world.Key
import sprouts.ValueSet
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("CameraFlight - free-fly control as a pure function")
@Narrative('''

    The free-fly camera scheme (WASD move, Q/E/Space up-down, Shift sprint, mouse
    look) is a pure function lifted out of the demo into the engine. Given a camera,
    the held keys, accumulated cursor delta, world size and elapsed time it returns a
    moved camera - with no side effects - so it can be tested in isolation.

''')
class CameraFlight_Spec extends Specification
{
    private static final double EXTENT = 100.0   // world edge -> base speed = 100 * 0.6 = 60 / second
    private static final CameraF64 LOOKING_FORWARD =
            new CameraF64(VecF64.zero(), VecF64.of(0, 0, 1), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)

    private static ValueSet<Key> keys( Key... held ) {
        var set = ValueSet.of(Key.class)
        for ( Key k : held ) set = set.add(k)
        return set
    }

    def "With no keys held and no cursor movement the camera is returned unchanged."()
    {
        expect:
            CameraFlight.fly(LOOKING_FORWARD, keys(), 0, 0, EXTENT, 1.0).is(LOOKING_FORWARD)
    }

    def "Holding a movement key translates the camera; over one second it moves base-speed units."()
    {
        when:
            var moved = CameraFlight.fly(LOOKING_FORWARD, keys(key), 0, 0, EXTENT, 1.0).position()
        then: 'Forward is +Z here, so W/S move along Z and the up keys along Y, by 60 units in 1s.'
            Math.abs(moved.x() - expectX) < 1e-9
            Math.abs(moved.y() - expectY) < 1e-9
            Math.abs(moved.z() - expectZ) < 1e-9
        where:
            key       || expectX | expectY | expectZ
            Key.W     || 0.0     | 0.0     | 60.0
            Key.S     || 0.0     | 0.0     | -60.0
            Key.SPACE || 0.0     | 60.0    | 0.0
            Key.E     || 0.0     | 60.0    | 0.0
            Key.Q     || 0.0     | -60.0   | 0.0
    }

    def "Strafing left and right move in exactly opposite directions."()
    {
        when:
            var right = CameraFlight.fly(LOOKING_FORWARD, keys(Key.D), 0, 0, EXTENT, 1.0).position()
            var left  = CameraFlight.fly(LOOKING_FORWARD, keys(Key.A), 0, 0, EXTENT, 1.0).position()
        then: 'Both move purely horizontally, and one is the negation of the other.'
            Math.abs(right.y()) < 1e-9 && Math.abs(left.y()) < 1e-9
            Math.abs(right.x() + left.x()) < 1e-9
            Math.abs(right.z() + left.z()) < 1e-9
            right.lengthSquared() > 0
    }

    def "Sprinting with Shift triples the distance travelled."()
    {
        when:
            var walk   = CameraFlight.fly(LOOKING_FORWARD, keys(Key.W), 0, 0, EXTENT, 1.0).position()
            var sprint = CameraFlight.fly(LOOKING_FORWARD, keys(Key.W, Key.SHIFT), 0, 0, EXTENT, 1.0).position()
        then:
            Math.abs(sprint.z() - 3.0 * walk.z()) < 1e-9
    }

    def "Movement scales with elapsed time (frame-rate independence)."()
    {
        expect:
            Math.abs(CameraFlight.fly(LOOKING_FORWARD, keys(Key.W), 0, 0, EXTENT, 0.5).position().z() - 30.0) < 1e-9
    }

    def "Cursor movement rotates the view without moving the camera."()
    {
        when:
            var looked = CameraFlight.fly(LOOKING_FORWARD, keys(), 100, 0, EXTENT, 1.0)
        then: 'The position is unchanged...'
            looked.position() == VecF64.zero()
        and: '...but the look direction has turned away from straight ahead (+Z).'
            looked.forward().z() < 1.0 - 1e-6
    }
}