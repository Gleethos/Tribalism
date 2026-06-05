package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;
import app.engine.primitives.VecF64;
import org.jspecify.annotations.Nullable;
import sprouts.Association;
import sprouts.Tuple;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 *  The whole world as a single immutable value.
 *  <p>
 *  A world is just two things: the {@code root} of the spatial {@link WorldSector}
 *  tree, and an {@code entities} lookup from id to {@link Entity}. The tree is used
 *  purely for positional queries (it holds only {@link WorldTreeEntityId}s), while
 *  the association is the authoritative store of the actual entities. Keeping the
 *  two in sync is the job of {@link #withEntity} / {@link #withoutEntity}.
 *  <p>
 *  This value is what an update loop transforms from one tick to the next; nothing
 *  is ever mutated in place.
 *
 *  @param root     The root sector of the world tree.
 *  @param entities The lookup from entity id to the actual entity.
 */
public record World(
    WorldSector root,
    Association<Long, Entity> entities
) {
    /** A sensible default cap on how deep an entity may fall into the tree. */
    public static final int DEFAULT_MAX_DEPTH = 8;

    /** @return An empty world whose root covers {@code bounds}, made of nothing. */
    public static World of( BoundsF64 bounds ) {
        return new World(WorldSector.empty(bounds), Association.between(Long.class, Entity.class));
    }

    /** @return A world over the given {@code root} with no entities yet. */
    public static World of( WorldSector root ) {
        return new World(root, Association.between(Long.class, Entity.class));
    }

    public Optional<Entity> entity( long id ) {
        return entities.get(id);
    }

    public Tuple<Entity> allEntities() {
        return entities.values();
    }

    public World withRoot( WorldSector newRoot ) {
        return new World(newRoot, entities);
    }

    /**
     *  Adds (or replaces) an entity, keeping the tree and the lookup in sync: the
     *  entity's {@link WorldTreeEntityId} falls down into the tree, and the entity
     *  itself is stored in the lookup.
     */
    public World withEntity( Entity entity, int maxDepth ) {
        WorldSector newRoot = root.insert(entity.treeId(), maxDepth);
        return new World(newRoot, entities.put(entity.id(), entity));
    }

    public World withEntity( Entity entity ) {
        return withEntity(entity, DEFAULT_MAX_DEPTH);
    }

    /** Removes an entity from both the tree and the lookup. */
    public World withoutEntity( Entity entity, int maxDepth ) {
        WorldSector newRoot = root.remove(entity.treeId(), maxDepth);
        return new World(newRoot, entities.remove(entity.id()));
    }

    public World withoutEntity( Entity entity ) {
        return withoutEntity(entity, DEFAULT_MAX_DEPTH);
    }

    /**
     *  Re-places an entity whose bounds have changed: removes the old positional
     *  handle from the tree and inserts the updated one, then stores the new entity.
     *  This is the typical move performed during an update tick.
     *
     *  @param previous The entity as it currently sits in the world.
     *  @param updated  The new state of the same entity (same id, possibly new bounds).
     */
    public World withMovedEntity( Entity previous, Entity updated, int maxDepth ) {
        WorldSector newRoot = root.remove(previous.treeId(), maxDepth).insert(updated.treeId(), maxDepth);
        return new World(newRoot, entities.put(updated.id(), updated));
    }

    // ---- Visibility traversal for rendering -------------------------------------

    /** The occlusion coverage grid's tile size in pixels (coarser = faster, less culling). */
    private static final int COVERAGE_TILE = 16;

    /** What a {@link #collectSectorsForRendering visibility traversal} did, for HUDs and tests. */
    public record RenderStats(
        int sectorsCollected,
        int occlusionCulledSectors
    ) {}

    /**
     *  Walks the world tree from the given camera's viewpoint and hands every sector
     *  worth drawing to {@code collector}, applying every visibility decision itself so
     *  that no renderer (or test) has to traverse the tree:
     *  <ul>
     *      <li><b>Frustum culling</b> &mdash; a sector outside the view volume (and its
     *          whole sub-tree) is skipped.</li>
     *      <li><b>Occlusion culling</b> &mdash; the walk proceeds <b>near&nbsp;&rarr;&nbsp;far</b>;
     *          a fully-{@link WorldSector#isSolidOpaque() solid} sector marks its screen
     *          silhouette into a {@link CoverageGrid} as it is collected, and any later
     *          (farther) sector whose screen rectangle is already fully covered is
     *          skipped, sub-tree and all.</li>
     *      <li><b>Level of detail</b> &mdash; a sector smaller on screen than
     *          {@code refineThresholdPx} is collected as one coarse box
     *          ({@code wantsDetail == false}) instead of being refined into its children.</li>
     *  </ul>
     *  How a collected sector becomes pixels (a box, a mesh, &hellip;) is entirely the
     *  {@link SectorDrawCollector collector}'s business; this method only decides
     *  <i>what</i> is visible, never <i>how</i> it looks.
     *
     *  @param camera           The viewpoint to render from.
     *  @param width            The viewport width in pixels.
     *  @param height           The viewport height in pixels.
     *  @param refineThresholdPx The on-screen edge size, in pixels, above which a sector
     *                           is refined into its children rather than drawn as one box.
     *  @param collector         Receives each (potentially) visible sector.
     *  @return Counts describing what the traversal collected and culled.
     */
    public RenderStats collectSectorsForRendering(
        CameraF64 camera, int width, int height, double refineThresholdPx, SectorDrawCollector collector
    ) {
        ViewInfo view = ViewInfo.of(camera, width, height);
        RenderTraversal traversal = new RenderTraversal(
                view, new CoverageGrid(width, height, COVERAGE_TILE), refineThresholdPx, collector);
        traversal.collect(root);
        return new RenderStats(traversal.collected, traversal.occlusionCulled);
    }

    /**
     *  The approximate on-screen size, in pixels, that an object of the given
     *  {@code edgeLength} occupies at the given {@code distance} from the camera.
     *  This is the pure heart of the level-of-detail decision.
     */
    public static double projectedEdgePixels( double edgeLength, double distance, double focalLengthPx ) {
        if ( distance <= 0 )
            return Double.POSITIVE_INFINITY;
        return edgeLength * focalLengthPx / distance;
    }

    /** @return The focal length in pixels for a camera rendered into a viewport of the given height. */
    public static double focalLengthPx( CameraF64 camera, int viewportHeight ) {
        return (viewportHeight / 2.0) / Math.tan(camera.fovYRadians() / 2.0);
    }

    /** Holds the per-frame walk state so the recursion stays a set of small methods. */
    private static final class RenderTraversal
    {
        private final ViewInfo view;
        private final CoverageGrid coverage;
        private final double refineThresholdPx;
        private final SectorDrawCollector collector;
        private int collected;
        private int occlusionCulled;

        RenderTraversal( ViewInfo view, CoverageGrid coverage, double refineThresholdPx, SectorDrawCollector collector ) {
            this.view = view;
            this.coverage = coverage;
            this.refineThresholdPx = refineThresholdPx;
            this.collector = collector;
        }

        void collect( WorldSector sector ) {
            if ( !view.frustum().intersects(sector.bounds()) )
                return; // outside the view: prune this sector and its whole sub-tree.

            double[][] corners = project8(sector.bounds());
            if ( corners != null && coverage.isOccluded(minOf(corners, 0), minOf(corners, 1),
                                                        maxOf(corners, 0), maxOf(corners, 1)) ) {
                occlusionCulled++;
                return; // fully hidden behind nearer solid geometry: prune the sub-tree.
            }

            if ( sector.isSolidOpaque() ) {
                // A perfect occluder: collect it as a single box (its mesh would just be the
                // shell anyway) and record its silhouette so it blocks what is behind.
                collector.collect(sector, false, view);
                collected++;
                if ( corners != null )
                    coverage.markOccluder(corners);
                return;
            }

            double distance = view.camera().position().distance(sector.bounds().center());
            boolean wantsDetail = projectedEdgePixels(maxEdge(sector.bounds()), distance, view.focalLengthPx()) > refineThresholdPx;

            collector.collect(sector, wantsDetail, view);
            collected++;

            if ( sector.isLeaf() )
                return;
            if ( sector.hasOnlyLeafChildren() )
                return;

            // Recurse, nearest child first, so nearer occluders are marked before farther
            // siblings are tested.
            WorldTreeNode node = sector.children();
            Integer[] order = new Integer[WorldTreeNode.SECTOR_COUNT];
            double[] dist = new double[WorldTreeNode.SECTOR_COUNT];
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
                order[i] = i;
                dist[i] = view.camera().position().distance(node.sector(i).bounds().center());
            }
            Arrays.sort(order, Comparator.comparingDouble(i -> dist[i]));
            for ( int i : order )
                collect(node.sector(i));
        }

        /** @return The 8 corners of {@code bounds} projected to screen, or {@code null} if any is behind the camera. */
        private @Nullable double[][] project8( BoundsF64 bounds ) {
            VecF64 lo = bounds.min(), hi = bounds.max();
            double[][] screen = new double[8][];
            for ( int i = 0; i < 8; i++ ) {
                VecF64 corner = VecF64.of(
                        (i & 1) == 0 ? lo.x() : hi.x(),
                        (i & 2) == 0 ? lo.y() : hi.y(),
                        (i & 4) == 0 ? lo.z() : hi.z()
                );
                screen[i] = view.project(corner);
                if ( screen[i] == null )
                    return null;
            }
            return screen;
        }

        private static double minOf( double[][] pts, int axis ) {
            double m = Double.POSITIVE_INFINITY;
            for ( double[] p : pts ) m = Math.min(m, p[axis]);
            return m;
        }

        private static double maxOf( double[][] pts, int axis ) {
            double m = Double.NEGATIVE_INFINITY;
            for ( double[] p : pts ) m = Math.max(m, p[axis]);
            return m;
        }

        private static double maxEdge( BoundsF64 bounds ) {
            VecF64 size = bounds.size();
            return Math.max(size.x(), Math.max(size.y(), size.z()));
        }
    }
}