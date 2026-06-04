package app.engine.world;

import app.engine.primitives.BoundsF64;
import sprouts.Tuple;

/**
 *  A node in the world tree: a CPU-cache-friendly, immutable {@link Tuple} of
 *  exactly {@link #SECTOR_COUNT} {@link WorldSector}s arranged as a perfect
 *  {@link #RESOLUTION}&times;{@link #RESOLUTION}&times;{@link #RESOLUTION} cube.
 *  <p>
 *  The data structure is inspired by Hash Array Mapped Tries rather than a
 *  classic oct-tree: instead of branching by 2 on each axis, a node branches by
 *  {@code 8} on each axis at once, which keeps the tree shallow and the child
 *  array contiguous and cache-friendly. Sectors are stored in
 *  {@code x + y*RESOLUTION + z*RESOLUTION^2} order.
 *  <p>
 *  Because the node is built on a persistent tuple, replacing a single sector
 *  ({@link #withSector(int, WorldSector)}) shares all untouched structure with
 *  the original node.
 *
 *  @param sectors The {@link #SECTOR_COUNT} child sectors, in linear order.
 */
public record WorldTreeNode(
    Tuple<WorldSector> sectors
) {
    /** The number of sub-divisions along each axis of a node. */
    public static final int RESOLUTION = 8;

    /** The total number of sectors in a node, i.e. {@code RESOLUTION^3 == 512}. */
    public static final int SECTOR_COUNT = RESOLUTION * RESOLUTION * RESOLUTION;

    public WorldTreeNode {
        if ( sectors.size() != SECTOR_COUNT )
            throw new IllegalArgumentException(
                    "A world tree node must contain exactly " + SECTOR_COUNT + " sectors, but got " + sectors.size() + "."
                );
    }

    /**
     *  Builds a node by subdividing {@code bounds} into a regular grid of
     *  {@link #SECTOR_COUNT} leaf sectors, each filled with the same {@code ether}.
     *  This is how a single sector is "split" into finer detail.
     *
     *  @param bounds The region the whole node covers.
     *  @param ether  The material mixture every freshly created leaf inherits.
     */
    public static WorldTreeNode uniform( BoundsF64 bounds, WorldSectorEtherData ether ) {
        Tuple<BoundsF64> cells = bounds.subdivide(RESOLUTION);
        WorldSector[] sectors = new WorldSector[SECTOR_COUNT];
        for ( int i = 0; i < SECTOR_COUNT; i++ )
            sectors[i] = WorldSector.leaf(cells.get(i), ether);
        return new WorldTreeNode(Tuple.of(WorldSector.class, sectors));
    }

    /**
     *  @return The linear indices of the {@code RESOLUTION^2} child cells that lie
     *          on the given outer {@code side} of this node (e.g. {@link Side#POS_Y}
     *          yields the top {@code 8x8} layer). This is what per-side
     *          level-of-detail aggregation walks: only a face's boundary children
     *          contribute to that face of the parent.
     */
    public static int[] boundaryCells( Side side ) {
        return layerCells(side, 0);
    }

    /**
     *  @return The linear indices of the {@code RESOLUTION^2} child cells in the
     *          {@code depth}-th layer counted inward from the given {@code side}
     *          ({@code depth == 0} is the boundary layer on that face, {@code depth
     *          == RESOLUTION - 1} the far layer). This is what the inward-moving
     *          inset algorithm peels off layer by layer.
     *  @throws IndexOutOfBoundsException if {@code depth} is not in {@code [0, RESOLUTION)}.
     */
    public static int[] layerCells( Side side, int depth ) {
        if ( depth < 0 || depth >= RESOLUTION )
            throw new IndexOutOfBoundsException("Layer depth " + depth + " is outside [0, " + RESOLUTION + ").");
        int fixed = side.isPositive() ? RESOLUTION - 1 - depth : depth;
        int[] indices = new int[RESOLUTION * RESOLUTION];
        int k = 0;
        for ( int a = 0; a < RESOLUTION; a++ ) {
            for ( int b = 0; b < RESOLUTION; b++ ) {
                int x, y, z;
                switch ( side.axis() ) {
                    case 0  -> { x = fixed; y = a; z = b; }
                    case 1  -> { y = fixed; x = a; z = b; }
                    default -> { z = fixed; x = a; y = b; }
                }
                indices[k++] = indexOf(x, y, z);
            }
        }
        return indices;
    }

    /** @return The linear index for grid coordinate {@code (x, y, z)}. */
    public static int indexOf( int x, int y, int z ) {
        if ( x < 0 || y < 0 || z < 0 || x >= RESOLUTION || y >= RESOLUTION || z >= RESOLUTION )
            throw new IndexOutOfBoundsException("Cell (" + x + ", " + y + ", " + z + ") is outside the " + RESOLUTION + "^3 node.");
        return x + y * RESOLUTION + z * RESOLUTION * RESOLUTION;
    }

    public WorldSector sector( int index ) {
        return sectors.get(index);
    }

    public WorldSector sector( int x, int y, int z ) {
        return sectors.get(indexOf(x, y, z));
    }

    /** @return A copy of this node with the sector at {@code index} replaced. */
    public WorldTreeNode withSector( int index, WorldSector sector ) {
        return new WorldTreeNode(sectors.setAt(index, sector));
    }

    public WorldTreeNode withSector( int x, int y, int z, WorldSector sector ) {
        return withSector(indexOf(x, y, z), sector);
    }
}