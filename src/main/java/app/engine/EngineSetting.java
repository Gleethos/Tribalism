package app.engine;

/**
 *  A singleton class that contains the settings for the engine.
 */
public class EngineSetting
{
    private static final EngineSetting _INSTANCE = new EngineSetting();

    public static EngineSetting get() { return _INSTANCE; }

    private EngineSetting() {}

    public int chunkWidth() { return 64; }

    public int chunkHeight() { return 64; }

    public int chunkDepth() { return 64; }

    public int chunkSize() { return chunkWidth() * chunkHeight() * chunkDepth(); }


}
