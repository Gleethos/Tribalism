package app.engine;

import java.util.Optional;

public class TreeTraverseHead
{
    private final RealVoxel _voxel;

    private TreeTraverseHead(VoxelData node, BoundingBox scale ) {
        _voxel = RealVoxel.of(node, scale);
    }

    public static TreeTraverseHead of(VoxelData node, BoundingBox scale ) {
        return new TreeTraverseHead(node, scale);
    }

    public RealVoxel voxel() { return _voxel; }

    public Optional<TreeTraverseHead> traverse( VecF64 position )
    {
        if ( !_voxel.bounds().contains(position) )
            return Optional.empty();

        if ( _voxel.isLeaf() )
            return Optional.of(this);

        Optional<RealVoxel> voxel = findVoxelFor(_voxel.bounds(), position);

        return voxel.map(realizedVoxel -> TreeTraverseHead.of(realizedVoxel.data(), realizedVoxel.bounds()));
    }

    public TreeTraverseHead addEntity( Entity entity ) {
        var subTraverser = traverse(entity.position());

        if ( subTraverser.isEmpty() ) {

        }

        var realizedVoxel = subTraverser.get();
    }

    public Optional<RealVoxel> findVoxelFor( BoundingBox scale, VecF64 position )
    {
        if ( !scale.contains(position) )
            return Optional.empty();

        if ( _voxel.isLeaf() )
            return Optional.of(_voxel.withBounds(scale));

        return _voxel.data().subtree().map( t -> t.voxel(scale, position) );
    }

    public Optional<RealVoxel> findVoxelFor( BoundingBox scale, BoundingBox box )
    {
        VecF64 position = box.center();

        if ( !scale.contains(position) )
            return Optional.empty();

        if ( _voxel.isLeaf() )
            return Optional.of(_voxel.withBounds(scale));

        return _voxel.data().subtree().map( t -> t.voxel(scale, position) );
    }

}
