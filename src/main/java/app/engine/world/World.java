package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.CameraF64;
import app.engine.primitives.VecF64;
import app.engine.world.gen.WorldGenerator;
import org.jspecify.annotations.Nullable;
import sprouts.Association;
import sprouts.Pair;
import sprouts.Tuple;
import sprouts.ValueSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 *  The whole world as a single immutable value.
 *  <p>
 *  A world ties together:
 *  <ul>
 *      <li>the {@code root} of the spatial {@link WorldSector} tree (positional
 *          queries; it holds only {@link WorldTreeEntityId}s),</li>
 *      <li>an {@code entities} lookup from id to {@link Entity} (the authoritative
 *          store of actual entities, cameras included),</li>
 *      <li>the {@link Screen}s it renders to, each referencing a camera by id,</li>
 *      <li>the accumulated per-screen input state (which keys are currently held),
 *          encapsulated &mdash; events only report <i>changes</i>, so the world must
 *          remember held state between {@link #update updates}, and</li>
 *      <li>(optionally) its own {@link WorldGenerator}, which {@link #update} uses to
 *          build terrain around cameras on demand.</li>
 *  </ul>
 *  It is a {@code class} rather than a {@code record} precisely so these last pieces can
 *  be encapsulated and so the type has room to grow; it nonetheless remains a
 *  <b>value</b>: immutable, every operation returns a new world, and
 *  {@code equals}/{@code hashCode} are defined purely by its fields.
 *  <p>
 *  {@link #update(EngineInputs)} is the function an engine loop applies each tick to
 *  fold input into world state: free-fly camera control via {@link CameraFlight}, then
 *  generating the world around every camera within the generator's reach.
 */
public final class World
{
    /** A sensible default cap on how deep an entity may fall into the tree. */
    public static final int DEFAULT_MAX_DEPTH = 8;

    private final WorldSector _root;
    private final Association<Long, Entity> _entities;
    private final Association<ScreenId, Screen> _screens;
    private final Association<ScreenId, ScreenInputState> _inputStates;
    private final @Nullable WorldGenerator _generator;
    /** Indices of the root's top-level cells already generated, so they are not regenerated. */
    private final ValueSet<Integer> _generatedChunks;

    private World(
        WorldSector root,
        Association<Long, Entity> entities,
        Association<ScreenId, Screen> screens,
        Association<ScreenId, ScreenInputState> inputStates,
        @Nullable WorldGenerator generator,
        ValueSet<Integer> generatedChunks
    ) {
        _root            = Objects.requireNonNull(root);
        _entities        = Objects.requireNonNull(entities);
        _screens         = Objects.requireNonNull(screens);
        _inputStates     = Objects.requireNonNull(inputStates);
        _generator       = generator;
        _generatedChunks = Objects.requireNonNull(generatedChunks);
    }

    /** @return A copy of this world with the given core state, preserving its generator and generated-chunk set. */
    private World copy(
        WorldSector root,
        Association<Long, Entity> entities,
        Association<ScreenId, Screen> screens,
        Association<ScreenId, ScreenInputState> inputStates
    ) {
        return new World(root, entities, screens, inputStates, _generator, _generatedChunks);
    }

    /** @return An empty world whose root covers {@code bounds}, made of nothing, with no generator. */
    public static World of( BoundsF64 bounds ) {
        return of(WorldSector.empty(bounds));
    }

    /** @return A world over the given {@code root} with no entities, screens or generator yet. */
    public static World of( WorldSector root ) {
        return new World(
                root,
                Association.between(Long.class, Entity.class),
                Association.between(ScreenId.class, Screen.class),
                Association.between(ScreenId.class, ScreenInputState.class),
                null,
                ValueSet.of(Integer.class)
        );
    }

    /**
     *  @return A world over {@code region} that generates itself on demand with {@code generator}.
     *          The root is pre-divided into a grid of empty top-level cells (the "chunks");
     *          {@link #update} fills the cells near cameras, within the generator's
     *          {@link WorldGenerator#generationDistance() reach}, as cameras come and go.
     */
    public static World of( BoundsF64 region, WorldGenerator generator ) {
        return new World(
                WorldSector.empty(region).subdivide(),
                Association.between(Long.class, Entity.class),
                Association.between(ScreenId.class, Screen.class),
                Association.between(ScreenId.class, ScreenInputState.class),
                Objects.requireNonNull(generator),
                ValueSet.of(Integer.class)
        );
    }

    // ---- Core accessors ---------------------------------------------------------

    public WorldSector root() { return _root; }

    public Association<Long, Entity> entities() { return _entities; }

    public Optional<Entity> entity( long id ) {
        return _entities.get(id);
    }

    public Tuple<Entity> allEntities() {
        return _entities.values();
    }

    /** @return The camera entity with the given id, if one exists and it is a camera. */
    public Optional<Entity.CameraEntity> camera( long id ) {
        return _entities.get(id)
                        .filter(e -> e instanceof Entity.CameraEntity)
                        .map(e -> (Entity.CameraEntity) e);
    }

    /** @return The screen with the given id, if the world has one. */
    public Optional<Screen> screen( ScreenId id ) {
        return _screens.get(id);
    }

    /** @return Every screen the world currently knows about. */
    public Tuple<Screen> allScreens() {
        return _screens.values();
    }

    /** @return The world's generator, if it has one (worlds can also be purely hand-built). */
    public Optional<WorldGenerator> generator() {
        return Optional.ofNullable(_generator);
    }

    // ---- Entity transforms (tree + lookup kept in sync) -------------------------

    public World withRoot( WorldSector newRoot ) {
        return copy(newRoot, _entities, _screens, _inputStates);
    }

    /**
     *  Adds (or replaces) an entity, keeping the tree and the lookup in sync: the
     *  entity's {@link WorldTreeEntityId} falls down into the tree, and the entity
     *  itself is stored in the lookup.
     */
    public World withEntity( Entity entity, int maxDepth ) {
        WorldSector newRoot = _root.insert(entity.treeId(), maxDepth);
        return copy(newRoot, _entities.put(entity.id(), entity), _screens, _inputStates);
    }

    public World withEntity( Entity entity ) {
        return withEntity(entity, DEFAULT_MAX_DEPTH);
    }

    /** Removes an entity from both the tree and the lookup. */
    public World withoutEntity( Entity entity, int maxDepth ) {
        WorldSector newRoot = _root.remove(entity.treeId(), maxDepth);
        return copy(newRoot, _entities.remove(entity.id()), _screens, _inputStates);
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
        WorldSector newRoot = _root.remove(previous.treeId(), maxDepth).insert(updated.treeId(), maxDepth);
        return copy(newRoot, _entities.put(updated.id(), updated), _screens, _inputStates);
    }

    // ---- Cameras ----------------------------------------------------------------

    /**
     *  Creates (or replaces) a camera entity with the given id.
     *  <p>
     *  Unlike a {@link Entity.VoxelEntity}, a camera has no voxel presence, so it lives
     *  only in the entity lookup and is <i>not</i> placed into the spatial tree &mdash;
     *  nothing queries cameras positionally, and keeping one out of the tree avoids
     *  churning it as the camera flies around every frame.
     */
    public World createCamera( long id, CameraF64 camera ) {
        return copy(_root, _entities.put(id, Entity.CameraEntity.of(id, camera)), _screens, _inputStates);
    }

    /** Removes the camera entity with the given id (screens bound to it then render nothing). */
    public World destroyCamera( long id ) {
        return copy(_root, _entities.remove(id), _screens, _inputStates);
    }

    // ---- Screens ----------------------------------------------------------------

    /** Adds (or replaces) a screen. */
    public World withScreen( Screen screen ) {
        return copy(_root, _entities, _screens.put(screen.id(), screen), _inputStates);
    }

    /** Creates a new, unbound screen of the given id and pixel size. */
    public World createScreen( ScreenId id, int width, int height ) {
        return withScreen(Screen.of(id, width, height));
    }

    /** Removes a screen and any input state accumulated for it. */
    public World destroyScreen( ScreenId id ) {
        return copy(_root, _entities, _screens.remove(id), _inputStates.remove(id));
    }

    /** @return This world with the screen resized (its bound camera, if any, is kept). */
    public World resizeScreen( ScreenId id, int width, int height ) {
        Screen screen = _screens.get(id).orElseThrow(() ->
                new IllegalArgumentException("No screen with id " + id + " to resize."));
        return withScreen(screen.withSize(width, height));
    }

    /**
     *  Binds a screen to a camera entity, by id. The same camera may be bound to several
     *  screens. The camera need not exist yet (the binding simply renders nothing until
     *  it does), but the screen must.
     */
    public World bindScreenToCamera( ScreenId screenId, long cameraId ) {
        Screen screen = _screens.get(screenId).orElseThrow(() ->
                new IllegalArgumentException("No screen with id " + screenId + " to bind."));
        return withScreen(screen.withCamera(cameraId));
    }

    // ---- The update step --------------------------------------------------------

    /**
     *  Folds one step of input into a new world state.
     *  <p>
     *  For each screen named in {@code inputs}, its {@link ScreenInputs event log} is
     *  applied: key presses/releases update that screen's held-key state, and held keys
     *  plus accumulated cursor movement are turned into <b>camera mutations</b> on the
     *  screen's bound camera via {@link CameraFlight}. Screens not mentioned in the
     *  inputs are left untouched.
     *  <p>
     *  Then, if the world has a {@link WorldGenerator}, it <b>builds itself around every
     *  camera</b>: any not-yet-generated region within the generator's
     *  {@link WorldGenerator#generationDistance() reach} of a camera is generated, so a
     *  camera always has something to look at as it moves (see {@link #generateAroundCameras}).
     *
     *  @param inputs What happened on each screen, and how much time elapsed.
     *  @return The world advanced by one step.
     */
    public World update( EngineInputs inputs ) {
        World result = this;
        for ( Pair<ScreenId, ScreenInputs> entry : inputs.screens() )
            result = result.applyScreenInputs(entry.first(), entry.second(), inputs.dtSeconds());
        return result.generateAroundCameras();
    }

    /**
     *  Generates, around every camera, any region within the generator's reach that has
     *  not been generated yet &mdash; the lazy "stream the world in as you move" step.
     *  <p>
     *  The root is a grid of top-level cells (created by {@link #of(BoundsF64, WorldGenerator)});
     *  each is a "chunk". For every camera, each chunk whose nearest point lies within
     *  {@link WorldGenerator#generationDistance()} of the camera is generated (once) and
     *  spliced in, and its index remembered so it is never regenerated. A world with no
     *  generator, or whose root is not a chunk grid, is returned unchanged.
     */
    private World generateAroundCameras() {
        if ( _generator == null )
            return this;
        WorldTreeNode node = _root.children();
        if ( node == null )
            return this; // not a chunk-grid root (e.g. a hand-built world): nothing to stream.

        double reach = _generator.generationDistance();
        ValueSet<Integer> generated = _generatedChunks;
        boolean changed = false;
        for ( Entity entity : _entities.values() ) {
            if ( !(entity instanceof Entity.CameraEntity cameraEntity) )
                continue;
            VecF64 eye = cameraEntity.camera().position();
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
                if ( generated.contains(i) )
                    continue;
                BoundsF64 cell = node.sector(i).bounds();
                if ( distanceToBounds(eye, cell) <= reach ) {
                    node = node.withSector(i, _generator.generate(cell));
                    generated = generated.add(i);
                    changed = true;
                }
            }
        }
        if ( !changed )
            return this;
        WorldSector newRoot = _root.withChildren(node).aggregated();
        return new World(newRoot, _entities, _screens, _inputStates, _generator, generated);
    }

    /** @return The distance from {@code point} to the nearest point of {@code bounds} (0 if inside). */
    private static double distanceToBounds( VecF64 point, BoundsF64 bounds ) {
        return point.distance(point.clamp(bounds.min(), bounds.max()));
    }

    private World applyScreenInputs( ScreenId screenId, ScreenInputs screenInputs, double dtSeconds ) {
        ScreenInputState state = _inputStates.get(screenId).orElse(ScreenInputState.empty());
        ValueSet<Key> held = state.heldKeys();
        double lookDx = 0, lookDy = 0;
        for ( ScreenInputEvent event : screenInputs.events() ) {
            switch ( event ) {
                case ScreenInputEvent.KeyPressed e   -> held = held.add(e.key());
                case ScreenInputEvent.KeyReleased e  -> held = held.remove(e.key());
                case ScreenInputEvent.CursorMoved e  -> { lookDx += e.deltaX(); lookDy += e.deltaY(); }
                case ScreenInputEvent.CursorDown ignored -> { /* reserved for future picking/interaction */ }
                case ScreenInputEvent.CursorUp ignored   -> { /* reserved */ }
                case ScreenInputEvent.Scrolled ignored   -> { /* reserved for future zoom/dolly */ }
            }
        }
        Association<ScreenId, ScreenInputState> newStates = _inputStates.put(screenId, new ScreenInputState(held));

        Association<Long, Entity> newEntities = _entities;
        Optional<Screen> screen = screen(screenId);
        if ( screen.isPresent() && screen.get().camera().isPresent() ) {
            long cameraId = screen.get().camera().getAsLong();
            Optional<Entity.CameraEntity> cam = camera(cameraId);
            if ( cam.isPresent() ) {
                CameraF64 moved = CameraFlight.fly(cam.get().camera(), held, lookDx, lookDy, maxEdge(_root.bounds()), dtSeconds);
                newEntities = _entities.put(cameraId, Entity.CameraEntity.of(cameraId, moved));
            }
        }
        return copy(_root, newEntities, _screens, newStates);
    }

    /** The currently-held keys for one screen; remembered between updates since events report only changes. */
    private record ScreenInputState(ValueSet<Key> heldKeys) {
        static ScreenInputState empty() {
            return new ScreenInputState(ValueSet.of(Key.class));
        }
    }

    // ---- Visibility traversal for rendering -------------------------------------

    /** The occlusion coverage grid's tile size in pixels (coarser = faster, less culling). */
    private static final int COVERAGE_TILE = 16;

    /** What a {@link #collectSectorsForRendering visibility traversal} did, for HUDs and tests. */
    public record RenderStats(
        int sectorsCollected,
        int occlusionCulledSectors
    ) {
        /** Nothing collected (e.g. an unbound or unknown screen). */
        public static final RenderStats NONE = new RenderStats(0, 0);
    }

    /**
     *  Walks the world tree from the viewpoint of the camera bound to {@code screenId}
     *  and hands every sector worth drawing to {@code collector}, applying every
     *  visibility decision itself so that no renderer (or test) has to traverse the tree:
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
     *  The camera and viewport size come entirely from the screen (the camera's aspect is
     *  overridden to the screen's). If the screen is unknown, unbound, or its camera no
     *  longer exists, nothing is collected ({@link RenderStats#NONE}). How a collected
     *  sector becomes pixels is the {@link SectorDrawCollector collector}'s business; this
     *  method only decides <i>what</i> is visible.
     *
     *  @param screenId          The screen (hence camera + viewport) to render from.
     *  @param refineThresholdPx The on-screen edge size, in pixels, above which a sector
     *                           is refined into its children rather than drawn as one box.
     *  @param collector         Receives each (potentially) visible sector.
     *  @return Counts describing what the traversal collected and culled.
     */
    public RenderStats collectSectorsForRendering(
        ScreenId screenId, double refineThresholdPx, SectorDrawCollector collector
    ) {
        Optional<Screen> maybeScreen = screen(screenId);
        if ( maybeScreen.isEmpty() )
            return RenderStats.NONE;
        Screen screen = maybeScreen.get();
        if ( screen.camera().isEmpty() )
            return RenderStats.NONE;
        Optional<Entity.CameraEntity> cam = camera(screen.camera().getAsLong());
        if ( cam.isEmpty() )
            return RenderStats.NONE;

        CameraF64 camera = cam.get().camera().withAspect(screen.aspect());
        ViewInfo view = ViewInfo.of(camera, screen.width(), screen.height());
        RenderTraversal traversal = new RenderTraversal(
                view, new CoverageGrid(screen.width(), screen.height(), COVERAGE_TILE), refineThresholdPx, collector);
        traversal.collect(_root);
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

    private static double maxEdge( BoundsF64 bounds ) {
        VecF64 size = bounds.size();
        return Math.max(size.x(), Math.max(size.y(), size.z()));
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
        private double @Nullable [][] project8( BoundsF64 bounds ) {
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
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof World other) ) return false;
        return _root.equals(other._root)
            && _entities.equals(other._entities)
            && _screens.equals(other._screens)
            && _inputStates.equals(other._inputStates)
            && Objects.equals(_generator, other._generator)
            && _generatedChunks.equals(other._generatedChunks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_root, _entities, _screens, _inputStates, _generator, _generatedChunks);
    }

    @Override
    public String toString() {
        return "World[entities=" + _entities.size() + ", screens=" + _screens.size() + ']';
    }
}