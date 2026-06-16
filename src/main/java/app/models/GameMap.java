package app.models;

import dal.api.Value;

/** The immutable generation-parameter state of a {@link GameMapModel}. */
public record GameMap(String name, long seed, double chunkSize, double generationDistance) implements Value {
    public GameMap {
        if ( name == null )
            throw new IllegalArgumentException("GameMap name must not be null");
    }
    public static GameMap empty() { return new GameMap("", 0L, 0.0, 0.0); }
    public GameMap withName(String name)                                 { return new GameMap(name, seed, chunkSize, generationDistance); }
    public GameMap withSeed(long seed)                                   { return new GameMap(name, seed, chunkSize, generationDistance); }
    public GameMap withChunkSize(double chunkSize)                       { return new GameMap(name, seed, chunkSize, generationDistance); }
    public GameMap withGenerationDistance(double generationDistance)     { return new GameMap(name, seed, chunkSize, generationDistance); }
}
