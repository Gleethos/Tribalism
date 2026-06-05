package app.engine.world;

import app.engine.primitives.CameraF64;
import app.engine.primitives.Frustum;
import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import org.jspecify.annotations.Nullable;

/**
 *  Everything a renderer needs to turn world geometry into screen geometry for a
 *  single frame: the {@link CameraF64 camera} it is seen from, the derived
 *  {@link Frustum} and view-projection matrix, the focal length in pixels, and the
 *  viewport size.
 *  <p>
 *  This is the value handed to a {@link SectorDrawCollector} by
 *  {@link World#collectSectorsForRendering}. It deliberately carries the projection
 *  itself ({@link #project(VecF64)}) so the culling logic in {@link World} and the
 *  drawing logic in a renderer share exactly one definition of how a world point
 *  lands on the screen.
 *
 *  @param camera          The camera the frame is rendered from.
 *  @param frustum         The camera's view frustum (for culling tests).
 *  @param viewProjection  The combined view-projection matrix.
 *  @param focalLengthPx   The focal length in pixels for this viewport.
 *  @param width           The viewport width in pixels.
 *  @param height          The viewport height in pixels.
 */
public record ViewInfo(
    CameraF64 camera,
    Frustum frustum,
    Mat4F64 viewProjection,
    double focalLengthPx,
    int width,
    int height
) {
    /** @return The view of {@code camera} into a {@code width}x{@code height} viewport. */
    public static ViewInfo of( CameraF64 camera, int width, int height ) {
        return new ViewInfo(
                camera,
                camera.frustum(),
                camera.viewProjectionMatrix(),
                World.focalLengthPx(camera, height),
                width, height
        );
    }

    /**
     *  Projects a world point to screen pixels.
     *  @return {@code {screenX, screenY}}, or {@code null} if the point is at or
     *          behind the camera (clip {@code w <= 0}).
     */
    public double @Nullable [] project( VecF64 p ) {
        Mat4F64 vp = viewProjection;
        double x = vp.get(0, 0) * p.x() + vp.get(0, 1) * p.y() + vp.get(0, 2) * p.z() + vp.get(0, 3);
        double y = vp.get(1, 0) * p.x() + vp.get(1, 1) * p.y() + vp.get(1, 2) * p.z() + vp.get(1, 3);
        double clipW = vp.get(3, 0) * p.x() + vp.get(3, 1) * p.y() + vp.get(3, 2) * p.z() + vp.get(3, 3);
        if ( clipW <= 1e-9 )
            return null;
        double ndcX = x / clipW;
        double ndcY = y / clipW;
        return new double[]{ (ndcX * 0.5 + 0.5) * width, (1 - (ndcY * 0.5 + 0.5)) * height };
    }
}