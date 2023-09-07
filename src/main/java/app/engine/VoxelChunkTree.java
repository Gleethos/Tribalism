package app.engine;

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
    private final int _width  = 128;
    private final int _height = 128;
    private final int _depth  = 128;

    private final Voxel[] _children;




}
