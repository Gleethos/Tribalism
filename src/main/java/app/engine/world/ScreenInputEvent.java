package app.engine.world;

/**
 *  One thing that happened on a {@link Screen} since the last engine update.
 *  <p>
 *  This is a sum type. A frame's worth of these is gathered into a
 *  {@link ScreenInputs} event log and fed to {@link World#update(EngineInputs)},
 *  which folds them into camera (and, in future, entity) mutations. Events describe
 *  <i>changes</i>: a held key produces one {@link KeyPressed} when it goes down and
 *  one {@link KeyReleased} when it comes up &mdash; not one per frame &mdash; so the
 *  world is responsible for remembering held state between updates.
 *  <p>
 *  Pointer events carry a {@link PointerId} so the mouse and individual touch points
 *  are told apart, and cursor positions are in screen pixels.
 */
public sealed interface ScreenInputEvent
    permits ScreenInputEvent.KeyPressed,
            ScreenInputEvent.KeyReleased,
            ScreenInputEvent.CursorMoved,
            ScreenInputEvent.CursorDown,
            ScreenInputEvent.CursorUp,
            ScreenInputEvent.Scrolled
{
    /** A key went down. */
    record KeyPressed(Key key) implements ScreenInputEvent {}

    /** A key came back up. */
    record KeyReleased(Key key) implements ScreenInputEvent {}

    /**
     *  A pointer moved to {@code (x, y)} screen pixels, having travelled
     *  {@code (deltaX, deltaY)} pixels since its previous position.
     */
    record CursorMoved(PointerId pointer, double x, double y, double deltaX, double deltaY) implements ScreenInputEvent {}

    /** A pointer button was pressed (or a touch point first made contact) at {@code (x, y)}. */
    record CursorDown(PointerId pointer, double x, double y, PointerButton button) implements ScreenInputEvent {}

    /** A pointer button was released (or a touch point lifted) at {@code (x, y)}. */
    record CursorUp(PointerId pointer, double x, double y, PointerButton button) implements ScreenInputEvent {}

    /** A scroll/wheel (or pinch) of the given signed {@code amount} from a pointer. */
    record Scrolled(PointerId pointer, double amount) implements ScreenInputEvent {}
}