package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import app.engine.util.Lazy;
import org.jspecify.annotations.Nullable;
import sprouts.Tuple;
import sprouts.ValueSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *  A single cubic cell of the world, and the recursive building block of the
 *  whole engine.
 *  <p>
 *  A sector knows the {@link BoundsF64 region} it occupies and what it looks like /
 *  is made of ({@link WorldSectorEtherData}). It positionally holds the
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
 *  Every operation returns a new sector; nothing is ever mutated. The class still
 *  behaves as a <b>value</b> &mdash; immutable, with {@code equals}/{@code hashCode}
 *  defined purely by its six fields. It is a {@code class} rather than a
 *  {@code record} only so it can encapsulate one derived, lazily-computed field:
 *  its {@link #insets() side insets}, summarized from its sub-sectors.
 */
public final class WorldSector {

    private final BoundsF64 _bounds;
    private final WorldSectorEtherData _ether;
    private final ValueSet<WorldTreeEntityId> _entities;
    private final ValueSet<LightSource> _lights;
    private final Tuple<LightTrace> _lightTraces;
    private final @Nullable WorldTreeNode _children;

    // Derived, memoized. Purely a function of the fields above (specifically the
    // children), so it is excluded from equals/hashCode and computed at most once.
    private final Lazy<SideInsets> _insets;
    private final Lazy<Boolean> _solidOpaque;
    private final Lazy<Boolean> _hasOnlyLeafChildren;

    // Cached hash. The value equals/hashCode are deep (they walk the whole sub-tree),
    // so memoizing the hash makes a sector a cheap key for value-keyed maps (e.g. the
    // renderer's mesh cache). 0 means "not yet computed"; the recompute is benign.
    private int _hash;

    public WorldSector(
        BoundsF64 bounds,
        WorldSectorEtherData ether,
        ValueSet<WorldTreeEntityId> entities,
        ValueSet<LightSource> lights,
        Tuple<LightTrace> lightTraces,
        @Nullable WorldTreeNode children
    ) {
        _bounds      = Objects.requireNonNull(bounds);
        _ether       = Objects.requireNonNull(ether);
        _entities    = Objects.requireNonNull(entities);
        _lights      = Objects.requireNonNull(lights);
        _lightTraces = Objects.requireNonNull(lightTraces);
        _children    = children;
        _insets      = Lazy.of(this::computeInsets);
        _solidOpaque = Lazy.of(this::computeSolidOpaque);
        _hasOnlyLeafChildren = Lazy.of(this::computeHasOnlyLeafChildren);
    }

    public BoundsF64 bounds()                     { return _bounds; }
    public WorldSectorEtherData ether()           { return _ether; }
    public ValueSet<WorldTreeEntityId> entities() { return _entities; }
    public ValueSet<LightSource> lights()         { return _lights; }
    public Tuple<LightTrace> lightTraces()        { return _lightTraces; }
    public @Nullable WorldTreeNode children()     { return _children; }

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
        return _children == null;
    }

    /**
     *  @return {@code true} if this is a branch whose every child is a {@link #isLeaf()
     *          leaf} &mdash; i.e. a solid {@value WorldTreeNode#RESOLUTION}-cubed block of
     *          voxels with no deeper structure. A leaf itself returns {@code false}
     *          (it has no children). The renderer treats such a block as the unit it
     *          meshes and the traversal treats it as a place to stop descending.
     */
    public boolean hasOnlyLeafChildren() {
        return _hasOnlyLeafChildren.get();
    }

    private boolean computeHasOnlyLeafChildren() {
        if ( _children == null )
            return false;
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            if ( !_children.sector(i).isLeaf() )
                return false;
        return true;
    }

    /** @return {@code true} if this sector is empty space &mdash; invisible on every face. */
    public boolean isFullyTransparent() {
        return _ether.isInvisible();
    }

    /**
     *  @return {@code true} if every voxel inside this sector is fully opaque (no air,
     *          no holes) &mdash; a perfect occluder. Derived bottom-up and memoized: a
     *          leaf is solid iff its ether {@link WorldSectorEtherData#isFullyOpaque()
     *          is fully opaque}, a branch iff <i>all</i> its children are. The renderer
     *          uses this both to draw such a sector as a single box (its mesh would
     *          just be the shell) and to let it block whatever is behind it.
     */
    public boolean isSolidOpaque() {
        return _solidOpaque.get();
    }

    /**
     *  The per-face {@link SideInsets insets} of this sector, summarized from its
     *  sub-sectors and memoized lazily (computed at most once, on first access).
     *  <p>
     *  A leaf has no sub-sectors to traverse, so all of its insets are {@code 0}.
     *  A branch derives each face's inset with an <b>inward-moving</b> algorithm:
     *  starting at that face it peels off whole child layers while every sub-sector
     *  in the layer is {@link #isFullyTransparent() fully transparent}, adding one
     *  full layer of inset each time. At the first layer that holds any content it
     *  adds the <i>smallest</i> matching-side inset among that layer's non-transparent
     *  sub-sectors (so the inset refines below child granularity) and stops. The
     *  result is a fraction in {@code [0, 1]} of this sector's extent: {@code 0} =
     *  content reaches the face, {@code 1} = that whole half is empty.
     *
     *  @return How far content is recessed from each of the six faces.
     */
    public SideInsets insets() {
        return _insets.get();
    }

    public WorldSector withEther( WorldSectorEtherData newEther ) {
        return new WorldSector(_bounds, newEther, _entities, _lights, _lightTraces, _children);
    }

    public WorldSector withChildren( @Nullable WorldTreeNode newChildren ) {
        return new WorldSector(_bounds, _ether, _entities, _lights, _lightTraces, newChildren);
    }

    public WorldSector withEntity( WorldTreeEntityId entity ) {
        return new WorldSector(_bounds, _ether, _entities.add(entity), _lights, _lightTraces, _children);
    }

    public WorldSector withoutEntity( WorldTreeEntityId entity ) {
        return new WorldSector(_bounds, _ether, _entities.remove(entity), _lights, _lightTraces, _children);
    }

    public WorldSector withLight( LightSource light ) {
        return new WorldSector(_bounds, _ether, _entities, _lights.add(light), _lightTraces, _children);
    }

    public WorldSector withoutLight( LightSource light ) {
        return new WorldSector(_bounds, _ether, _entities, _lights.remove(light), _lightTraces, _children);
    }

    public WorldSector withLightTrace( LightTrace trace ) {
        return new WorldSector(_bounds, _ether, _entities, _lights, _lightTraces.add(trace), _children);
    }

    /**
     *  Splits this leaf into a {@link WorldTreeNode} of finer sub-sectors, each
     *  inheriting this sector's {@code ether}. Splitting a uniform voxel this way
     *  preserves its aggregated ether exactly, since the average of equal children
     *  is the child value. If this sector already has children it is returned
     *  unchanged.
     */
    public WorldSector subdivide() {
        if ( _children != null )
            return this;
        return withChildren(WorldTreeNode.uniform(_bounds, _ether));
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
        if ( _entities.contains(entity) )
            return withoutEntity(entity);
        if ( _children == null || remainingDepth <= 0 )
            return this;
        int cell = soleContainingCell(entity.bounds());
        if ( cell < 0 )
            return this;
        WorldSector child = _children.sector(cell).remove(entity, remainingDepth - 1);
        return withChildren(_children.withSector(cell, child));
    }

    /**
     *  Recomputes the level-of-detail ether of this sector from the bottom up.
     *  <p>
     *  Leaves keep their own ether. A branching sector first aggregates each of its
     *  children, then summarizes its own ether in two ways:
     *  <ul>
     *      <li><b>Appearance, per side:</b> each face of this super-sector is the
     *          {@link TextureProfile#average average} of the matching face of only
     *          the sub-sectors lying on that face (its
     *          {@link WorldTreeNode#boundaryCells boundary layer}). The hidden
     *          interior is never seen and so never contributes.</li>
     *      <li><b>Material, per cube:</b> the children's material ids are
     *          {@link MaterialId#merge merged} &mdash; the shared id if they all
     *          agree, otherwise {@link MaterialId#diverse() Diverse}.</li>
     *  </ul>
     *  (The per-face {@link #insets() insets} are derived the same way, but lazily on
     *  demand rather than here.) This is what lets a whole sub-tree collapse into one
     *  visually faithful representative voxel.
     *
     *  @return A sector whose ether (and that of every descendant) reflects the
     *          per-side appearance and merged material of its sub-tree.
     */
    public WorldSector aggregated() {
        if ( _children == null )
            return this;

        WorldSector[] aggregatedChildren = new WorldSector[WorldTreeNode.SECTOR_COUNT];
        List<MaterialId> childMaterials = new ArrayList<>(WorldTreeNode.SECTOR_COUNT);
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
            WorldSector child = _children.sector(i).aggregated();
            aggregatedChildren[i] = child;
            childMaterials.add(child.ether().material());
        }
        WorldTreeNode aggregatedNode = new WorldTreeNode(aggregatedChildren);

        WorldSectorEtherData ether = WorldSectorEtherData.empty().withMaterial(MaterialId.merge(childMaterials));
        for ( Side side : Side.values() ) {
            int[] boundary = WorldTreeNode.boundaryCells(side);
            List<TextureProfile> faces = new ArrayList<>(boundary.length);
            for ( int cell : boundary )
                faces.add(aggregatedNode.sector(cell).ether().sideOf(side));
            ether = ether.withSide(side, TextureProfile.average(faces));
        }
        return withChildren(aggregatedNode).withEther(ether);
    }

    private boolean computeSolidOpaque() {
        if ( _children == null )
            return _ether.isFullyOpaque();
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            if ( !_children.sector(i).isSolidOpaque() )
                return false;
        return true;
    }

    // ---- Inset computation ------------------------------------------------------

    private SideInsets computeInsets() {
        if ( _children == null )
            return SideInsets.none();

        boolean[] transparent = new boolean[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            transparent[i] = _children.sector(i).isFullyTransparent();

        SideInsets insets = SideInsets.none();
        for ( Side side : Side.values() )
            insets = insets.with(side, insetFromSide(side, transparent));
        return insets;
    }

    /** Walks layers inward from {@code side}, accumulating empty layers (and a final partial layer). */
    private double insetFromSide( Side side, boolean[] transparent ) {
        int res = WorldTreeNode.RESOLUTION;
        double layers = 0;
        for ( int depth = 0; depth < res; depth++ ) {
            boolean allTransparent = true;
            double minChildInset = 1.0;
            for ( int idx : WorldTreeNode.layerCells(side, depth) ) {
                if ( !transparent[idx] ) {
                    allTransparent = false;
                    double childInset = _children.sector(idx).insets().forSide(side);
                    if ( childInset < minChildInset )
                        minChildInset = childInset;
                }
            }
            if ( allTransparent ) {
                layers += 1.0; // a fully empty layer recesses content by a full child cell.
            } else {
                layers += minChildInset; // content begins partway into this layer; refine below it.
                break;
            }
        }
        return layers / res;
    }

    /**
     *  Determines which single sub-cell of an {@code 8x8x8} subdivision of this
     *  sector fully contains {@code region}.
     *
     *  @return The linear index of the sole containing cell, or {@code -1} if the
     *          region straddles a cell boundary or falls outside this sector.
     */
    private int soleContainingCell( BoundsF64 region ) {
        if ( !_bounds.contains(region) )
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
        VecF64 size = _bounds.size();
        int x = clampCell((int) Math.floor((point.x() - _bounds.min().x()) / (size.x() / res)), res);
        int y = clampCell((int) Math.floor((point.y() - _bounds.min().y()) / (size.y() / res)), res);
        int z = clampCell((int) Math.floor((point.z() - _bounds.min().z()) / (size.z() / res)), res);
        return new int[]{ x, y, z };
    }

    private static int clampCell( int value, int res ) {
        if ( value < 0 )    return 0;
        if ( value >= res ) return res - 1;
        return value;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof WorldSector other) ) return false;
        return _bounds.equals(other._bounds)
            && _ether.equals(other._ether)
            && _entities.equals(other._entities)
            && _lights.equals(other._lights)
            && _lightTraces.equals(other._lightTraces)
            && Objects.equals(_children, other._children);
    }

    @Override
    public int hashCode() {
        int h = _hash;
        if ( h == 0 ) {
            h = Objects.hash(_bounds, _ether, _entities, _lights, _lightTraces, _children);
            _hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return "WorldSector[bounds=" + _bounds + ", ether=" + _ether
             + ", entities=" + _entities.size() + ", lights=" + _lights.size()
             + ", lightTraces=" + _lightTraces.size() + ", leaf=" + isLeaf() + ']';
    }
}