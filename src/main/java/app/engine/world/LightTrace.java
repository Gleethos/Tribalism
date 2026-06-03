package app.engine.world;

import app.engine.primitives.VecF64;

/**
 *  A trace of light radiating away from a {@link LightSource}, but without an
 *  identity of its own.
 *  <p>
 *  When the engine's update cycle runs, it places these traces into the world
 *  tree so that light slowly radiates from its source into neighbouring sectors.
 *  How far a trace spreads depends on its {@code intensity}, which falls off with
 *  a threshold as the light propagates. Each trace remembers which actual light
 *  it came from via {@code sourceId}.
 *
 *  @param direction The direction the light is travelling in.
 *  @param intensity The remaining intensity of the trace (non-negative).
 *  @param sourceId  The id of the {@link LightSource} this trace radiated from.
 */
public record LightTrace(
    VecF64 direction,
    double intensity,
    long sourceId
) {
    public LightTrace {
        if ( intensity < 0 )
            throw new IllegalArgumentException("The intensity must not be negative, but was " + intensity + ".");
    }

    public static LightTrace of( VecF64 direction, double intensity, long sourceId ) {
        return new LightTrace(direction, intensity, sourceId);
    }
}