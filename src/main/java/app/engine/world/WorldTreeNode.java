package app.engine.world;

import app.engine.primitives.BoundsF64;
import sprouts.Tuple;

import java.util.Arrays;

/**
 *  A node in the world tree: a CPU-cache-friendly, immutable array of exactly
 *  {@link #SECTOR_COUNT} {@link WorldSector}s arranged as a perfect
 *  {@link #RESOLUTION}&times;{@link #RESOLUTION}&times;{@link #RESOLUTION} cube.
 *  <p>
 *  The data structure is inspired by Hash Array Mapped Tries rather than a
 *  classic oct-tree: instead of branching by 2 on each axis, a node branches by
 *  {@code 8} on each axis at once, which keeps the tree shallow and the child
 *  array contiguous and cache-friendly. Sectors are stored in
 *  {@code x + y*RESOLUTION + z*RESOLUTION^2} order.
 *  <p>
 *  <b>Representation.</b> The children are a plain {@code WorldSector[]}, not a
 *  persistent {@code Tuple}: {@link #sector(int)} is the single hottest read in the
 *  engine (every render walk and every aggregation touches all 512 children), so it
 *  must be a bare array access with no wrapper overhead. It is a {@code class} rather
 *  than a {@code record} purely to <i>encapsulate</i> that array (owned, never exposed)
 *  while staying an immutable <b>value</b>: {@link #withSector} returns a new node and
 *  {@code equals}/{@code hashCode} compare the children element-wise. Replacing one
 *  sector copies the 512-element array &mdash; cheaper than it sounds, and writes are
 *  far rarer than reads.
 */
public final class WorldTreeNode
{
    /** The number of sub-divisions along each axis of a node. */
    public static final int RESOLUTION = 8;

    /** The total number of sectors in a node, i.e. {@code RESOLUTION^3 == 512}. */
    public static final int SECTOR_COUNT = RESOLUTION * RESOLUTION * RESOLUTION;

    /** The {@link #SECTOR_COUNT} child sectors in linear order. Owned, never exposed. */
    private final WorldSector[] _sectors;

    /**
     *  Wraps the given child array as a node. <b>Takes ownership</b> of {@code sectors}:
     *  callers must hand over a freshly built array and not retain or mutate it afterwards.
     *
     *  @param sectors Exactly {@link #SECTOR_COUNT} child sectors, in linear order.
     */
    public WorldTreeNode( WorldSector[] sectors ) {
        if ( sectors.length != SECTOR_COUNT )
            throw new IllegalArgumentException(
                    "A world tree node must contain exactly " + SECTOR_COUNT + " sectors, but got " + sectors.length + "."
                );
        _sectors = sectors;
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
        return new WorldTreeNode(sectors);
    }

    /** @return The number of child sectors (always {@link #SECTOR_COUNT}). */
    public int size() {
        return _sectors.length;
    }

    /**
     *  @return The linear indices of the {@code RESOLUTION^2} child cells that lie
     *          on the given outer {@code side} of this node (e.g. {@link Side#POS_Y}
     *          yields the top {@code 8x8} layer). This is what per-side
     *          level-of-detail aggregation walks: only a face's boundary children
     *          contribute to that face of the parent.
     */
    public static int[] boundaryCells( Side side ) {
        int fixed = side.isPositive() ? RESOLUTION - 1 : 0;
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
        return _sectors[index];
    }

    public WorldSector sector( int x, int y, int z ) {
        return _sectors[indexOf(x, y, z)];
    }

    /** @return A copy of this node with the sector at {@code index} replaced. */
    public WorldTreeNode withSector( int index, WorldSector sector ) {
        WorldSector[] copy = _sectors.clone();
        copy[index] = sector;
        return new WorldTreeNode(copy);
    }

    public WorldTreeNode withSector( int x, int y, int z, WorldSector sector ) {
        return withSector(indexOf(x, y, z), sector);
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof WorldTreeNode other) ) return false;
        return Arrays.equals(_sectors, other._sectors);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_sectors);
    }

    @Override
    public String toString() {
        return "WorldTreeNode[" + SECTOR_COUNT + " sectors]";
    }
}