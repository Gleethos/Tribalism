package app.engine;

import app.engine.entities.Entity;
import app.engine.primitives.BoundingBox;

public class TreeRoot
{
    private final RealVoxel _root;

    private TreeRoot( BoundingBox bounds ) {
        _root = RealVoxel.of(VoxelData.none(), bounds);
    }

    private TreeRoot( RealVoxel root ) {
        _root = root;
    }

    public static TreeRoot of( BoundingBox bounds ) {
        return new TreeRoot(bounds);
    }

    public TreeRoot addEntity( Entity entity ) {
        var traverser = TreeTraverseHead.of(_root.data(), _root.bounds());

        traverser = traverser.addEntity(entity);

        return new TreeRoot(traverser.voxel());
    }

    public TreeRoot update() {
        var traverser = TreeTraverseHead.of(_root.data(), _root.bounds());

        //traverser = traverser.addEntity(entity)

        return new TreeRoot(traverser.voxel());
    }
}
