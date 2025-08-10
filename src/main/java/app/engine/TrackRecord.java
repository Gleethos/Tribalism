package app.engine;

import java.util.Objects;

public class TrackRecord
{
    private static final TrackRecord _NONE = new TrackRecord( null, VoxelChunkTree.empty(), 0 );

    public static TrackRecord none() { return _NONE; }

    private final TrackRecord    _history;
    private final VoxelChunkTree _previous;
    private final long _historyLength;

    private TrackRecord( TrackRecord history, VoxelChunkTree previous, long historyLength ) {
        _history = history == null ? this : history;
        _previous = Objects.requireNonNull(previous);
        _historyLength = historyLength;
    }

    public TrackRecord withPrevious( VoxelChunkTree previousReference ) {
        return new TrackRecord(this, previousReference, _historyLength + 1);
    }

    public long historyLength() { return _historyLength; }
}

