package app.engine.world;

/**
 *  An engine-neutral keyboard key.
 *  <p>
 *  The engine deliberately does <i>not</i> speak AWT/Swing (or any other toolkit's)
 *  raw key codes: the host application maps its platform key codes onto these values
 *  when it builds {@link ScreenInputEvent.KeyPressed} / {@link ScreenInputEvent.KeyReleased}
 *  events, keeping the world model free of UI-framework dependencies. Only the keys the
 *  engine actually reasons about need to exist here; the set grows as needed.
 */
public enum Key
{
    A, B, C, D, E, F, G, H, I, J, K, L, M,
    N, O, P, Q, R, S, T, U, V, W, X, Y, Z,

    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,

    SPACE, SHIFT, CONTROL, ALT, ESCAPE, ENTER, TAB, BACKSPACE,

    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT
}