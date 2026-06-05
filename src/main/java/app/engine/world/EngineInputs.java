package app.engine.world;

import sprouts.Association;

/**
 *  Everything the outside world tells the engine for a single {@link World#update}
 *  step: how much time has passed, and what happened on each {@link Screen}.
 *  <p>
 *  Time ({@code dtSeconds}) is global to the step rather than per-screen, so movement
 *  derived from held inputs is frame-rate independent. The per-screen
 *  {@link ScreenInputs} are keyed by {@link ScreenId}; a screen absent from the map
 *  simply had nothing happen to it (and is left untouched by the update).
 *
 *  @param dtSeconds The wall-clock time elapsed since the previous update, in seconds.
 *  @param screens   The per-screen input logs for this step.
 */
public record EngineInputs(
    double dtSeconds,
    Association<ScreenId, ScreenInputs> screens
) {
    /** @return Inputs advancing time by {@code dtSeconds} with no screen events yet. */
    public static EngineInputs of( double dtSeconds ) {
        return new EngineInputs(dtSeconds, Association.between(ScreenId.class, ScreenInputs.class));
    }

    /** @return These inputs with {@code inputs} recorded for {@code screen} (replacing any prior log). */
    public EngineInputs withScreen( ScreenId screen, ScreenInputs inputs ) {
        return new EngineInputs(dtSeconds, screens.put(screen, inputs));
    }
}