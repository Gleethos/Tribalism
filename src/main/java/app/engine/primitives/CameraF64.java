package app.engine.primitives;

import app.engine.util.Lazy;

import java.util.Objects;

/**
 *  An immutable description of a viewpoint into the 64-bit simulation space.
 *  <p>
 *  A camera is conceptually pure data: it knows where it sits ({@code position}),
 *  what it looks at ({@code target}), which way is up, and the shape of its view
 *  frustum ({@code fovYRadians}, {@code aspect}, {@code near}, {@code far}). From
 *  that it derives its {@link #viewMatrix()}, {@link #projectionMatrix()} and the
 *  cullable {@link #frustum()}; rendering remains a separate, pluggable function
 *  of this state.
 *  <p>
 *  Unlike the other primitives this is a {@code class} rather than a {@code record},
 *  precisely so it can <i>encapsulate</i> a private cache: the view, projection,
 *  combined view-projection matrices and frustum are expensive to build and are
 *  needed repeatedly while rendering a frame, so each is computed lazily and
 *  memoized via {@link Lazy}. The camera still behaves as a value &mdash; it is
 *  immutable and its {@link #equals(Object)}/{@link #hashCode()} are defined purely
 *  by the seven defining fields (never the derived caches).
 */
public final class CameraF64
{
    private final VecF64 _position;
    private final VecF64 _target;
    private final VecF64 _up;
    private final double _fovYRadians;
    private final double _aspect;
    private final double _near;
    private final double _far;

    // Derived, memoized state. Purely a function of the fields above, so it is
    // excluded from equals/hashCode and recomputed at most once per instance.
    // Initialized at the end of the constructor, once the defining fields are set.
    private final Lazy<Mat4F64> _viewMatrix;
    private final Lazy<Mat4F64> _projectionMatrix;
    private final Lazy<Mat4F64> _viewProjectionMatrix;
    private final Lazy<Frustum> _frustum;

    /**
     *  @param position    Where the camera sits in world space.
     *  @param target      The point the camera looks at.
     *  @param up          The world-space "up" hint used to orient the camera.
     *  @param fovYRadians The vertical field of view, in radians.
     *  @param aspect      The viewport aspect ratio ({@code width / height}).
     *  @param near        The distance to the near clipping plane (positive).
     *  @param far         The distance to the far clipping plane (positive, > near).
     */
    public CameraF64(
        VecF64 position,
        VecF64 target,
        VecF64 up,
        double fovYRadians,
        double aspect,
        double near,
        double far
    ) {
        if ( near <= 0 )       throw new IllegalArgumentException("The near plane must be positive, but was " + near + ".");
        if ( far <= near )     throw new IllegalArgumentException("The far plane (" + far + ") must be greater than the near plane (" + near + ").");
        if ( aspect <= 0 )     throw new IllegalArgumentException("The aspect ratio must be positive, but was " + aspect + ".");
        if ( fovYRadians <= 0 || fovYRadians >= Math.PI )
            throw new IllegalArgumentException("The vertical field of view must be in the open interval (0, PI), but was " + fovYRadians + ".");
        _position    = Objects.requireNonNull(position);
        _target      = Objects.requireNonNull(target);
        _up          = Objects.requireNonNull(up);
        _fovYRadians = fovYRadians;
        _aspect      = aspect;
        _near        = near;
        _far         = far;

        _viewMatrix           = Lazy.of(() -> Mat4F64.lookAt(_position, _target, _up));
        _projectionMatrix     = Lazy.of(() -> Mat4F64.perspective(_fovYRadians, _aspect, _near, _far));
        _viewProjectionMatrix = Lazy.of(() -> projectionMatrix().mul(viewMatrix()));
        _frustum              = Lazy.of(() -> Frustum.of(viewProjectionMatrix()));
    }

    public VecF64 position()    { return _position; }
    public VecF64 target()      { return _target; }
    public VecF64 up()          { return _up; }
    public double fovYRadians() { return _fovYRadians; }
    public double aspect()      { return _aspect; }
    public double near()        { return _near; }
    public double far()         { return _far; }

    /** @return The normalized direction the camera is looking towards. */
    public VecF64 forward() {
        return _target.sub(_position).normalize();
    }

    /** @return The normalized direction pointing to the camera's right. */
    public VecF64 right() {
        return forward().cross(_up).normalize();
    }

    /** @return The world-to-view transform, derived from position, target and up (cached). */
    public Mat4F64 viewMatrix() {
        return _viewMatrix.get();
    }

    /** @return The view-to-clip perspective transform, derived from the frustum (cached). */
    public Mat4F64 projectionMatrix() {
        return _projectionMatrix.get();
    }

    /** @return The combined world-to-clip transform, {@code projection * view} (cached). */
    public Mat4F64 viewProjectionMatrix() {
        return _viewProjectionMatrix.get();
    }

    /** @return The six culling planes of this camera's view volume (cached). */
    public Frustum frustum() {
        return _frustum.get();
    }

    public CameraF64 withPosition( VecF64 newPosition ) {
        return new CameraF64(newPosition, _target, _up, _fovYRadians, _aspect, _near, _far);
    }

    public CameraF64 withTarget( VecF64 newTarget ) {
        return new CameraF64(_position, newTarget, _up, _fovYRadians, _aspect, _near, _far);
    }

    public CameraF64 withAspect( double newAspect ) {
        return new CameraF64(_position, _target, _up, _fovYRadians, newAspect, _near, _far);
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof CameraF64 other) ) return false;
        return Double.compare(_fovYRadians, other._fovYRadians) == 0
            && Double.compare(_aspect, other._aspect) == 0
            && Double.compare(_near, other._near) == 0
            && Double.compare(_far, other._far) == 0
            && _position.equals(other._position)
            && _target.equals(other._target)
            && _up.equals(other._up);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_position, _target, _up, _fovYRadians, _aspect, _near, _far);
    }

    @Override
    public String toString() {
        return "CameraF64[" +
                "position=" + _position +
                ", target=" + _target +
                ", up=" + _up +
                ", fovYRadians=" + _fovYRadians +
                ", aspect=" + _aspect +
                ", near=" + _near +
                ", far=" + _far +
                ']';
    }
}