package app.engine;

import java.util.Objects;

public class EngineContext
{
    private final ChunkFactory _chunkFactory;

    public EngineContext(ChunkFactory chunkFactory) {
        _chunkFactory = Objects.requireNonNull(chunkFactory);
    }

    public ChunkFactory chunkFactory() { return _chunkFactory; }
}
