package app.engine.world;

/**
 *  Which button of a pointer triggered a {@link ScreenInputEvent.CursorDown} /
 *  {@link ScreenInputEvent.CursorUp}. For a touch pointer there is only ever
 *  {@link #PRIMARY} (the contact itself).
 */
public enum PointerButton
{
    PRIMARY,
    SECONDARY,
    MIDDLE
}