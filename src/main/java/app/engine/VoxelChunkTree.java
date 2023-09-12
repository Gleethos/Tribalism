package app.engine;

import java.util.Objects;

/**
 *   A voxel tree node is a chunk of voxels forming a 3-dimensional grid (a tensor).
 *   It is used to represent a 3-dimensional space with a certain size and scale (relative to the entire world).
 *   Note that this is an immutable data structure, meaning that it cannot be changed after it has been created.
 *   Changes to the voxel tree are made by creating a new voxel tree with the desired changes
 *   (this is not as bad as you might think, because the voxel tree is a tree structure, meaning that
 *   only the changed nodes and the path to them are copied, the rest of the tree is shared).
 *   This is done to make the voxel tree thread-safe, because it can be accessed from multiple threads
 *   at the same time.
 *   The engine is designed from the ground up to be fully multithreaded, meaning that it can
 *   take advantage of all the cores of the CPU (except for the main thread, which is used for
 *   rendering and user input).
 */
public class VoxelChunkTree
{
    private static final VoxelChunkTree _EMPTY;
    static {
        VoxelData[] children = new VoxelData[64 * 64 * 64];
        for ( int i = 0; i < children.length; i++ )
            children[i] = VoxelData.none();
        _EMPTY = new VoxelChunkTree(children, new Entity[0], 0, 0);
    }

    public static VoxelChunkTree empty() { return _EMPTY; }

    private final int _width  = EngineSetting.get().chunkWidth();
    private final int _height = EngineSetting.get().chunkHeight();
    private final int _depth  = EngineSetting.get().chunkDepth();

    private final VoxelData[] _children;
    private final Entity[] _entities;

    private final long _allEntities;
    private final long _modifiedVoxels;


    private VoxelChunkTree(VoxelData[] children, Entity[] entities, long allEntities, long modifiedVoxels ) {
        _children = Objects.requireNonNull(children);
        _entities = Objects.requireNonNull(entities);
        _allEntities = allEntities;
        _modifiedVoxels = modifiedVoxels;
    }

    public VoxelChunkTree withVoxel(VoxelData voxelData, int x, int y, int z ) {
        if ( x < 0 || x >= _width || y < 0 || y >= _height || z < 0 || z >= _depth )
            throw new IllegalArgumentException("Voxel coordinates out of bounds!");

        VoxelData[] children = new VoxelData[_children.length];
        System.arraycopy(_children, 0, children, 0, _children.length);
        var oldVoxel = children[ x + y * _width + z * _width * _height ];
        if ( oldVoxel.equals(voxelData) )
            return this;

        long modifiedVoxels = _modifiedVoxels;
        if ( oldVoxel.equals(VoxelData.none()) && !voxelData.equals(VoxelData.none()) )
            modifiedVoxels++;
        else if ( !oldVoxel.equals(VoxelData.none()) && voxelData.equals(VoxelData.none()) )
            modifiedVoxels--;

        children[ x + y * _width + z * _width * _height ] = voxelData;

        return new VoxelChunkTree(children, _entities, _allEntities, modifiedVoxels);
    }

    private int _indexOfEntity( Entity entity ) {
        for ( int i = 0; i < _entities.length; i++ ) {
            if ( _entities[i].equals(entity) )
                return i;
        }
        return -1;
    }

    private VoxelChunkTree _withEntity( Entity entity ) {
        int index = _indexOfEntity(entity);
        if ( index != -1 )
            return this;

        Entity[] entities = new Entity[_entities.length + 1];
        System.arraycopy(_entities, 0, entities, 0, _entities.length);
        entities[_entities.length] = entity;
        return new VoxelChunkTree(_children, entities, _allEntities + 1, _modifiedVoxels);
    }

    private VoxelChunkTree _withoutEntity( Entity entity ) {
        int index = _indexOfEntity(entity);
        if ( index == -1 )
            return this;

        Entity[] entities = new Entity[_entities.length - 1];
        index = 0;
        for ( Entity e : _entities ) {
            if ( e.equals(entity) )
                continue;
            entities[index++] = e;
        }
        return new VoxelChunkTree(_children, entities, _allEntities - 1, _modifiedVoxels);
    }

    public Entity[] entities() { return _entities; }

    /**
     *  Note that a chuck is just a 3-dimensional grid of voxels without any knowledge of its position
     *  and bounds in the full world reference frame.
     *  All of this information is calculated eagerly during traversal of the voxel tree
     *  (this is designed so that a chuck can also be used as part of a moving entity!).
     *  This method resolves the voxel at the given position in the given chunk reference frame.
     *  The chunk reference frame is a bounding box that represents the chunk's position and size
     *  in the full world reference frame. <br>
     *  This method returns a {@link RealVoxel} object that contains the resolved voxel and its bounds
     *  in the full world reference frame.
     *
     * @param chunkReferenceFrame A bounding box that represents the chunk's position and size
     * @param chunkPos The position of the voxel in the chunk reference frame
     * @return A {@link RealVoxel} object that contains the resolved voxel and its bounds
     */
    public RealVoxel voxel(BoundingBox chunkReferenceFrame, VecF64 chunkPos ) {
        // Our goal is the integer based voxel position in the chunk!
        // Let's first calculate the translation and scale between the reference and the chunk bounds:
        var translation = chunkReferenceFrame.min();
        var scale       = chunkReferenceFrame.size().div(_width, _height, _depth);
        // Now we can calculate the voxel position in the chunk:
        int x = (int) Math.floor( (chunkPos.x() - translation.x()) / scale.x() );
        int y = (int) Math.floor( (chunkPos.y() - translation.y()) / scale.y() );
        int z = (int) Math.floor( (chunkPos.z() - translation.z()) / scale.z() );
        // And finally we can calculate the voxel position in the world:
        var voxelPos = chunkReferenceFrame.min().add(VecF64.of(x, y, z).mul(scale));
        // And the bounds of the voxel in the world:
        var voxelBounds = BoundingBox.of(voxelPos, voxelPos.add(scale));
        // And now we can return the result:
        return RealVoxel.of(voxel(x, y, z), voxelBounds);
    }

    public VoxelData voxel(int x, int y, int z ) {
        if ( x < 0 || x >= _width || y < 0 || y >= _height || z < 0 || z >= _depth )
            throw new IllegalArgumentException("Voxel coordinates out of bounds!");

        return _children[ x + y * _width + z * _width * _height ];
    }

}
