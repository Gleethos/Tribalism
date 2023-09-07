package app.engine;

public class Voxel
{
    private final int _color;
    private final VoxelChunkTree _subtree;

    public Voxel( int color, VoxelChunkTree subtree ) {
        _color = color;
        _subtree = subtree;
    }

    public boolean isLeaf() { return _subtree == null; }
}
