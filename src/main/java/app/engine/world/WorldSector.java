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
 *  A sector knows the {@link BoundsF64 region} it occupies and what it is made
 *  of ({@link WorldSectorEtherData}). It positionally holds the
 *  {@link WorldTreeEntityId entities} and {@link LightSource lights} that fall
 *  within it, plus any {@link LightTrace light traces} radiating through it.
 *  <p>
 *  Crucially it is <i>recursive</i>: a sector may reference {@code null} (making
 *  it a leaf &mdash; effectively a single voxel) or a {@link WorldTreeNode} of
 *  {@value WorldTreeNode#SECTOR_COUNT} finer sub-sectors. This gives the
 *  structure practically infinite resolution into the small and, via
 *  {@link #aggregated() ether aggregation}, an automatic level-of-detail story
 *  into the large.
 *  <p>
 *  Every operation returns a new sector; nothing is ever mutated.
 *
 *  @param bounds      The region this sector occupies in world space.
 *  @param ether       The material mixture this sector is made of.
 *  @param entities    The entities positioned within this sector.
 *  @param lights      The lights positioned within this sector.
 *  @param lightTraces The light traces currently radiating through this sector.
 *  @param children    The finer sub-sectors, or {@code null} if this is a leaf voxel.
 */
public record WorldSector(
    BoundsF64 bounds,
    WorldSectorEtherData ether,
    ValueSet<WorldTreeEntityId> entities,
    ValueSet<LightSource> lights,
    Tuple<LightTrace> lightTraces,
    @Nullable WorldTreeNode children
) {
    /** @return An empty leaf sector over {@code bounds} made of the given {@code ether}. */
    public static WorldSector leaf( BoundsF64 bounds, WorldSectorEtherData ether ) {
        return new WorldSector(
                bounds,
                ether,
                ValueSet.of(WorldTreeEntityId.class),
                ValueSet.of(LightSource.class),
                Tuple.of(LightTrace.class),
                null
            );
    }

    /** @return An empty leaf sector over {@code bounds} made of nothing. */
    public static WorldSector empty( BoundsF64 bounds ) {
        return leaf(bounds, WorldSectorEtherData.empty());
    }

    /** @return {@code true} if this sector has no finer sub-sectors. */
    public boolean isLeaf() {
        return children == null;
    }

    public WorldSector withEther( WorldSectorEtherData newEther ) {
        return new WorldSector(bounds, newEther, entities, lights, lightTraces, children);
    }

    public WorldSector withChildren( @Nullable WorldTreeNode newChildren ) {
        return new WorldSector(bounds, ether, entities, lights, lightTraces, newChildren);
    }

    public WorldSector withEntity( WorldTreeEntityId entity ) {
        return new WorldSector(bounds, ether, entities.add(entity), lights, lightTraces, children);
    }

    public WorldSector withoutEntity( WorldTreeEntityId entity ) {
        return new WorldSector(bounds, ether, entities.remove(entity), lights, lightTraces, children);
    }

    public WorldSector withLight( LightSource light ) {
        return new WorldSector(bounds, ether, entities, lights.add(light), lightTraces, children);
    }

    public WorldSector withoutLight( LightSource light ) {
        return new WorldSector(bounds, ether, entities, lights.remove(light), lightTraces, children);
    }

    public WorldSector withLightTrace( LightTrace trace ) {
        return new WorldSector(bounds, ether, entities, lights, lightTraces.add(trace), children);
    }

    /**
     *  Splits this leaf into a {@link WorldTreeNode} of finer sub-sectors, each
     *  inheriting this sector's {@code ether}. Splitting a uniform voxel this way
     *  preserves its aggregated ether exactly, since the average of equal children
     *  is the child value. If this sector already has children it is returned
     *  unchanged.
     */
    public WorldSector subdivide() {
        if ( children != null )
            return this;
        return withChildren(WorldTreeNode.uniform(bounds, ether));
    }

    /**
     *  Inserts an entity into the tree, letting it "fall down" to the deepest
     *  sector that still fully contains its bounding box.
     *  <p>
     *  Starting from this sector, the entity descends into a child whenever it
     *  fits entirely within a single sub-cell; otherwise it comes to rest here.
     *  Missing child nodes are created on demand by {@link #subdivide() subdividing}.
     *  This keeps the tree only as deep as the entities require, and means an
     *  entity is stored exactly once, in its tightest enclosing sector.
     *
     *  @param entity         The entity (id + bounds) to place.
     *  @param remainingDepth How many further levels of subdivision are permitted.
     *  @return A new sector containing the entity somewhere in its sub-tree.
     */
    public WorldSector insert( WorldTreeEntityId entity, int remainingDepth ) {
        if ( remainingDepth <= 0 )
            return withEntity(entity);

        int cell = soleContainingCell(entity.bounds());
        if ( cell < 0 )
            return withEntity(entity); // spans several sub-cells (or none): rest here.

        WorldSector subdivided = subdivide();
        WorldTreeNode node = subdivided.children();
        WorldSector child = node.sector(cell).insert(entity, remainingDepth - 1);
        return subdivided.withChildren(node.withSector(cell, child));
    }

    /**
     *  Removes an entity from the tree, mirroring {@link #insert(WorldTreeEntityId, int)}.
     *  <p>
     *  The search follows the same fall-down path the entity would have taken: it
     *  is removed from this sector if present, otherwise we descend into the sole
     *  child that could contain it. If the entity is not found the tree is returned
     *  unchanged. This is what lets an entity be re-placed when its bounds change:
     *  remove it along its old path, then {@link #insert insert} it along the new one.
     *
     *  @param entity         The entity to remove.
     *  @param remainingDepth How many further levels of subdivision to search.
     *  @return A new sector without the entity, or this sector if it was absent.
     */
    public WorldSector remove( WorldTreeEntityId entity, int remainingDepth ) {
        if ( entities.contains(entity) )
            return withoutEntity(entity);
        if ( children == null || remainingDepth <= 0 )
            return this;
        int cell = soleContainingCell(entity.bounds());
        if ( cell < 0 )
            return this;
        WorldSector child = children.sector(cell).remove(entity, remainingDepth - 1);
        return withChildren(children.withSector(cell, child));
    }

    /**
     *  Recomputes the level-of-detail ether of this sector from the bottom up.
     *  <p>
     *  Leaves keep their own ether. A branching sector first aggregates each of
     *  its children, then sets its own ether to the {@link WorldSectorEtherData#average
     *  average} of those children &mdash; so a whole sub-tree can be summarized by
     *  the single representative voxel at its root.
     *
     *  @return A sector whose ether (and that of every descendant) reflects the
     *          averaged material of its sub-tree.
     */
    public WorldSector aggregated() {
        if ( children == null )
            return this;

        WorldSector[] aggregatedChildren = new WorldSector[WorldTreeNode.SECTOR_COUNT];
        WorldSectorEtherData[] childEther = new WorldSectorEtherData[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
            WorldSector child = children.sector(i).aggregated();
            aggregatedChildren[i] = child;
            childEther[i] = child.ether();
        }
        WorldTreeNode aggregatedNode = new WorldTreeNode(Tuple.of(WorldSector.class, aggregatedChildren));
        WorldSectorEtherData averaged = WorldSectorEtherData.average(Tuple.of(WorldSectorEtherData.class, childEther));
        return withChildren(aggregatedNode).withEther(averaged);
    }

    /**
     *  Determines which single sub-cell of an {@code 8x8x8} subdivision of this
     *  sector fully contains {@code region}.
     *
     *  @return The linear index of the sole containing cell, or {@code -1} if the
     *          region straddles a cell boundary or falls outside this sector.
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