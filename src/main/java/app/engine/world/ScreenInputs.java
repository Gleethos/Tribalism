package app.engine.world;

import sprouts.Tuple;

/**
 *  The log of everything that happened on one {@link Screen} since the last engine
 *  update: an ordered {@link Tuple} of {@link ScreenInputEvent}s.
 *  <p>
 *  Order is preserved because it can matter (e.g. a press and release of the same key
 *  within one frame). A screen with nothing happening contributes {@link #none()}.
 *
 *  @param events The events, in the order they occurred.
 */
public record ScreenInputs(
    Tuple<ScreenInputEvent> events
) {
    private static final ScreenInputs NONE = new ScreenInputs(Tuple.of(ScreenInputEvent.class));

    /** @return An empty event log (nothing happened on the screen this frame). */
    public static ScreenInputs none() {
        return NONE;
    }

    /** @return An input log over the given events, in order. */
    public static ScreenInputs of( ScreenInputEvent... events ) {
        return new ScreenInputs(Tuple.of(ScreenInputEvent.class, events));
    }

    /** @return {@code true} if no events were recorded. */
    public boolean isEmpty() {
        return events.isEmpty();
    }
}