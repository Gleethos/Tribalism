package app.engine.renderer;

import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;

public class Camera
{
    private static final Camera _DEFAULT = new Camera(
                                                VecF64.of(0, 0, 0),
                                                VecF64.of(0, 0, 1),
                                                VecF64.of(0, 1, 0),
                                                Math.PI / 2,
                                                1,
                                                0.1,
                                                1000
                                            );

    public static Camera getDefault() { return _DEFAULT; }

    private final VecF64 _position;
    private final VecF64 _direction;
    private final VecF64 _up;

    private final double _fov;
    private final double _aspectRatio;
    private final double _near;
    private final double _far;

    private final Mat4F64 _projectionMatrix;


    private Camera( VecF64 position, VecF64 direction, VecF64 up, double fov, double aspectRatio, double near, double far ) {
        _position = position;
        _direction = direction;
        _up = up;
        _fov = fov;
        _aspectRatio = aspectRatio;
        _near = near;
        _far = far;

        _projectionMatrix = _calculateProjectionMatrix( position, direction, up, fov, aspectRatio, near, far );
    }

    private Mat4F64 _calculateProjectionMatrix(
        VecF64 position,
        VecF64 direction,
        VecF64 up,
        double fov,
        double aspectRatio,
        double near,
        double far
    ) {
        VecF64 zAxis = direction.normalize();
        VecF64 xAxis = up.cross(zAxis).normalize();
        VecF64 yAxis = zAxis.cross(xAxis);

        double tanHalfFov = Math.tan(fov / 2);
        double zRange = near - far;

        return Mat4F64.of(new double[] {
            xAxis.x() * (1.0 / tanHalfFov) * aspectRatio, yAxis.x() * (1.0 / tanHalfFov), zAxis.x(), 0,
            xAxis.y() * (1.0 / tanHalfFov) * aspectRatio, yAxis.y() * (1.0 / tanHalfFov), zAxis.y(), 0,
            xAxis.z() * (1.0 / tanHalfFov) * aspectRatio, yAxis.z() * (1.0 / tanHalfFov), zAxis.z(), 0,
            xAxis.dot(position) * (1.0 / tanHalfFov) * aspectRatio, yAxis.dot(position) * (1.0 / tanHalfFov), zAxis.dot(position), 1
        });
    }

    public VecF64 position() { return _position; }

    public VecF64 direction() { return _direction; }

    public VecF64 up() { return _up; }

    public double fov() { return _fov; }

    public double aspectRatio() { return _aspectRatio; }

    public double near() { return _near; }

    public double far() { return _far; }

    public Mat4F64 projectionMatrix() { return _projectionMatrix; }

    public Camera withPosition( VecF64 position ) {
        return new Camera(position, _direction, _up, _fov, _aspectRatio, _near, _far);
    }

    public Camera withDirection( VecF64 direction ) {
        return new Camera(_position, direction, _up, _fov, _aspectRatio, _near, _far);
    }

    public Camera withUp( VecF64 up ) {
        return new Camera(_position, _direction, up, _fov, _aspectRatio, _near, _far);
    }

    public Camera withFov( double fov ) {
        return new Camera(_position, _direction, _up, fov, _aspectRatio, _near, _far);
    }

    public Camera withAspectRatio( double aspectRatio ) {
        return new Camera(_position, _direction, _up, _fov, aspectRatio, _near, _far);
    }

    public Camera withNear( double near ) {
        return new Camera(_position, _direction, _up, _fov, _aspectRatio, near, _far);
    }

    public Camera withFar( double far ) {
        return new Camera(_position, _direction, _up, _fov, _aspectRatio, _near, far);
    }

    public Camera withProjectionMatrix( Mat4F64 projectionMatrix ) {
        return new Camera(_position, _direction, _up, _fov, _aspectRatio, _near, _far);
    }

}
