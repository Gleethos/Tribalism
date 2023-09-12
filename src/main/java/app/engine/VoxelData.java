package app.engine;

import java.util.Optional;

public class VoxelData
{
    private static final VoxelData _NONE = new VoxelData(0, null);

    public static VoxelData none() { return _NONE; }

    private final int _color;
    private final VoxelChunkTree _subtree;

    public VoxelData(int color, VoxelChunkTree subtree ) {
        _color = color;
        _subtree = subtree;
    }

    public boolean isLeaf() { return _subtree == null; }

    public Optional<VoxelChunkTree> subtree() { return Optional.ofNullable(_subtree); }
}
