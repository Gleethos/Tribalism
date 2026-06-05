package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.EngineInputs
import app.engine.world.Key
import app.engine.world.ScreenId
import app.engine.world.ScreenInputEvent
import app.engine.world.ScreenInputs
import app.engine.world.World
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("World.update - inputs become camera mutations")
@Narrative('''

    update(EngineInputs) folds one step of per-screen input into a new world. Key
    events change a screen's held-key state and, together with cursor movement, drive
    the bound camera via the fly controls. Because events only report changes, the
    world remembers held keys between updates: a key pressed once keeps moving the
    camera on later, event-less updates until it is released.

''')
class WorldUpdate_Spec extends Specification
{
    private static final ScreenId SCREEN = ScreenId.of(1L)
    private static final long CAMERA = 1L

    /** A world whose camera (id 1) sits at the origin looking down +Z, with a bound screen. */
    private static World flyingWorld() {
        var cam = new CameraF64(VecF64.zero(), VecF64.of(0, 0, 1), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
        return World.of(BoundsF64.cube(VecF64.zero(), 128))
                    .createCamera(CAMERA, cam)
                    .createScreen(SCREEN, 200, 200)
                    .bindScreenToCamera(SCREEN, CAMERA)
    }

    private static EngineInputs step( double dt, ScreenInputEvent... events ) {
        return EngineInputs.of(dt).withScreen(SCREEN, ScreenInputs.of(events))
    }

    private static VecF64 cameraPosition( World world ) {
        return world.camera(CAMERA).get().camera().position()
    }

    def "Pressing a movement key moves the bound camera on the next update."()
    {
        when:
            var world = flyingWorld().update(step(1.0, new ScreenInputEvent.KeyPressed(Key.W)))
        then: 'Forward is +Z, so the camera advanced along +Z (128*0.6*1 = 76.8 units).'
            cameraPosition(world).z() > 0
            Math.abs(cameraPosition(world).z() - 76.8) < 1e-6
    }

    def "A held key keeps moving the camera on later, event-less updates, until released."()
    {
        given: 'Press W and advance one step.'
            var world = flyingWorld().update(step(1.0, new ScreenInputEvent.KeyPressed(Key.W)))
            var afterFirst = cameraPosition(world).z()
        when: 'A second update with NO events at all.'
            world = world.update(step(1.0))
        then: 'The camera kept moving, because the world remembered W is still held.'
            cameraPosition(world).z() > afterFirst + 1e-6

        when: 'W is released, then another event-less update.'
            world = world.update(step(1.0, new ScreenInputEvent.KeyReleased(Key.W)))
            var afterRelease = cameraPosition(world).z()
            world = world.update(step(1.0))
        then: 'Motion has stopped.'
            Math.abs(cameraPosition(world).z() - afterRelease) < 1e-9
    }

    def "Cursor movement rotates the bound camera without moving it."()
    {
        when:
            var world = flyingWorld().update(step(1.0, new ScreenInputEvent.CursorMoved(app.engine.world.PointerId.MOUSE, 50, 0, 50, 0)))
        then:
            cameraPosition(world) == VecF64.zero()
            world.camera(CAMERA).get().camera().forward().z() < 1.0 - 1e-6
    }

    def "Larger time steps move the camera further."()
    {
        expect:
            cameraPosition(flyingWorld().update(step(1.0, new ScreenInputEvent.KeyPressed(Key.W)))).z() >
            cameraPosition(flyingWorld().update(step(0.5, new ScreenInputEvent.KeyPressed(Key.W)))).z()
    }

    def "Updating a screen bound to a missing camera is a harmless no-op."()
    {
        given: 'A screen bound to a camera id that was never created.'
            var world = World.of(BoundsF64.cube(VecF64.zero(), 128)).createScreen(SCREEN, 200, 200).bindScreenToCamera(SCREEN, 99L)
        when:
            var updated = world.update(step(1.0, new ScreenInputEvent.KeyPressed(Key.W)))
        then: 'No camera to mutate, so nothing throws and there is still no such camera.'
            updated.camera(99L).isEmpty()
    }

    def "Screens not mentioned in the inputs are left untouched."()
    {
        given:
            var world = flyingWorld()
            var before = cameraPosition(world)
        when: 'An update that only carries inputs for some OTHER screen.'
            var updated = world.update(EngineInputs.of(1.0).withScreen(ScreenId.of(42L), ScreenInputs.of(new ScreenInputEvent.KeyPressed(Key.W))))
        then:
            cameraPosition(updated) == before
    }
}