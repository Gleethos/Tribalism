package app.engine.world;

import app.engine.primitives.BoundsF64;
import sprouts.Tuple;

/**
 *  A node in the world tree: a CPU-cache-friendly, immutable {@link Tuple} of
 *  exactly {@link #SECTION_COUNT} {@link WorldSection}s arranged as a perfect
 *  {@link #RESOLUTION}&times;{@link #RESOLUTION}&times;{@link #RESOLUTION} cube.
 *  <p>
 *  The data structure is inspired by Hash Array Mapped Tries rather than a
 *  classic oct-tree: instead of branching by 2 on each axis, a node branches by
 *  {@code 8} on each axis at once, which keeps the tree shallow and the child
 *  array contiguous and cache-friendly. Sections are stored in
 *  {@code x + y*RESOLUTION + z*RESOLUTION^2} order.
 *  <p>
 *  Because the node is built on a persistent tuple, replacing a single section
 *  ({@link #withSection(int, WorldSection)}) shares all untouched structure with
 *  the original node.
 *
 *  @param sections The {@link #SECTION_COUNT} child sections, in linear order.
 */
public record WorldTreeNode(
    Tuple<WorldSection> sections
) {
    /** The number of sub-divisions along each axis of a node. */
    public static final int RESOLUTION = 8;

    /** The total number of sections in a node, i.e. {@code RESOLUTION^3 == 512}. */
    public static final int SECTION_COUNT = RESOLUTION * RESOLUTION * RESOLUTION;

    public WorldTreeNode {
        if ( sections.size() != SECTION_COUNT )
            throw new IllegalArgumentException(
                    "A world tree node must contain exactly " + SECTION_COUNT + " sections, but got " + sections.size() + "."
                );
    }

    /**
     *  Builds a node by subdividing {@code bounds} into a regular grid of
     *  {@link #SECTION_COUNT} leaf sections, each filled with the same {@code ether}.
     *  This is how a single section is "split" into finer detail.
     *
     *  @param bounds The region the whole node covers.
     *  @param ether  The material mixture every freshly created leaf inherits.
     */
    public static WorldTreeNode uniform( BoundsF64 bounds, WorldSectionEtherData ether ) {
        Tuple<BoundsF64> cells = bounds.subdivide(RESOLUTION);
        WorldSection[] sections = new WorldSection[SECTION_COUNT];
        for ( int i = 0; i < SECTION_COUNT; i++ )
            sections[i] = WorldSection.leaf(cells.get(i), ether);
        return new WorldTreeNode(Tuple.of(WorldSection.class, sections));
    }

    /** @return The linear index for grid coordinate {@code (x, y, z)}. */
    public static int indexOf( int x, int y, int z ) {
        if ( x < 0 || y < 0 || z < 0 || x >= RESOLUTION || y >= RESOLUTION || z >= RESOLUTION )
            throw new IndexOutOfBoundsException("Cell (" + x + ", " + y + ", " + z + ") is outside the " + RESOLUTION + "^3 node.");
        return x + y * RESOLUTION + z * RESOLUTION * RESOLUTION;
    }

    public WorldSection section( int index ) {
        return sections.get(index);
    }

    public WorldSection section( int x, int y, int z ) {
        return sections.get(indexOf(x, y, z));
    }

    /** @return A copy of this node with the section at {@code index} replaced. */
    public WorldTreeNode withSection( int index, WorldSection section ) {
        return new WorldTreeNode(sections.setAt(index, section));
    }

    public WorldTreeNode withSection( int x, int y, int z, WorldSection section ) {
        return withSection(indexOf(x, y, z), section);
    }
}