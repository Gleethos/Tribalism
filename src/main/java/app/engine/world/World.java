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
import java.util.Comparator;
import java.util.List;
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

    private World(
        WorldSector root,
        Association<Long, Entity> entities,
        Association<ScreenId, Screen> screens,
        Association<ScreenId, ScreenInputState> inputStates,
        @Nullable WorldGenerator generator
    ) {
        _root            = Objects.requireNonNull(root);
        _entities        = Objects.requireNonNull(entities);
        _screens         = Objects.requireNonNull(screens);
        _inputStates     = Objects.requireNonNull(inputStates);
        _generator       = generator;
    }

    /** @return A copy of this world with the given core state, preserving its generator. */
    private World copy(
        WorldSector root,
        Association<Long, Entity> entities,
        Association<ScreenId, Screen> screens,
        Association<ScreenId, ScreenInputState> inputStates
    ) {
        return new World(root, entities, screens, inputStates, _generator);
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
                generator
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
     *  How close a camera must be, as a multiple of a sector's own edge, for that sector to be refined
     *  into finer children. It is set <b>larger than the renderer's draw threshold</b> (a node is drawn
     *  when it spans ~{@link #LOD_COLLAPSE_CELLS} {@link #TARGET_CELL_PX}-cells, i.e. at
     *  {@code dist ≈ edge × focal / (LOD_COLLAPSE_CELLS × TARGET_CELL_PX)}) so that the coarse sector the
     *  renderer actually <b>draws still has children</b> &mdash; and is therefore greedy-meshed at res-8
     *  <i>from its sub-sectors</i> (showing the surface shape) instead of as a single flat box. Affordable
     *  at this size only because refinement is restricted to {@link WorldGenerator#isHomogeneous
     *  non-homogeneous} (surface) sectors, which bounds the cone to the 2D terrain surface.
     */
    private static final double REFINE_FACTOR = 7.0;

    /** Collapse hysteresis: a refined sector is only dropped once a camera is this much past {@link #REFINE_FACTOR}. */
    private static final double COLLAPSE_HYSTERESIS = 1.5;

    /** How many sectors one {@link #update} may materialize (subdivide or generate), so a tick never stalls. */
    private static final int REFINE_BUDGET_PER_UPDATE = 8;

    /**
     *  Refines the level-of-detail octree around every camera &mdash; the unified "build the world as
     *  you move" step that replaces the old chunk grid, making the world both <b>infinite</b> and
     *  <b>memory-bounded</b>.
     *  <p>
     *  First the root is {@link #growToContain grown} to cover a {@link #VIEW_DISTANCE} box around each
     *  camera (so far terrain exists to refine into). Then the tree is walked and each sector, by its
     *  distance relative to its own size, is:
     *  <ul>
     *      <li><b>refined</b> &mdash; a coarse sector a camera is within {@link #REFINE_FACTOR}&times;
     *          its edge of is subdivided into eight finer {@link #coarseLeaf coarse children} (each
     *          described top-down by {@link WorldGenerator#etherOf}); at the chunk level it is instead
     *          {@link WorldGenerator#generate generated} to full voxel detail;</li>
     *      <li><b>kept</b> as-is; or</li>
     *      <li><b>collapsed</b> &mdash; a refined sector no camera is near (past the hysteresis band)
     *          drops its sub-tree back to a single coarse leaf, freeing memory. Deterministic terrain
     *          makes this lossless: approaching again re-refines it.</li>
     *  </ul>
     *  The materialized sectors form a <b>cone of detail</b> around each camera &mdash; full voxels up
     *  close, progressively coarser {@code etherOf} leaves outward &mdash; so memory is bounded by the
     *  cone, not the distance travelled. Materializations are budgeted
     *  ({@link #REFINE_BUDGET_PER_UPDATE}, nearest first) so one tick never stalls; the rest stream in
     *  over following ticks. A world with no generator, or no camera to anchor the cone, is unchanged.
     */
    private World refineAroundCameras() {
        WorldGenerator generator = _generator;
        if ( generator == null )
            return this;
        List<VecF64> eyes = cameraEyes();
        if ( eyes.isEmpty() )
            return this;

        WorldSector root = _root;
        double v = VIEW_DISTANCE;
        for ( VecF64 eye : eyes )
            root = growToContain(generator, root, BoundsF64.of(eye.sub(VecF64.of(v, v, v)), eye.add(VecF64.of(v, v, v))));

        int[] budget = { REFINE_BUDGET_PER_UPDATE };
        WorldSector refined = refine(generator, root, eyes, budget);
        if ( refined == _root )
            return this;
        return new World(refined, _entities, _screens, _inputStates, generator);
    }

    /** @return The positions of all camera entities (the anchors of the detail cone). */
    private List<VecF64> cameraEyes() {
        List<VecF64> eyes = new ArrayList<>();
        for ( Entity entity : _entities.values() )
            if ( entity instanceof Entity.CameraEntity cameraEntity )
                eyes.add(cameraEntity.camera().position());
        return eyes;
    }

    /**
     *  Refines (or collapses) one sub-tree toward the cameras, returning the possibly-new sub-tree.
     *  Returns the same instance when nothing changed, so an already-settled world is returned by
     *  identity from {@link #refineAroundCameras} (and the renderer's value-keyed caches keep hitting).
     */
    private static WorldSector refine( WorldGenerator generator, WorldSector node, List<VecF64> eyes, int[] budget ) {
        double edge = maxEdge(node.bounds());
        double dist = nearestDistance(node.bounds(), eyes);

        if ( edge <= generator.chunkSize() ) {
            // Chunk level: full voxel detail when close, a single coarse leaf when far.
            if ( dist < generator.generationDistance() ) {
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
            if ( !node.isLeaf() && dist > generator.generationDistance() * COLLAPSE_HYSTERESIS )
                return coarseLeaf(generator, node.bounds()); // far: drop the chunk's voxel detail.
            return node;
        }

        // Above the chunk level: refine into coarse children when close — but ONLY a non-homogeneous
        // (surface-straddling) sector, since a uniform region is identical at every level of detail.
        // This keeps refinement on the 2D terrain surface, not the 3D solid/empty volume, so the cone
        // stays bounded even with a large REFINE_FACTOR. Collapse when far.
        if ( dist < edge * REFINE_FACTOR && !generator.isHomogeneous(node.bounds()) ) {
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
            for ( int i : nearestFirst(children, eyes) ) {
                WorldSector child = children.sector(i);
                WorldSector refinedChild = refine(generator, child, eyes, budget);
                if ( refinedChild != child ) {
                    newKids[i] = refinedChild;
                    changed = true;
                }
            }
            return changed ? node.withChildren(new WorldTreeNode(newKids)) : node;
        }

        if ( !node.isLeaf() && dist > edge * REFINE_FACTOR * COLLAPSE_HYSTERESIS )
            return coarseLeaf(generator, node.bounds()); // far: collapse the whole sub-tree to one coarse leaf.
        return node;
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

    /** @return Child indices ordered nearest-camera first, so the materialization budget is spent on the nearest detail. */
    private static Integer[] nearestFirst( WorldTreeNode node, List<VecF64> eyes ) {
        Integer[] order = new Integer[WorldTreeNode.SECTOR_COUNT];
        double[] dist = new double[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
            order[i] = i;
            dist[i] = nearestDistance(node.sector(i).bounds(), eyes);
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> dist[i]));
        return order;
    }

    /** @return The distance from the nearest camera to {@code bounds} (0 if a camera is inside it). */
    private static double nearestDistance( BoundsF64 bounds, List<VecF64> eyes ) {
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
     *  The level-of-detail target: roughly the screen size, in pixels, of one meshed grid cell. The
     *  walk meshes each collected sector at the resolution that keeps its cells near this size, so a
     *  sector small on screen is drawn from a few coarse cells and a near one finely. Smaller = more
     *  detail (and more quads); larger = coarser.
     */
    private static final double TARGET_CELL_PX = 12.0;

    /**
     *  The cap on the grid resolution a single render unit is meshed at. A power of
     *  {@link WorldTreeNode#RESOLUTION}; the renderer's mesher caps its grid at the same value.
     */
    private static final int MAX_MESH_RESOLUTION = WorldTreeNode.RESOLUTION * WorldTreeNode.RESOLUTION; // 64

    /**
     *  Above the {@code chunkSize} floor, a sector small enough on screen to span at most this many
     *  {@link #TARGET_CELL_PX target-sized} cells is collapsed into <i>one</i> coarse render unit
     *  (meshed from its branch sectors); a bigger one is descended into instead, so its children carry
     *  the detail and occlusion can act between them. Tied to {@link WorldTreeNode#RESOLUTION} so a
     *  collapsed unit is meshed at one tree level (its direct children as voxels) or coarser.
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
     *      <li><b>Level of detail</b> &mdash; for each surviving sector the walk estimates its
     *          projected screen size and the grid resolution that would keep cells near
     *          {@link #TARGET_CELL_PX}. A leaf, a solid occluder, a sector already {@code chunkSize}
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
     *  @return The grid resolution to mesh a unit at, given how many {@link #TARGET_CELL_PX}-sized
     *          cells would span its projected edge: the nearest power of {@link WorldTreeNode#RESOLUTION}
     *          ({@code 1, 8, 64}), capped at {@link #MAX_MESH_RESOLUTION}. Nearest (rather than floor)
     *          keeps the cell size centred on the target across the coarse, factor-8 LoD steps.
     */
    private static int meshResolutionFor( double desiredCells ) {
        if ( desiredCells <= 1 )
            return 1;
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

        void collect( WorldSector sector ) {
            if ( sector.isVoid() )
                return; // empty air sub-tree: nothing to draw.
            if ( !view.frustum().intersects(sector.bounds()) )
                return; // outside the view: prune this sector and its whole sub-tree.

            double[][] corners = project8(sector.bounds());
            if ( corners != null && coverage.isOccluded(minOf(corners, 0), minOf(corners, 1),
                                                        maxOf(corners, 0), maxOf(corners, 1)) ) {
                occlusionCulled++;
                return; // fully hidden behind nearer solid geometry: prune the sub-tree.
            }

            if ( sector.isSolidOpaque() ) {
                // A perfect occluder: collect it as one render unit (its mesh is just the
                // shell, so the coarsest resolution suffices) and record its silhouette so it
                // blocks whatever is behind.
                collector.collect(sector, view, 1);
                collected++;
                if ( corners != null )
                    coverage.markOccluder(corners);
                return;
            }

            // Level of detail: how many cells across would keep each grid cell near the target
            // screen size? A sector that is small on screen is meshed coarsely (few big quads from
            // branch sectors); a near, big one is descended into so its children carry the detail.
            double distance = view.camera().position().distance(sector.bounds().center());
            double projectedPx = projectedEdgePixels(maxEdge(sector.bounds()), distance, view.focalLengthPx());
            double desiredCells = projectedPx / TARGET_CELL_PX;

            boolean atFloor = sector.isLeaf() || maxEdge(sector.bounds()) <= chunkSize;
            if ( atFloor || desiredCells <= LOD_COLLAPSE_CELLS ) {
                // At the chunk floor (full detail), or far enough that the whole sector is only a few
                // cells on screen: hand it over as one unit, meshed at the chosen level of detail.
                collector.collect(sector, view, meshResolutionFor(desiredCells));
                collected++;
                return;
            }

            // Still big on screen above the floor: recurse so children carry the detail (and occlusion
            // can act between them), nearest child first, so nearer occluders are marked before farther ones.
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