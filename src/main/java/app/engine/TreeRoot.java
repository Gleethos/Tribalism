package app.engine;

public class TreeRoot
{
    private RealVoxel _root;

    private TreeRoot( BoundingBox bounds ) {
        _root = RealVoxel.of(VoxelData.none(), bounds);
    }

    public static TreeRoot of( BoundingBox bounds ) {
        return new TreeRoot(bounds);
    }

    public void addEntity( Entity entity ) {
        var traverser = TreeTraverseHead.of(_root.data(), _root.bounds());

        traverser = traverser.addEntity(entity);

        _root = traverser.voxel();
    }
}
