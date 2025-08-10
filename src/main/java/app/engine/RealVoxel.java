package app.engine;

import app.engine.primitives.BoundingBox;

public class RealVoxel
{
    private final VoxelData _voxelData;
    private final BoundingBox _bounds;

    private RealVoxel(VoxelData voxelData, BoundingBox bounds ) {
        _voxelData = voxelData;
        _bounds = bounds;
    }

    public static RealVoxel of(VoxelData voxelData, BoundingBox bounds ) {
        return new RealVoxel(voxelData, bounds);
    }

    public RealVoxel withBounds( BoundingBox bounds ) {
        return RealVoxel.of(_voxelData, bounds);
    }

    public VoxelData data() { return _voxelData; }

    public BoundingBox bounds() { return _bounds; }

    public boolean isLeaf() { return _voxelData.isLeaf(); }
}
