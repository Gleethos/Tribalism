package app.engine.world;

import app.engine.primitives.CameraF64;
import app.engine.primitives.VecF64;
import sprouts.ValueSet;

/**
 *  The free-fly camera control scheme, as a pure function of inputs.
 *  <p>
 *  This is the "fly logic" that used to live in the Swing demo, lifted into the
 *  engine so it is the world's job (via {@link World#update}) to turn held keys and
 *  mouse-look deltas into camera motion &mdash; not any individual front-end's. It is
 *  intentionally a single static, side-effect-free method so it can be unit-tested in
 *  isolation and reused by any host.
 *  <p>
 *  Movement is keyed to {@link Key}: {@code W/S} forward/back, {@code A/D} strafe,
 *  {@code Q} down and {@code E}/{@link Key#SPACE} up, with {@link Key#SHIFT} to sprint.
 *  Look is driven by accumulated cursor delta. Speed scales with the size of the world
 *  ({@code worldExtent}) so the same controls feel right whether the world is metres or
 *  kilometres across, and with {@code dtSeconds} so motion is frame-rate independent.
 */
public final class CameraFlight
{
    private static final double LOOK_SENSITIVITY     = 0.005;
    private static final double PITCH_LIMIT_RADIANS  = Math.toRadians(89);
    private static final double SPEED_FACTOR         = 0.6;
    private static final double SPRINT_MULTIPLIER    = 3.0;

    private static final VecF64 WORLD_UP = VecF64.of(0, 1, 0);

    private CameraFlight() {}

    /**
     *  Applies one step of free-fly control to a camera.
     *
     *  @param camera      The camera to move.
     *  @param heldKeys    The keys currently held down (movement).
     *  @param lookDeltaX  Accumulated horizontal cursor delta this step, in pixels.
     *  @param lookDeltaY  Accumulated vertical cursor delta this step, in pixels.
     *  @param worldExtent The largest edge of the world's bounds (sets the movement scale).
     *  @param dtSeconds   The time elapsed this step, in seconds.
     *  @return The moved/looked camera, or the same camera if nothing was held or moved.
     */
    public static CameraF64 fly(
        CameraF64 camera, ValueSet<Key> heldKeys,
        double lookDeltaX, double lookDeltaY,
        double worldExtent, double dtSeconds
    ) {
        boolean hasLook = lookDeltaX != 0 || lookDeltaY != 0;
        boolean hasMove = heldKeys.contains(Key.W) || heldKeys.contains(Key.S)
                       || heldKeys.contains(Key.A) || heldKeys.contains(Key.D)
                       || heldKeys.contains(Key.Q) || heldKeys.contains(Key.E)
                       || heldKeys.contains(Key.SPACE);
        if ( !hasLook && !hasMove )
            return camera; // nothing to do: leave the camera value untouched.

        // Decompose the current orientation into yaw/pitch, apply the look delta, recompose.
        VecF64 forward = camera.forward();
        double pitch = Math.asin(Math.max(-1.0, Math.min(1.0, forward.y())));
        double yaw   = Math.atan2(forward.x(), forward.z());

        yaw   -= lookDeltaX * LOOK_SENSITIVITY;
        pitch -= lookDeltaY * LOOK_SENSITIVITY;
        pitch = Math.max(-PITCH_LIMIT_RADIANS, Math.min(PITCH_LIMIT_RADIANS, pitch));

        double cosPitch = Math.cos(pitch);
        VecF64 newForward = VecF64.of(cosPitch * Math.sin(yaw), Math.sin(pitch), cosPitch * Math.cos(yaw));

        // Translate along the new orientation by the held movement keys.
        double speed = worldExtent * SPEED_FACTOR * dtSeconds;
        if ( heldKeys.contains(Key.SHIFT) )
            speed *= SPRINT_MULTIPLIER;

        VecF64 right = newForward.cross(WORLD_UP).normalize();
        VecF64 move = VecF64.zero();
        if ( heldKeys.contains(Key.W) )     move = move.add(newForward);
        if ( heldKeys.contains(Key.S) )     move = move.sub(newForward);
        if ( heldKeys.contains(Key.D) )     move = move.add(right);
        if ( heldKeys.contains(Key.A) )     move = move.sub(right);
        if ( heldKeys.contains(Key.E) || heldKeys.contains(Key.SPACE) ) move = move.add(WORLD_UP);
        if ( heldKeys.contains(Key.Q) )     move = move.sub(WORLD_UP);

        VecF64 position = camera.position();
        if ( move.lengthSquared() > 0 )
            position = position.add(move.normalize().mul(speed));

        return camera.withPosition(position).withTarget(position.add(newForward));
    }
}