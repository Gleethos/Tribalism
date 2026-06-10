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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

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
    /**
     *  The camera positions at which the last refinement walk <b>settled</b> (changed nothing), or
     *  {@code null} if the world is not (yet) settled. It is the anchor for the cheap movement gate in
     *  {@link #refineAroundCameras}, and is a derived <i>optimization hint</i> only &mdash; deliberately
     *  excluded from {@link #equals}/{@link #hashCode}, like the lazily-cached matrices on a camera.
     */
    private final @Nullable Tuple<VecF64> _settledEyes;

    private World(
        WorldSector root,
        Association<Long, Entity> entities,
        Association<ScreenId, Screen> screens,
        Association<ScreenId, ScreenInputState> inputStates,
        @Nullable WorldGenerator generator,
        @Nullable Tuple<VecF64> settledEyes
    ) {
        _root            = Objects.requireNonNull(root);
        _entities        = Objects.requireNonNull(entities);
        _screens         = Objects.requireNonNull(screens);
        _inputStates     = Objects.requireNonNull(inputStates);
        _generator       = generator;
        _settledEyes     = settledEyes;
    }

    /**
     *  @return A copy of this world with the given core state, preserving its generator. The settled-eyes
     *          gate anchor is kept (the gate re-validates it against the live camera positions, so a copy
     *          that moves or adds a camera is handled correctly there, not here).
     */
    private World copy(
        WorldSector root,
        Association<Long, Entity> entities,
        Association<ScreenId, Screen> screens,
        Association<ScreenId, ScreenInputState> inputStates
    ) {
        return new World(root, entities, screens, inputStates, _generator, _settledEyes);
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
                null
        );
    }

    /**
     *  @return An <b>infinite</b> world that generates itself on demand with {@code generator}.
     *          It starts as a single coarse sector at the origin (described top-down by the
     *          generator, with no sub-tree); {@link #update} grows the root to cover each camera's
     *          view range and then <b>refines the tree toward the cameras</b> &mdash; finer detail
     *          where they are close, collapsing back to coarse where they are far &mdash; a single
     *          continuous level-of-detail octree with no fixed bounds (see {@link #refineAroundCameras}).
     */
    public static World of( WorldGenerator generator ) {
        Objects.requireNonNull(generator);
        // Start with one large coarse root covering the view range, centred on the origin and sized to a
        // whole power of RESOLUTION chunks (so sub-cells land exactly on chunk-sized nodes). update()
        // refines detail toward cameras and grows the root only if one roams beyond it.
        double size = generator.chunkSize();
        while ( size < 2 * VIEW_DISTANCE )
            size *= WorldTreeNode.RESOLUTION;
        double half = size / 2;
        BoundsF64 rootBounds = BoundsF64.of(VecF64.of(-half, -half, -half), VecF64.of(half, half, half));
        return new World(
                WorldSector.leaf(rootBounds, generator.etherOf(rootBounds)),
                Association.between(Long.class, Entity.class),
                Association.between(ScreenId.class, Screen.class),
                Association.between(ScreenId.class, ScreenInputState.class),
                generator,
                null
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

    /**
     *  @return The deepest existing sector containing {@code point} &mdash; a finely refined sector
     *          (small bounds) where detail has been materialized near a camera, or a coarse sector
     *          (large bounds) where it has not. Empty if {@code point} lies outside the current root.
     *          A spatial point query into the tree; the bounds size of the result tells you how
     *          refined that location currently is.
     */
    public Optional<WorldSector> sectorAt( VecF64 point ) {
        if ( !_root.bounds().contains(point) )
            return Optional.empty();
        WorldSector sector = _root;
        while ( !sector.isLeaf() )
            sector = sector.children().sector(cellContaining(sector.bounds(), point));
        return Optional.of(sector);
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
     *  Then, if the world has a {@link WorldGenerator}, it <b>refines the tree around every
     *  camera</b>: the root is grown to cover each camera's view range, then detail is materialized
     *  where a camera is close and collapsed back to coarse where it is far &mdash; a single
     *  continuous level-of-detail octree (see {@link #refineAroundCameras}).
     *
     *  @param inputs What happened on each screen, and how much time elapsed.
     *  @return The world advanced by one step.
     */
    public World update( EngineInputs inputs ) {
        World result = this;
        for ( Pair<ScreenId, ScreenInputs> entry : inputs.screens() )
            result = result.applyScreenInputs(entry.first(), entry.second(), inputs.dtSeconds());
        return result.refineAroundCameras();
    }

    // ---- Level-of-detail refinement (the infinite, streaming world) -------------

    /** How far around each camera the root is grown so coarse terrain exists out to here (world units). */
    private static final double VIEW_DISTANCE = 2048.0;

    /**
     *  The one level-of-detail knob, shared by world refinement and the renderer so they always agree:
     *  how many cells of detail a sector wants across its edge when a camera is one sector-<i>radius</i>
     *  away (see {@link #detailCells}). It is <b>resolution-independent</b> &mdash; detail depends only on
     *  a sector's size relative to its distance, never on pixels or viewport height &mdash; so the world
     *  looks the same at any window size (a 4K screen just renders the same geometry more sharply, instead
     *  of descending eight times deeper and collapsing into giant boxes, which was the old pixel-based bug).
     *  Bigger = more detail and deeper descent. At {@code 40} the renderer descends into a sector within
     *  ~2.5 of its edges and the world refines surface sectors within ~7 edges &mdash; the well-behaved
     *  cone the engine was hand-tuned to before pixels crept in.
     */
    private static final double LOD_DETAIL = 40.0;

    /**
     *  The {@link #detailCells} value at and above which a sector must have children: exactly the
     *  res-1&rarr;res-8 boundary of {@link #meshResolutionFor} ({@code sqrt(RESOLUTION)}), so every sector
     *  the renderer meshes finer than a single flat box (res-8 <i>from its sub-sectors</i>, showing the
     *  surface shape) is guaranteed to have sub-sectors to mesh from. Refinement is further restricted to
     *  {@link WorldGenerator#isHomogeneous non-homogeneous} (surface) sectors, which bounds the cone to the
     *  2D terrain surface.
     */
    private static final double REFINE_CELLS = Math.sqrt(WorldTreeNode.RESOLUTION);

    /** Collapse hysteresis: a refined sector is only dropped once it shrinks this far below {@link #REFINE_CELLS}. */
    private static final double COLLAPSE_HYSTERESIS = 1.5;

    /** How many sectors one {@link #update} may materialize (subdivide or generate), so a tick never stalls. */
    private static final int REFINE_BUDGET_PER_UPDATE = 8;

    /**
     *  Movement gate, as a fraction of a chunk: once a walk has <b>settled</b> (changed nothing), the
     *  whole tree walk is skipped on later ticks until some camera drifts farther than
     *  {@code chunkSize ×} this from where it was when the world settled. A small move barely shifts any
     *  level-of-detail boundary, so re-walking the entire structure every tick for it is wasted work;
     *  this is what keeps a near-stationary or slowly drifting camera cheap. Larger = cheaper but detail
     *  lags farther behind a moving camera before it refreshes; smaller = crisper but more frequent walks.
     */
    private static final double REFINE_REANCHOR_FRACTION = 0.5;

    /**
     *  Refines the level-of-detail octree around every camera &mdash; the unified "build the world as
     *  you move" step that replaces the old chunk grid, making the world both <b>infinite</b> and
     *  <b>memory-bounded</b>.
     *  <p>
     *  First the root is {@link #growToContain grown} to cover a {@link #VIEW_DISTANCE} box around each
     *  camera (so far terrain exists to refine into). Then the tree is walked and each sector, by how many
     *  {@link #REFINE_CELLS screen cells} it spans on the nearest camera, is:
     *  <ul>
     *      <li><b>refined</b> &mdash; a coarse sector big enough on screen (more than {@link #REFINE_CELLS}
     *          cells) is subdivided into eight finer {@link #coarseLeaf coarse children} (each
     *          described top-down by {@link WorldGenerator#etherOf}); at the chunk level it is instead
     *          {@link WorldGenerator#generate generated} to full voxel detail;</li>
     *      <li><b>kept</b> as-is; or</li>
     *      <li><b>collapsed</b> &mdash; a refined sector now small on screen (past the hysteresis band)
     *          drops its sub-tree back to a single coarse leaf, freeing memory. Deterministic terrain
     *          makes this lossless: approaching again re-refines it.</li>
     *  </ul>
     *  The materialized sectors form a <b>cone of detail</b> around each camera &mdash; full voxels up
     *  close, progressively coarser {@code etherOf} leaves outward &mdash; so memory is bounded by the
     *  cone, not the distance travelled. Materializations are budgeted
     *  ({@link #REFINE_BUDGET_PER_UPDATE}, nearest first) so one tick never stalls; the rest stream in
     *  over following ticks. A world with no generator, or no camera to anchor the cone, is unchanged.
     *  <p>
     *  <b>Movement gate.</b> Walking the whole tree every tick is wasted when nothing about the detail
     *  needs to change. So once a walk <b>settles</b> (changes nothing) the camera positions are
     *  remembered in {@link #_settledEyes}, and subsequent ticks skip the walk entirely until some camera
     *  drifts past {@code chunkSize × }{@link #REFINE_REANCHOR_FRACTION}. The gate only engages <i>after</i>
     *  settling, so budgeted detail still streams to completion while a freshly-arrived camera holds still.
     */
    private World refineAroundCameras() {
        WorldGenerator generator = _generator;
        if ( generator == null )
            return this;
        Tuple<VecF64> eyes = cameraEyes();
        if ( eyes.isEmpty() )
            return this;

        // Settled, and no camera has drifted far enough to shift a level-of-detail boundary: skip the
        // entire walk (returned by identity, so every cache downstream keeps hitting). Resize cannot shift
        // anything because the metric is resolution-independent, so positions alone anchor the gate.
        double reanchor = generator.chunkSize() * REFINE_REANCHOR_FRACTION;
        if ( _settledEyes != null && withinDistance(eyes, _settledEyes, reanchor) )
            return this;

        WorldSector root = _root;
        double v = VIEW_DISTANCE;
        for ( VecF64 eye : eyes )
            root = growToContain(generator, root, BoundsF64.of(eye.sub(VecF64.of(v, v, v)), eye.add(VecF64.of(v, v, v))));

        int[] budget = { REFINE_BUDGET_PER_UPDATE };
        WorldSector refined = refine(generator, root, eyes, budget);
        if ( refined == _root )
            // Settled at the current camera positions: anchor the gate to them so the next ticks can skip
            // the walk. Same root instance, so the renderer's sector-keyed caches keep hitting; this is the
            // one new instance, after which the gate above returns by identity.
            return new World(_root, _entities, _screens, _inputStates, generator, eyes);
        // Still streaming detail: clear the anchor so we keep walking next tick until it settles.
        return new World(refined, _entities, _screens, _inputStates, generator, null);
    }

    /** @return Whether every camera in {@code eyes} is within {@code maxDistance} of its position in {@code anchor} (false if the counts differ). */
    private static boolean withinDistance( Tuple<VecF64> eyes, Tuple<VecF64> anchor, double maxDistance ) {
        if ( eyes.size() != anchor.size() )
            return false;
        double maxSq = maxDistance * maxDistance;
        for ( int i = 0; i < eyes.size(); i++ )
            if ( eyes.get(i).distanceSquared(anchor.get(i)) > maxSq )
                return false;
        return true;
    }

    /** @return The positions of all camera entities (the anchors of the detail cone). */
    private Tuple<VecF64> cameraEyes() {
        List<VecF64> eyes = new ArrayList<>();
        for ( Entity entity : _entities.values() )
            if ( entity instanceof Entity.CameraEntity cameraEntity )
                eyes.add(cameraEntity.camera().position());
        return Tuple.of(VecF64.class, eyes);
    }

    /**
     *  Refines (or collapses) one sub-tree toward the cameras, returning the possibly-new sub-tree.
     *  Returns the same instance when nothing changed, so an already-settled world is returned by
     *  identity from {@link #refineAroundCameras} (and the renderer's value-keyed caches keep hitting).
     */
    private static WorldSector refine( WorldGenerator generator, WorldSector node, Tuple<VecF64> eyes, int[] budget ) {
        double edge = maxEdge(node.bounds());
        // How many cells of detail this node wants across its edge on the nearest camera - the SAME metric
        // the renderer uses, so the two never disagree.
        double cells = detailCells(node.bounds(), eyes);
        // Only surface-straddling sectors carry detail; a uniform region is identical at every level, so it
        // is never worth refining (this bounds the cone to the 2D terrain surface, not the 3D volume).
        boolean wantsDetail = cells > REFINE_CELLS && !generator.isHomogeneous(node.bounds());

        if ( edge <= generator.chunkSize() ) {
            // Finest managed level. Detail here is true voxels from generate() - but only within the
            // generator's full-detail reach; farther chunks that are still big on screen stay coarse boxes
            // (the parent above already shows their shape as a res-8 mesh over its coarse-leaf children).
            if ( wantsDetail && nearestDistance(node.bounds(), eyes) < generator.generationDistance() ) {
                if ( !node.isLeaf() )
                    return node; // already a generated chunk with sub-tree detail.
                if ( budget[0] <= 0 )
                    return node; // out of budget this tick; generated next tick.
                WorldSector generated = generator.generate(node.bounds());
                if ( generated.isLeaf() )
                    return node; // homogeneous region: the coarse leaf already equals full detail — keep it (no churn).
                budget[0]--;
                return generated;
            }
            if ( !node.isLeaf() && cells < REFINE_CELLS / COLLAPSE_HYSTERESIS )
                return coarseLeaf(generator, node.bounds()); // far/small: drop the chunk's voxel detail.
            return node;
        }

        // Above the chunk level: refine into coarse children whenever the renderer would draw this node
        // finer than a single flat box (res-8 from its sub-sectors, or descend into it). Collapse when it
        // shrinks well below that on screen.
        if ( wantsDetail ) {
            if ( node.isLeaf() ) {
                if ( budget[0] <= 0 )
                    return node; // out of budget; subdivided next tick.
                budget[0]--;
                node = subdivideCoarse(generator, node);
            }
            WorldTreeNode children = node.children();
            WorldSector[] newKids = new WorldSector[WorldTreeNode.SECTOR_COUNT];
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
                newKids[i] = children.sector(i);
            boolean changed = false;
            // Nearest-first ordering exists only to spend the materialization budget on the closest
            // detail; once the budget is gone the remaining walk only detects far-collapses (which are
            // order-independent), so we skip the per-node sort entirely - that is the bulk of the walk.
            int[] order = budget[0] > 0 ? nearestFirst(children, eyes) : NATURAL_CHILD_ORDER;
            for ( int i : order ) {
                WorldSector child = children.sector(i);
                WorldSector refinedChild = refine(generator, child, eyes, budget);
                if ( refinedChild != child ) {
                    newKids[i] = refinedChild;
                    changed = true;
                }
            }
            return changed ? node.withChildren(new WorldTreeNode(newKids)) : node;
        }

        if ( !node.isLeaf() && cells < REFINE_CELLS / COLLAPSE_HYSTERESIS )
            return coarseLeaf(generator, node.bounds()); // small on screen: collapse the whole sub-tree to one coarse leaf.
        return node;
    }

    /**
     *  The one "size on screen" metric, shared by world refinement and the renderer.
     *
     *  @return How many cells of detail {@code bounds} wants across its edge from {@code eye}: {@link #LOD_DETAIL}
     *          scaled by the sector's radius over its distance (to the camera's nearest point), or
     *          {@code +Infinity} if the camera is inside it. It depends only on the sector's size relative to
     *          its distance &mdash; never on pixels &mdash; so it is identical at any viewport resolution.
     */
    private static double detailCells( BoundsF64 bounds, VecF64 eye ) {
        double distance = distanceToBounds(eye, bounds);
        if ( distance <= 0 )
            return Double.POSITIVE_INFINITY;
        return LOD_DETAIL * ( maxEdge(bounds) / 2.0 ) / distance;
    }

    /** @return {@link #detailCells(BoundsF64, VecF64)} for the <i>most-demanding</i> (largest) camera in {@code eyes}. */
    private static double detailCells( BoundsF64 bounds, Tuple<VecF64> eyes ) {
        double best = 0;
        for ( VecF64 eye : eyes )
            best = Math.max(best, detailCells(bounds, eye));
        return best;
    }

    /**
     *  @return A childless sector over {@code bounds} described top-down by the generator. It uses the
     *          <i>cheap</i> {@link WorldGenerator#representativeEtherOf} summary (not the full
     *          {@link WorldGenerator#etherOf}), because the detail cone materializes many of these and a
     *          per-side summary is not worth its cost at coarse levels &mdash; the renderer draws a
     *          childless coarse leaf as a single box anyway.
     */
    private static WorldSector coarseLeaf( WorldGenerator generator, BoundsF64 bounds ) {
        return WorldSector.leaf(bounds, generator.representativeEtherOf(bounds));
    }

    /** Subdivides a coarse leaf into eight coarse-leaf children (the node keeps its own top-down ether). */
    private static WorldSector subdivideCoarse( WorldGenerator generator, WorldSector node ) {
        Tuple<BoundsF64> cells = node.bounds().subdivide(WorldTreeNode.RESOLUTION);
        WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            kids[i] = coarseLeaf(generator, cells.get(i));
        return node.withChildren(new WorldTreeNode(kids));
    }

    /** The identity child order {0, 1, ..., SECTOR_COUNT-1}, reused (read-only) when no sort is needed. */
    private static final int[] NATURAL_CHILD_ORDER = naturalOrder();
    private static int[] naturalOrder() {
        int[] order = new int[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < order.length; i++ )
            order[i] = i;
        return order;
    }

    /** @return Child indices ordered nearest-camera first, so the materialization budget is spent on the nearest detail. */
    private static int[] nearestFirst( WorldTreeNode node, Tuple<VecF64> eyes ) {
        double[] dist = new double[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            dist[i] = nearestDistance(node.sector(i).bounds(), eyes);
        return orderByDistance(dist);
    }

    /**
     *  @return The indices {@code [0, SECTOR_COUNT)} ordered by ascending {@code dist}, sorted as packed
     *          primitives so the hot refinement walk never boxes an {@code Integer} nor allocates a
     *          {@code Comparator}. Each entry packs the (non-negative) distance's float bits into the high
     *          bits and the index into the low 9 bits, so a plain {@link Arrays#sort(long[])} orders by
     *          distance with the index as a stable tie-break.
     */
    private static int[] orderByDistance( double[] dist ) {
        long[] keyed = new long[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            keyed[i] = ( (long) Float.floatToRawIntBits((float) dist[i]) << 9 ) | i;
        Arrays.sort(keyed);
        int[] order = new int[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            order[i] = (int) ( keyed[i] & 0x1FFL );
        return order;
    }

    /** @return The distance from the nearest camera to {@code bounds} (0 if a camera is inside it). */
    private static double nearestDistance( BoundsF64 bounds, Tuple<VecF64> eyes ) {
        double best = Double.POSITIVE_INFINITY;
        for ( VecF64 eye : eyes )
            best = Math.min(best, distanceToBounds(eye, bounds));
        return best;
    }

    /** @return The distance from {@code point} to the nearest point of {@code bounds} (0 if inside). */
    private static double distanceToBounds( VecF64 point, BoundsF64 bounds ) {
        return point.distance(point.clamp(bounds.min(), bounds.max()));
    }

    /**
     *  Grows {@code root} until it contains {@code target}, re-rooting it {@value WorldTreeNode#RESOLUTION}&times;
     *  larger at each step: the old root becomes one cell of a new, larger root (placed in the corner
     *  furthest from the target so the root extends toward it), the other cells fresh
     *  {@link #coarseLeaf coarse leaves}, so all existing content keeps its world coordinates. Rare
     *  &mdash; the root already covers {@link #VIEW_DISTANCE} around the cameras &mdash; so the per-level
     *  coarse-leaf cost is acceptable.
     */
    private static WorldSector growToContain( WorldGenerator generator, WorldSector root, BoundsF64 target ) {
        int res = WorldTreeNode.RESOLUTION;
        while ( !root.bounds().contains(target) ) {
            double size = root.bounds().width();
            VecF64 oldMin = root.bounds().min();
            double newMinX = target.min().x() < oldMin.x() ? oldMin.x() - (res - 1) * size : oldMin.x();
            double newMinY = target.min().y() < oldMin.y() ? oldMin.y() - (res - 1) * size : oldMin.y();
            double newMinZ = target.min().z() < oldMin.z() ? oldMin.z() - (res - 1) * size : oldMin.z();
            double bigger = size * res;
            BoundsF64 newBounds = BoundsF64.of(VecF64.of(newMinX, newMinY, newMinZ),
                                               VecF64.of(newMinX + bigger, newMinY + bigger, newMinZ + bigger));
            int cell = cellContaining(newBounds, root.bounds().center());
            Tuple<BoundsF64> cells = newBounds.subdivide(res);
            WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT];
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
                kids[i] = i == cell ? root : coarseLeaf(generator, cells.get(i));
            root = WorldSector.leaf(newBounds, generator.etherOf(newBounds)).withChildren(new WorldTreeNode(kids));
        }
        return root;
    }

    /** @return The linear index of the {@value WorldTreeNode#RESOLUTION}-cubed sub-cell of {@code bounds} that contains {@code point}. */
    private static int cellContaining( BoundsF64 bounds, VecF64 point ) {
        int res = WorldTreeNode.RESOLUTION;
        VecF64 size = bounds.size();
        int x = clampCell((int) Math.floor((point.x() - bounds.min().x()) / (size.x() / res)), res);
        int y = clampCell((int) Math.floor((point.y() - bounds.min().y()) / (size.y() / res)), res);
        int z = clampCell((int) Math.floor((point.z() - bounds.min().z()) / (size.z() / res)), res);
        return WorldTreeNode.indexOf(x, y, z);
    }

    private static int clampCell( int value, int res ) {
        return value < 0 ? 0 : Math.min(value, res - 1);
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
                // Movement scale: a stable reference (the generator's reach for an infinite world,
                // whose root grows without bound; otherwise the fixed root's size).
                double speedScale = _generator != null ? _generator.generationDistance() : maxEdge(_root.bounds());
                CameraF64 moved = CameraFlight.fly(cam.get().camera(), held, lookDx, lookDy, speedScale, dtSeconds);
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

    /**
     *  The cap on the grid resolution a single render unit is meshed at. A power of
     *  {@link WorldTreeNode#RESOLUTION}; the renderer's mesher caps its grid at the same value.
     */
    private static final int MAX_MESH_RESOLUTION = WorldTreeNode.RESOLUTION * WorldTreeNode.RESOLUTION; // 64

    /**
     *  Above the {@code chunkSize} floor, a sector wanting at most this many {@link #detailCells cells} of
     *  detail across its edge is collapsed into <i>one</i> coarse render unit (meshed from its branch
     *  sectors); a bigger one is descended into instead, so its children carry the detail and occlusion can
     *  act between them. Tied to {@link WorldTreeNode#RESOLUTION} so a collapsed unit is meshed at one tree
     *  level (its direct children as voxels) or coarser.
     */
    private static final double LOD_COLLAPSE_CELLS = WorldTreeNode.RESOLUTION; // 8

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
     *  and hands every <i>render unit</i> worth drawing to {@code collector}, applying
     *  every visibility decision itself so that no renderer (or test) traverses the tree:
     *  <ul>
     *      <li><b>Frustum culling</b> &mdash; a sector outside the view volume (and its
     *          whole sub-tree) is skipped, as is a fully transparent (air) sub-tree.</li>
     *      <li><b>Occlusion culling</b> &mdash; the walk proceeds <b>near&nbsp;&rarr;&nbsp;far</b>;
     *          a fully-{@link WorldSector#isSolidOpaque() solid} sector marks its screen
     *          silhouette into a {@link CoverageGrid} as it is collected, and any later
     *          (farther) sector whose screen rectangle is already fully covered is
     *          skipped, sub-tree and all.</li>
     *      <li><b>Level of detail</b> &mdash; for each surviving sector the walk estimates how big it is on
     *          screen ({@link #detailCells}) and the grid resolution that keeps its cells near constant.
     *          A leaf, a solid occluder, a sector already {@code chunkSize}
     *          or smaller (the floor, meshed at full detail), or one small enough on screen to span
     *          only a few cells ({@link #LOD_COLLAPSE_CELLS}) is handed over as one render unit
     *          together with that resolution &mdash; distant sectors meshed coarsely from big blocks
     *          of branch sectors, near ones finely. A sector still big on screen above the floor is
     *          descended into so its children carry the detail (and occlusion can act between them).
     *          {@code chunkSize} is the floor below which the walk never descends, so near terrain
     *          stays batched into chunk-sized units.</li>
     *  </ul>
     *  The camera and viewport size come entirely from the screen (the camera's aspect is
     *  overridden to the screen's). If the screen is unknown, unbound, or its camera no
     *  longer exists, nothing is collected ({@link RenderStats#NONE}). How a collected
     *  unit becomes pixels is the {@link SectorDrawCollector collector}'s business; this
     *  method only decides <i>what</i> is visible.
     *
     *  @param screenId  The screen (hence camera + viewport) to render from.
     *  @param chunkSize The world-space edge size at or below which a sub-tree is handed
     *                   over whole as one render unit rather than descended into.
     *  @param collector Receives each (potentially) visible render unit.
     *  @return Counts describing what the traversal collected and culled.
     */
    public RenderStats collectSectorsForRendering(
        ScreenId screenId, double chunkSize, SectorDrawCollector collector
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
                view, new CoverageGrid(screen.width(), screen.height(), COVERAGE_TILE), chunkSize, collector);
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

    /**
     *  @return The grid resolution to mesh a unit at, given how many {@link #detailCells cells} of detail
     *          it wants across its edge: the nearest power of {@link WorldTreeNode#RESOLUTION}
     *          ({@code 1, 8, 64}), capped at {@link #MAX_MESH_RESOLUTION}. Nearest (rather than floor)
     *          keeps the cell size centred on the target across the coarse, factor-8 LoD steps.
     */
    private static int meshResolutionFor( double desiredCells ) {
        if ( desiredCells <= 1 )
            return 1;
        // A camera inside the sector gives desiredCells == +Infinity; clamp first, both to mesh the nearest
        // terrain at its finest and to avoid (int) Math.round(+Infinity) wrapping to -1 (which collapsed a
        // chunk to a single res-1 box the instant the camera entered its bounds).
        if ( desiredCells >= MAX_MESH_RESOLUTION )
            return MAX_MESH_RESOLUTION;
        int exponent = (int) Math.round(Math.log(desiredCells) / Math.log(WorldTreeNode.RESOLUTION));
        int res = 1;
        for ( int i = 0; i < exponent; i++ )
            res *= WorldTreeNode.RESOLUTION;
        return Math.min(res, MAX_MESH_RESOLUTION);
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
        private final double chunkSize;
        private final SectorDrawCollector collector;
        private int collected;
        private int occlusionCulled;

        RenderTraversal( ViewInfo view, CoverageGrid coverage, double chunkSize, SectorDrawCollector collector ) {
            this.view = view;
            this.coverage = coverage;
            this.chunkSize = chunkSize;
            this.collector = collector;
        }

        /**
         *  Walks the tree <b>strictly nearest-first</b> (best-first over a priority queue keyed by
         *  distance to each sector's bounds), which is what the occlusion {@link CoverageGrid} requires:
         *  a sector may only be tested for occlusion once <i>every</i> nearer sector has already marked
         *  its silhouette. A plain recursive depth-first walk does not give that &mdash; it expands the
         *  nearest child's <i>whole</i> sub-tree (including its far parts) before the next child, so a far
         *  occluder in a near sub-tree could be marked before a nearer sector in a far sub-tree is tested,
         *  wrongly culling the nearer one. That error concentrates at the horizon, where many depths
         *  compress into a thin screen band, so it showed up as visible geometry vanishing at the top edge.
         *  Best-first keeps the hierarchical pruning (an occluded sector's whole sub-tree is still skipped,
         *  because it is only reached after everything nearer, and is dropped before its children are queued).
         */
        void collect( WorldSector root ) {
            VecF64 eye = view.camera().position();
            PriorityQueue<Pending> frontier = new PriorityQueue<>();
            frontier.add(new Pending(distanceToBounds(eye, root.bounds()), root));
            while ( !frontier.isEmpty() ) {
                WorldSector sector = frontier.poll().sector();
                if ( sector.isVoid() )
                    continue; // empty air sub-tree: nothing to draw.
                if ( !view.frustum().intersects(sector.bounds()) )
                    continue; // outside the view: prune this sector and its whole sub-tree.

                double[][] corners = project8(sector.bounds());
                if ( corners != null && coverage.isOccluded(minOf(corners, 0), minOf(corners, 1),
                                                            maxOf(corners, 0), maxOf(corners, 1)) ) {
                    occlusionCulled++;
                    continue; // fully hidden behind nearer solid geometry: prune the sub-tree.
                }

                if ( sector.isSolidOpaque() ) {
                    // A perfect occluder: collect it as one render unit (its mesh is just the shell, so the
                    // coarsest resolution suffices) and record its silhouette so it blocks whatever is behind.
                    collector.collect(sector, view, 1);
                    collected++;
                    if ( corners != null )
                        coverage.markOccluder(corners);
                    continue;
                }

                // Level of detail: how many cells of detail does this sector want across its edge? A sector
                // small on screen is meshed coarsely (few big quads from branch sectors); a near, big one is
                // descended into so its children carry the detail. The SAME metric the world refines by.
                double cells = detailCells(sector.bounds(), eye);

                boolean atFloor = sector.isLeaf() || maxEdge(sector.bounds()) <= chunkSize;
                if ( atFloor || cells <= LOD_COLLAPSE_CELLS ) {
                    // At the chunk floor (full detail), or far enough that the whole sector is only a few
                    // cells on screen: hand it over as one unit, meshed at the chosen level of detail.
                    collector.collect(sector, view, meshResolutionFor(cells));
                    collected++;
                    continue;
                }

                // Still big on screen above the floor: queue the children so they carry the detail and
                // occlusion can act between them. They re-enter the frontier in global nearest-first order.
                WorldTreeNode node = sector.children();
                for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
                    WorldSector child = node.sector(i);
                    frontier.add(new Pending(distanceToBounds(eye, child.bounds()), child));
                }
            }
        }

        /** A sector waiting in the traversal frontier, ordered nearest-first by its distance to the camera. */
        private record Pending(double distance, WorldSector sector) implements Comparable<Pending> {
            @Override public int compareTo( Pending other ) { return Double.compare(distance, other.distance); }
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
            && Objects.equals(_generator, other._generator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_root, _entities, _screens, _inputStates, _generator);
    }

    @Override
    public String toString() {
        return "World[entities=" + _entities.size() + ", screens=" + _screens.size() + ']';
    }
}