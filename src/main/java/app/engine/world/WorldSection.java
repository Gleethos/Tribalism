package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import org.jspecify.annotations.Nullable;
import sprouts.Tuple;
import sprouts.ValueSet;

/**
 *  A single cubic cell of the world, and the recursive building block of the
 *  whole engine.
 *  <p>
 *  A section knows the {@link BoundsF64 region} it occupies and what it is made
 *  of ({@link WorldSectionEtherData}). It positionally holds the
 *  {@link WorldTreeEntityId entities} and {@link LightSource lights} that fall
 *  within it, plus any {@link LightTrace light traces} radiating through it.
 *  <p>
 *  Crucially it is <i>recursive</i>: a section may reference {@code null} (making
 *  it a leaf &mdash; effectively a single voxel) or a {@link WorldTreeNode} of
 *  {@value WorldTreeNode#SECTION_COUNT} finer sub-sections. This gives the
 *  structure practically infinite resolution into the small and, via
 *  {@link #aggregated() ether aggregation}, an automatic level-of-detail story
 *  into the large.
 *  <p>
 *  Every operation returns a new section; nothing is ever mutated.
 *
 *  @param bounds      The region this section occupies in world space.
 *  @param ether       The material mixture this section is made of.
 *  @param entities    The entities positioned within this section.
 *  @param lights      The lights positioned within this section.
 *  @param lightTraces The light traces currently radiating through this section.
 *  @param children    The finer sub-sections, or {@code null} if this is a leaf voxel.
 */
public record WorldSection(
    BoundsF64 bounds,
    WorldSectionEtherData ether,
    ValueSet<WorldTreeEntityId> entities,
    ValueSet<LightSource> lights,
    Tuple<LightTrace> lightTraces,
    @Nullable WorldTreeNode children
) {
    /** @return An empty leaf section over {@code bounds} made of the given {@code ether}. */
    public static WorldSection leaf( BoundsF64 bounds, WorldSectionEtherData ether ) {
        return new WorldSection(
                bounds,
                ether,
                ValueSet.of(WorldTreeEntityId.class),
                ValueSet.of(LightSource.class),
                Tuple.of(LightTrace.class),
                null
            );
    }

    /** @return An empty leaf section over {@code bounds} made of nothing. */
    public static WorldSection empty( BoundsF64 bounds ) {
        return leaf(bounds, WorldSectionEtherData.empty());
    }

    /** @return {@code true} if this section has no finer sub-sections. */
    public boolean isLeaf() {
        return children == null;
    }

    public WorldSection withEther( WorldSectionEtherData newEther ) {
        return new WorldSection(bounds, newEther, entities, lights, lightTraces, children);
    }

    public WorldSection withChildren( @Nullable WorldTreeNode newChildren ) {
        return new WorldSection(bounds, ether, entities, lights, lightTraces, newChildren);
    }

    public WorldSection withEntity( WorldTreeEntityId entity ) {
        return new WorldSection(bounds, ether, entities.add(entity), lights, lightTraces, children);
    }

    public WorldSection withoutEntity( WorldTreeEntityId entity ) {
        return new WorldSection(bounds, ether, entities.remove(entity), lights, lightTraces, children);
    }

    public WorldSection withLight( LightSource light ) {
        return new WorldSection(bounds, ether, entities, lights.add(light), lightTraces, children);
    }

    public WorldSection withoutLight( LightSource light ) {
        return new WorldSection(bounds, ether, entities, lights.remove(light), lightTraces, children);
    }

    public WorldSection withLightTrace( LightTrace trace ) {
        return new WorldSection(bounds, ether, entities, lights, lightTraces.add(trace), children);
    }

    /**
     *  Splits this leaf into a {@link WorldTreeNode} of finer sub-sections, each
     *  inheriting this section's {@code ether}. Splitting a uniform voxel this way
     *  preserves its aggregated ether exactly, since the average of equal children
     *  is the child value. If this section already has children it is returned
     *  unchanged.
     */
    public WorldSection subdivide() {
        if ( children != null )
            return this;
        return withChildren(WorldTreeNode.uniform(bounds, ether));
    }

    /**
     *  Inserts an entity into the tree, letting it "fall down" to the deepest
     *  section that still fully contains its bounding box.
     *  <p>
     *  Starting from this section, the entity descends into a child whenever it
     *  fits entirely within a single sub-cell; otherwise it comes to rest here.
     *  Missing child nodes are created on demand by {@link #subdivide() subdividing}.
     *  This keeps the tree only as deep as the entities require, and means an
     *  entity is stored exactly once, in its tightest enclosing section.
     *
     *  @param entity         The entity (id + bounds) to place.
     *  @param remainingDepth How many further levels of subdivision are permitted.
     *  @return A new section containing the entity somewhere in its sub-tree.
     */
    public WorldSection insert( WorldTreeEntityId entity, int remainingDepth ) {
        if ( remainingDepth <= 0 )
            return withEntity(entity);

        int cell = soleContainingCell(entity.bounds());
        if ( cell < 0 )
            return withEntity(entity); // spans several sub-cells (or none): rest here.

        WorldSection subdivided = subdivide();
        WorldTreeNode node = subdivided.children();
        WorldSection child = node.section(cell).insert(entity, remainingDepth - 1);
        return subdivided.withChildren(node.withSection(cell, child));
    }

    /**
     *  Recomputes the level-of-detail ether of this section from the bottom up.
     *  <p>
     *  Leaves keep their own ether. A branching section first aggregates each of
     *  its children, then sets its own ether to the {@link WorldSectionEtherData#average
     *  average} of those children &mdash; so a whole sub-tree can be summarized by
     *  the single representative voxel at its root.
     *
     *  @return A section whose ether (and that of every descendant) reflects the
     *          averaged material of its sub-tree.
     */
    public WorldSection aggregated() {
        if ( children == null )
            return this;

        WorldSection[] aggregatedChildren = new WorldSection[WorldTreeNode.SECTION_COUNT];
        WorldSectionEtherData[] childEther = new WorldSectionEtherData[WorldTreeNode.SECTION_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTION_COUNT; i++ ) {
            WorldSection child = children.section(i).aggregated();
            aggregatedChildren[i] = child;
            childEther[i] = child.ether();
        }
        WorldTreeNode aggregatedNode = new WorldTreeNode(Tuple.of(WorldSection.class, aggregatedChildren));
        WorldSectionEtherData averaged = WorldSectionEtherData.average(Tuple.of(WorldSectionEtherData.class, childEther));
        return withChildren(aggregatedNode).withEther(averaged);
    }

    /**
     *  Determines which single sub-cell of an {@code 8x8x8} subdivision of this
     *  section fully contains {@code region}.
     *
     *  @return The linear index of the sole containing cell, or {@code -1} if the
     *          region straddles a cell boundary or falls outside this section.
     */
    private int soleContainingCell( BoundsF64 region ) {
        if ( !bounds.contains(region) )
            return -1;
        int[] minCell = cellOf(region.min());
        int[] maxCell = cellOf(region.max());
        if ( minCell[0] == maxCell[0] && minCell[1] == maxCell[1] && minCell[2] == maxCell[2] )
            return WorldTreeNode.indexOf(minCell[0], minCell[1], minCell[2]);
        return -1;
    }

    /** @return The {@code [x, y, z]} grid coordinate of {@code point}, clamped into the node. */
    private int[] cellOf( VecF64 point ) {
        int res = WorldTreeNode.RESOLUTION;
        VecF64 size = bounds.size();
        int x = clampCell((int) Math.floor((point.x() - bounds.min().x()) / (size.x() / res)), res);
        int y = clampCell((int) Math.floor((point.y() - bounds.min().y()) / (size.y() / res)), res);
        int z = clampCell((int) Math.floor((point.z() - bounds.min().z()) / (size.z() / res)), res);
        return new int[]{ x, y, z };
    }

    private static int clampCell( int value, int res ) {
        if ( value < 0 )    return 0;
        if ( value >= res ) return res - 1;
        return value;
    }
}