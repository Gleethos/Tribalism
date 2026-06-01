package app.engine.primitives;

/**
 *  An immutable description of a viewpoint into the 64-bit simulation space.
 *  <p>
 *  A camera is purely a piece of data: it knows where it sits ({@code position}),
 *  what it looks at ({@code target}), which way is up, and the shape of its view
 *  frustum ({@code fovYRadians}, {@code aspect}, {@code near}, {@code far}). From
 *  that it can derive its {@link #viewMatrix()} and {@link #projectionMatrix()}.
 *  The actual rendering remains a separate, pluggable function of this state.
 *
 *  @param position    Where the camera sits in world space.
 *  @param target      The point the camera looks at.
 *  @param up          The world-space "up" hint used to orient the camera.
 *  @param fovYRadians The vertical field of view, in radians.
 *  @param aspect      The viewport aspect ratio ({@code width / height}).
 *  @param near        The distance to the near clipping plane (positive).
 *  @param far         The distance to the far clipping plane (positive, > near).
 */
public record CameraF64(
    VecF64 position,
    VecF64 target,
    VecF64 up,
    double fovYRadians,
    double aspect,
    double near,
    double far
) {
    public CameraF64 {
        if ( near <= 0 )       throw new IllegalArgumentException("The near plane must be positive, but was " + near + ".");
        if ( far <= near )     throw new IllegalArgumentException("The far plane (" + far + ") must be greater than the near plane (" + near + ").");
        if ( aspect <= 0 )     throw new IllegalArgumentException("The aspect ratio must be positive, but was " + aspect + ".");
        if ( fovYRadians <= 0 || fovYRadians >= Math.PI )
            throw new IllegalArgumentException("The vertical field of view must be in the open interval (0, PI), but was " + fovYRadians + ".");
    }

    /** @return The normalized direction the camera is looking towards. */
    public VecF64 forward() {
        return target.sub(position).normalize();
    }

    /** @return The normalized direction pointing to the camera's right. */
    public VecF64 right() {
        return forward().cross(up).normalize();
    }

    /** @return The world-to-view transform, derived from position, target and up. */
    public Mat4F64 viewMatrix() {
        return Mat4F64.lookAt(position, target, up);
    }

    /** @return The view-to-clip perspective transform, derived from the frustum. */
    public Mat4F64 projectionMatrix() {
        return Mat4F64.perspective(fovYRadians, aspect, near, far);
    }

    /** @return The combined world-to-clip transform, {@code projection * view}. */
    public Mat4F64 viewProjectionMatrix() {
        return projectionMatrix().mul(viewMatrix());
    }

    public CameraF64 withPosition( VecF64 newPosition ) {
        return new CameraF64(newPosition, target, up, fovYRadians, aspect, near, far);
    }

    public CameraF64 withTarget( VecF64 newTarget ) {
        return new CameraF64(position, newTarget, up, fovYRadians, aspect, near, far);
    }

    public CameraF64 withAspect( double newAspect ) {
        return new CameraF64(position, target, up, fovYRadians, newAspect, near, far);
    }
}