package app.engine.world;

import org.jspecify.annotations.Nullable;

import java.util.OptionalLong;

/**
 *  A render target the world knows about: a rectangular surface with a stable
 *  {@link ScreenId id}, a pixel {@code width} and {@code height}, and (optionally) the
 *  id of the camera entity it shows.
 *  <p>
 *  A screen references its camera <b>one-way</b>, by id, so the same camera entity can
 *  drive several screens at once; cameras never need to know which screens display
 *  them. A freshly {@link World#createScreen created} screen is <b>unbound</b> until
 *  {@link World#bindScreenToCamera bound}; rendering an unbound (or dangling) screen
 *  simply produces nothing.
 *
 *  @param id       The screen's stable identifier.
 *  @param width    The viewport width in pixels.
 *  @param height   The viewport height in pixels.
 *  @param cameraId The id of the bound camera entity, or {@code null} if unbound.
 */
public record Screen(
    ScreenId id,
    int width,
    int height,
    @Nullable Long cameraId
) {
    /** @return An unbound screen of the given id and size. */
    public static Screen of( ScreenId id, int width, int height ) {
        return new Screen(id, width, height, null);
    }

    /** @return The viewport aspect ratio, {@code width / height}. */
    public double aspect() {
        return height == 0 ? 1.0 : (double) width / height;
    }

    /** @return {@code true} if this screen is bound to a camera. */
    public boolean isBound() {
        return cameraId != null;
    }

    /** @return The bound camera entity id, if any. */
    public OptionalLong camera() {
        return cameraId == null ? OptionalLong.empty() : OptionalLong.of(cameraId);
    }

    /** @return This screen bound to the camera entity {@code cameraId}. */
    public Screen withCamera( long cameraId ) {
        return new Screen(id, width, height, cameraId);
    }

    /** @return This screen with no bound camera. */
    public Screen withoutCamera() {
        return new Screen(id, width, height, null);
    }

    /** @return This screen resized to {@code newWidth} x {@code newHeight} pixels. */
    public Screen withSize( int newWidth, int newHeight ) {
        return new Screen(id, newWidth, newHeight, cameraId);
    }
}