package app.engine.world;

/**
 *  A typed identifier for an on-screen pointer.
 *  <p>
 *  A pointer is whatever produces {@link ScreenInputEvent.CursorMoved cursor} events:
 *  the mouse, or one finger of a multi-touch surface. Giving each pointer an id is
 *  what lets the input model describe several simultaneous cursors (multi-touch)
 *  rather than assuming a single mouse.
 *
 *  @param value The underlying numeric id.
 */
public record PointerId(long value)
{
    /** The conventional id of the single system mouse pointer. */
    public static final PointerId MOUSE = new PointerId(0);

    public static PointerId of( long value ) {
        return new PointerId(value);
    }
}