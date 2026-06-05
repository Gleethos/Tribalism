package app.engine.world;

/**
 *  A stable, typed identifier for a {@link Screen}.
 *  <p>
 *  It is a thin wrapper around a {@code long} purely so a screen id can never be
 *  confused with an entity id or any other bare number in a method signature &mdash;
 *  most importantly the one passed to {@link World#collectSectorsForRendering}.
 *
 *  @param value The underlying numeric id.
 */
public record ScreenId(long value)
{
    public static ScreenId of( long value ) {
        return new ScreenId(value);
    }
}