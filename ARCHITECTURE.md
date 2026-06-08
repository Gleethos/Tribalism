# World Engine — Architecture

This document describes the architecture of the **world engine** that lives under
`src/main/java/app/engine`. It is the spatial simulation core that the wider
Tribalism application will eventually render and interact with. Rendering, the
update loop and entity behaviour are deliberately thin, pluggable layers on top
of an immutable data model.

> The original design brief lives in [`claude/CLAUDE.md`](claude/CLAUDE.md). This
> file documents what has actually been built and how the pieces fit together.

---

## 1. Guiding principles

The engine is built around **functional, data-oriented programming**:

- **Everything is an immutable value.** No data structure mutates in place. Every
  "change" returns a new value. This makes the whole model trivially thread-safe
  and free of aliasing bugs.
- **Records and sum types.** Data is modelled with `record`s and sealed
  interfaces (sum types implemented only by records), so the shape of the data
  is explicit and can be matched exhaustively.
- **Value semantics everywhere.** Every primitive has correct `equals`/`hashCode`
  so it can live inside records and the persistent collections.
- **Structural sharing for performance.** Because the world is a tree built on
  persistent collections, replacing one node copies only that node and the path
  to it; the rest of the tree is shared with the previous version.
- **Persistent collections from Sprouts.** `Tuple` (immutable array),
  `Association` (immutable map) and `ValueSet` (immutable set). Sprouts ships
  transitively via the `swing-tree` dependency.

The simulation runs entirely in **64-bit space** (all `double`). Any conversion
to 32-bit floats is a concern of the rendering step only.

---

## 2. Package layout

```
app.engine
├── util                Small cross-cutting helpers
│   └── Lazy            Thread-safe, write-once memoized value
│
├── primitives          Math primitives (pure 64-bit value types)
│   ├── VecF64          3D vector
│   ├── BoundsF64       Axis-aligned bounding box
│   ├── Mat4F64         4×4 matrix (transforms, projection)
│   ├── CameraF64       A viewpoint that lazily caches its matrices + frustum
│   └── Frustum         Six culling planes of a view volume (box visibility tests)
│
└── world               The world data model and its tooling
    ├── Texture                 A visual appearance quality (GRAINY, LIQUID, REFLECTIVE, …)
    ├── TextureProfile          A face's look: independent Texture intensities [0,1] + an inset [0,1] (content recess)
    ├── MaterialId              Sum type: Specific(int) | Diverse (what a cube is made of)
    ├── Material                Starter substance registry (id + name + default TextureProfile)
    ├── Side                    One of the six cube faces (NEG_X … POS_Z)
    ├── WorldSectorEtherData    Ether: one MaterialId per cube + one TextureProfile per Side (+ shrink-to-fit)
    ├── WorldTreeEntityId       Positional handle: long id + bounds
    ├── LightSource             Sealed light sum type (Sphere | Cube | Plane)
    ├── LightTrace              A ray of light radiating from a source
    ├── WorldTreeNode           A node = array of exactly 512 sectors (8×8×8), encapsulated
    ├── WorldSector             The recursive cell of the world (the heart)
    ├── Entity                  Sealed entity sum type (CameraEntity | VoxelEntity)
    ├── ScreenId                Typed id of a Screen
    ├── Screen                  A render target: id + pixel size + (optional) bound camera id
    ├── PointerId               Typed id of a pointer (mouse / one touch point)
    ├── PointerButton           PRIMARY | SECONDARY | MIDDLE
    ├── Key                     Engine-neutral keyboard key (app maps toolkit codes onto it)
    ├── ScreenInputEvent        Sum type: one thing that happened on a screen (key/cursor/scroll)
    ├── ScreenInputs            A screen's event log (Tuple<ScreenInputEvent>) since last update
    ├── EngineInputs            One update step's input: dt + per-screen ScreenInputs
    ├── CameraFlight            Pure free-fly control: held keys + look delta → moved camera
    ├── ViewInfo                A frame's camera + frustum + projection (and how to project a point)
    ├── SectorDrawCollector     Sink a visibility walk hands each drawable sector to
    ├── CoverageGrid            Screen "already-blocked" buffer for occlusion culling
    ├── World                   Value class: tree + entities + screens + generator; infinite, updatable
    │
    ├── gen              Procedural generation (owned by World)
    │   ├── PerlinNoise         Deterministic, seeded 3D gradient noise + fbm
    │   └── WorldGenerator      Adaptive noise → sector tree (+ generationDistance/chunkSize/detailDepth)
    │
    ├── render           Rendering: a backend SPI + two interchangeable backends
    │   ├── Renderer            SPI: a swappable backend (viewport + setWorld + stats), a fn of the World
    │   ├── FrameStats          Per-frame diagnostics a backend reports (faces drawn, sectors culled)
    │   ├── SectorGeometry      Turns a collected chunk sub-tree into world-space Quads
    │   ├── SectorMeshCache     Greedy-meshes a sector over a rasterized voxel grid at a chosen LoD; memoized per (sector, res)
    │   ├── SectorMesh          A chunk's culled + greedy-merged set of visible faces
    │   ├── Quad                One world-space face (4 corners + normal + profile)
    │   ├── Cubes               Bounds + Side → face Quad (shared box/mesh geometry)
    │   ├── Shading             Pure directional shade (ambient floor + diffuse) — colour and scalar
    │   ├── Noise               Engine-owned procedural noise toolkit (value/fBm/Worley + named patterns)
    │   ├── TextureBaker        Bakes a TextureProfile into a seamless, tileable procedural tile
    │   ├── TexturePalette      TextureProfile → base AWT Color (the tile's tint; keeps AWT out of the model)
    │   ├── Graphics2DRenderer  Backend #1: software Graphics2D (the dependable fallback)
    │   ├── WorldRenderer       The Graphics2D SectorDrawCollector (shaded 2D polygons, painter's sort)
    │   └── gl
    │       └── GlRenderer      Backend #2: OpenGL (LWJGL) — retained per-chunk VBOs, z-buffer,
    │                           procedural textures in a mip-mapped GL_TEXTURE_2D_ARRAY
    │
    └── demo
        └── WorldEngineDemo     Self-contained, runnable demo window (either backend)
```

> **Note:** an older `app.engine` prototype (`VoxelChunkTree`, `Engine`,
> `RealVoxel`, `renderer/`, `input/`, `entities/`, …) predates this design and is
> a disposable first attempt. The current engine is the `primitives` + `world`
> packages described here.

---

## 3. Math primitives (`app.engine.primitives`)

All primitives are immutable, have full value semantics, and operate in 64-bit.

### `VecF64`
A 3D vector `(x, y, z)`. Provides the usual algebra (`add`, `sub`, `mul`, `div`,
`dot`, `cross`, `length`, `normalize`, …) plus helpers used elsewhere in the
engine: `lengthSquared`, `distance`, `distanceSquared`, `lerp`, `clamp`,
`floor`/`ceil`/`round`. Constants `zero()` / `one()`.

### `BoundsF64`
An axis-aligned bounding box defined by `min` and `max` corners. It is the
spatial backbone of the tree. Key operations:

- `center`, `size`, `volume`, `isCube`
- containment: `contains(point)`, `contains(bounds)`, `intersects`, `union`,
  `intersectionWith`
- **`subdivide(divisions)`** → `Tuple<BoundsF64>` of `divisions³` sub-boxes in
  `x + y·d + z·d²` order. This single method drives the whole recursive tree.
- `child(x, y, z, divisions)` → one sub-box without building the whole grid.

### `Mat4F64`
A row-major 4×4 matrix (`get(row, col) == data[row*4 + col]`). Fully immutable:
`set` returns a new matrix and `data()` returns a defensive copy. Provides
`identity`, `translation`, `scale`, `rotationX/Y/Z`, `perspective`,
`orthographic`, `lookAt`, `mul`, `transpose`, `determinant`, a general
`inverse()` (cofactor/adjugate), and `transformPoint` / `transformDirection`.

### `CameraF64`
A viewpoint defined by `position`, `target`, `up`, plus the frustum shape
(`fovYRadians`, `aspect`, `near`, `far`). It derives `viewMatrix()` (look-at),
`projectionMatrix()` (perspective), `viewProjectionMatrix()` and a cullable
`frustum()`. Rendering stays a separate function of this state.

Unlike the other primitives, `CameraF64` is a **`final class`, not a `record`** —
deliberately, so it can *encapsulate* a private cache. The four derived values
(view, projection, view-projection matrices and the frustum) are expensive and
queried repeatedly per frame, so each is wrapped in a [`Lazy`](#lazy) and computed
at most once. The camera still behaves as a **value**: it is immutable and its
`equals`/`hashCode` are defined purely by the seven fields, never the caches.
This is the engine's recurring pattern for "lazy values + memoization" applied to
a value object that a `record` cannot express (records may not hold extra fields).

### `Frustum`
The six clipping planes of a camera's view volume, extracted from a world-to-clip
`viewProjectionMatrix()` via the Gribb–Hartmann method (each plane is `row3 ± rowₖ`
of the matrix, normalized, with the normal pointing inward). The workhorse is
**`intersects(BoundsF64)`**: a conservative box test that returns `false` only when
the box lies *entirely* outside the volume (it tests the box's "positive vertex"
against each plane). It never rejects a visible box, which is exactly what makes it
safe to drive **frustum culling** (§7). `contains(point)` is the point analogue.

### `Lazy`
A tiny helper in `app.engine.util`: a **thread-safe, write-once memoized value**.
`Lazy.of(supplier)` defers a computation until the first `get()`, then caches it
for every later read (double-checked locking guarded by a `volatile`). It lets an
immutable value object carry derived, expensive state that is only paid for if and
when it is actually read — the mechanism behind `CameraF64`'s cached matrices and
frustum. Because the cached value is a pure function of the inputs, it is always
excluded from the holder's `equals`/`hashCode`.

---

## 4. The world tree (`app.engine.world`)

This is the core data structure: a tree inspired by **Hash Array Mapped Tries**
rather than a classic oct-tree. Instead of branching by 2 per axis (oct-tree), it
branches by **8 per axis at once**, giving a flat, cache-friendly child array and
a shallow tree.

### Branching factor: 512 = 8×8×8

Each `WorldTreeNode` holds **exactly 512** `WorldSector`s forming a perfect
`8 × 8 × 8` cube. (The original brief said "256", but 256 is not a perfect cube;
512 = 8³ is both a perfect cube *and* a power of two, satisfying both the
"cache-friendly" and "perfect 3D cube" goals.) Constants:

```java
WorldTreeNode.RESOLUTION    == 8
WorldTreeNode.SECTOR_COUNT == 512
```

Sectors are stored linearly in `x + y·8 + z·64` order. `WorldTreeNode.indexOf(x, y, z)`
maps grid coordinates to that index. The 512 children are a plain encapsulated
`WorldSector[]` (an immutable value class, not a persistent `Tuple`): `sector(i)` is the
single hottest read in the engine — every render walk and aggregation touches all 512 —
so it must be a bare array access. `withSector` copies the array (writes are far rarer
than reads); `equals`/`hashCode` compare children element-wise.

### `WorldSector` — the recursive cell

A `WorldSector` is the heart of the engine and is *recursive*:

```java
final class WorldSector {           // a value (immutable, value equals/hashCode)
    BoundsF64                   bounds;       // the region it occupies
    WorldSectorEtherData        ether;        // what it looks like + what it is made of
    ValueSet<WorldTreeEntityId> entities;     // entities positioned here
    ValueSet<LightSource>       lights;       // lights positioned here
    Tuple<LightTrace>           lightTraces;  // light radiating through here
    @Nullable WorldTreeNode     children;     // null = leaf voxel; else 512 sub-sectors
    // (a sector's SHAPE — per-face content recess — lives in its ether, not here; see TextureProfile.inset)
    // derived, lazily-memoized (excluded from equals/hashCode):
    Lazy<Boolean>               solidOpaque;        // is every voxel inside fully opaque? (a perfect occluder)
    Lazy<Boolean>               hasOnlyLeafChildren;// is this a branch of only leaves? (a full-detail block)
    Lazy<Boolean>               isVoid;             // is the whole sub-tree empty air? (nothing to draw)
    int                         hash;               // cached deep hash (0 = not yet computed)
}
```

It is a `final class` rather than a `record` for the same reason as `CameraF64`:
so it can encapsulate its *derived, lazily-memoized* predicates the render walk leans on
(`isSolidOpaque`, `hasOnlyLeafChildren`, `isVoid`) and a cached hash — while still behaving as an
immutable value (its `equals`/`hashCode` cover only the six defining fields, never the caches).
The value `equals`/`hashCode` are *deep* (they walk the whole sub-tree), so the hash is
**memoized** too: that makes a sector a cheap key for value-keyed caches (e.g. the
renderer's per-chunk meshes), and `equals` further short-circuits on identity — which is
exactly why the incremental, structure-sharing aggregation below matters so much.

A sector with `children == null` is a **leaf** — effectively a single voxel. A
sector with children is a branch of 512 finer sub-sectors. This gives the
structure **practically infinite resolution into the small and infinite scale
into the large**: a single sector can be a solid rock voxel, or a continent
full of detail.

```
                 WorldSector (bounds, ether, entities, lights, traces)
                        │ children?
            ┌───────────┴───────────┐
          null                 WorldTreeNode
        (leaf voxel)         WorldSector[512]
                          ┌──────┬──────┬─── … ───┐
                          │  0   │  1   │   …      │  (each a WorldSector,
                          └──────┴──────┴──────────┘   recursing again)
```

All mutations are copy-on-write `with…` methods: `withEther`, `withChildren`,
`withEntity`/`withoutEntity`, `withLight`/`withoutLight`, `withLightTrace`.

### Appearance & material (the "ether")

The engine deliberately separates **how a sector looks** from **what it is made
of**. The old "material percentages summing to 1" model is gone; in its place:

- **`Texture`** — an enum of independent *visual appearance qualities*:
  `OPACITY, REFLECTIVE, METALLIC, EMISSIVE, ROUGH, GRAINY, POWDERY, CRYSTALLINE,
  LIQUID, WET, MOLTEN, FIBROUS, HAIRY, MOSSY, LEAFY, SPIKY, SHATTERED, POROUS,
  LAYERED, VEINED`. These are *hints*, not a composition: they are the intended
  inputs to a future procedural noise shader (see §7 / §10).
- **`TextureProfile`** — one face's full profile: a set of `Texture` intensities in
  `[0, 1]` (the **appearance**) plus an **`inset()`** in `[0, 1]` (the **shape** — how far
  content is recessed behind this face; see §4.3). The qualities are **independent** and
  need **not sum to anything** — a surface can be a "grainy liquid" with both at full
  strength. `none()` (every quality 0, inset 0) is the null object: invisible empty space.
  Provides `intensityOf`, `with` (clamped), `inset`/`withInset`, `isInvisible`, `blend`,
  the static **`average(samples)`** (which averages the inset alongside the qualities) and
  **`sameAppearance`** (qualities only, ignoring inset — what greedy meshing merges by). It
  is an immutable **value class** over a flat `double[]` indexed by `Texture.ordinal()`
  (plus the scalar inset), not a map: the key space is a small fixed enum, so a plain array
  makes the hot operations tight, allocation-/hash-free loops. The array is encapsulated
  (owned, never exposed); `equals`/`hashCode` compare the intensities *and* inset.
- **`MaterialId`** — *what a cube is made of*, as a sum type:
  `Specific(int id)` | `Diverse`. A leaf "block" is always one `Specific` material;
  a coarse aggregate of disagreeing children is the `Diverse` null object. The
  integer id (not an enum) lets the substance set scale to thousands. `merge(ids)`
  folds children into the shared id, or `Diverse` if they differ.
- **`Material`** — a record-backed *starter registry* of substances (id + name +
  default `TextureProfile`): `AIR, ROCK, SOIL, GRASS, SAND, WATER, WOOD, METAL,
  ICE, LAVA, SNOW, MOSS, CRYSTAL, CLAY, BARK, LEAVES, MUD, ORE`. It carries no
  colour — hue is a *rendering* concern derived from the textures.
- **`Side`** — one of the six cube faces (`NEG_X … POS_Z`), each with an `axis()`,
  `isPositive()`, outward `normal()` and `opposite()`.
- **`WorldSectorEtherData`** — a sector's "ether": **one `MaterialId` for the whole
  cube** (the gameplay substance) plus **one `TextureProfile` per `Side`** (the
  appearance *and* that face's inset). Provides `material()`/`withMaterial`,
  `sideOf(side)`/`withSide`, `insetOf(side)`, `combined()` (the average of all six side
  profiles) and **`shrink(bounds)`** (turns the six insets into a content-fitting box,
  collapsing crossed axes to a slab). Also a value class over a flat `TextureProfile[]`
  indexed by `Side.ordinal()`, so `sideOf` is a direct array read — it sits on the hot
  aggregation path and must not pay map-lookup costs.

  Appearance is stored *per face* because only the outer faces of a cube are ever
  seen — so a super-sector can summarize each of its faces from only the matching
  faces of the sub-sectors lying on that face, never the hidden interior. The
  single material, by contrast, is a whole-cube property (one per block).

### Entities & lights in the tree

The tree stores only lightweight, positional handles, never the real objects:

- **`WorldTreeEntityId`** = `(long id, BoundsF64 bounds)`. The actual entities
  live separately (in `World`). This separation means the tree only has to be
  touched when an entity's **bounds** change, not on every state change.
- **`LightSource`** — a sealed sum type: `Sphere`, `Cube`, `Plane`. Each has an
  `id`, `intensity`, `color` (RGB as `VecF64`), a `position()` and `bounds()`.
- **`LightTrace`** = `(VecF64 direction, double intensity, long sourceId)` — a ray
  of light without its own identity, placed into the tree by the (future) update
  loop so light radiates from its source across sectors.

### Two key behaviours

**1. Entity fall-down — `WorldSector.insert(entity, remainingDepth)`**

An entity descends into the deepest sector that *still fully contains* its
bounding box. At each level the sector computes which single `8×8×8` sub-cell
fully contains the entity; if exactly one does, it subdivides (on demand) and
recurses; otherwise the entity comes to rest at the current level. This keeps the
tree only as deep as the entities require and stores each entity exactly once, in
its tightest enclosing sector. `remove(entity, depth)` mirrors this for moving
entities.

**2. Level of detail — `WorldSector.aggregated()`**

A parent sector summarizes its whole sub-tree in two ways. Leaves keep their own
ether; a branch first aggregates each child recursively, then:

- **Appearance, per side.** For each `Side` it averages that same face of only the
  sub-sectors on the parent's boundary layer for that side
  (`WorldTreeNode.boundaryCells(side)` — the 8×8 = 64 children touching that face).
  The hidden interior never contributes. Because each face averages over a 64-cell
  layer (not the full 512-cell volume), a single deep opaque voxel contributes
  `1/64` of `OPACITY` per level it climbs on the faces it lies on, and `0` to the
  faces it never touches.
- **Material, per cube.** The 512 children's `MaterialId`s are `merge`d into the
  shared id if they all agree, otherwise `Diverse`.

So any sub-tree collapses into one *visually faithful* representative voxel — e.g.
a super-sector straddling the ground shows grassy/mossy texture on its `POS_Y`
(top) face and rocky texture on `NEG_Y` (bottom), with a `Diverse` material —
which is what makes cheap, directionally-correct LoD rendering possible.

**Incremental, structure-sharing aggregation.** `aggregated()` rebuilds a *whole* sub-tree
(every descendant becomes a new instance) — right for aggregating one freshly-generated chunk,
but ruinous if run over the entire world each time a chunk streams in: it is `O(every voxel)`
*and* it replaces every existing sector with a new-but-`equals` instance, which silently
destroys structural sharing (so value-keyed caches stop hitting on identity and fall back to
deep `equals`, and the lazy predicates above all recompute). The splice path therefore uses
**`WorldSector.withAggregatedChildren(node)`** instead: it adopts an *already-aggregated*
`node` **by reference** and recomputes only *this one* sector's ether from it. Splicing a chunk
re-aggregates just the ancestors on its root-to-chunk path (`O(depth)`), leaving every off-path
sub-tree — and every other chunk — untouched and identity-stable. This is the single change
that keeps flying smooth (see §5); the full `aggregated()` remains for building one chunk.

**3. Side insets — a face's content recess, carried on its `TextureProfile`** <a id="side-insets"></a>

Per-side appearance fixes the *colour* of a coarse LoD box, but not its *shape*: a
"half-full" sector (solid bottom, air top) drawn as a full cube would either stick
out into empty air or, if skipped, leave a hole. The **inset** fixes the shape: each
face's `TextureProfile` carries, alongside its appearance qualities, an `inset()` in
`[0, 1]` — the fraction of the sector's extent by which its content is recessed from
that face. Shape thus lives *in the ether*, not in a separate type or a `WorldSector`
field; `WorldSectorEtherData.shrink(bounds)` reads the six insets and the renderer
**meshes that shrunk box** when it draws a unit as a single res-1 box, so a distant
LoD box stops at the terrain instead of sticking up as a full cube.

The inset is **geometry, not appearance**, so although it is part of value identity
(`equals`/`hashCode` — the mesh cache key must reflect a box's shape) it is **ignored
by colour and by greedy-mesh face merging**: the mesher merges adjacent coplanar faces
by `TextureProfile.sameAppearance` (qualities only), so faces that look identical but
recede differently still merge into one rectangle. A material's intrinsic texture
simply has inset `0`, so shared profiles stay shared (it costs no extra memory until a
face is actually recessed).

Insets are **always described top-down by the generator**, just like appearance:
`etherOf` and `representativeEtherOf` sample the region's 8³ grid and, per face, peel
whole empty (invisible) cell-layers inward, attaching the resulting recess to that
side's profile via `TextureProfile.withInset`. A uniform region (solid rock, open air)
recesses nothing (inset `0`); a surface-straddling region yields roughly `POS_Y > 0`,
`NEG_Y = 0`. `generate` attaches the **same** top-down insets to its aggregated root
ether, so the faithfulness invariant `etherOf(b) == generate(b, 1).ether()` holds for
shape as well as appearance (no LoD pop). There is no bottom-up inset derivation any
more — `aggregated()` (used only for *edited* sub-trees) just averages whatever insets
its children's faces carry, for free, since `TextureProfile.average` averages the inset
alongside the qualities.

---

## 5. Entities and the World

### `Entity` (sum type)

```java
sealed interface Entity permits Entity.CameraEntity, Entity.VoxelEntity {
    WorldTreeEntityId treeId();        // long id + bounds
    default long id();
    default BoundsF64 bounds();
}
```

- **`CameraEntity(treeId, CameraF64 camera)`** — a viewpoint into the world. Every
  camera is an entity, so it has an `id` and is identifiable/bindable from outside.
- **`VoxelEntity(treeId, WorldSector sector)`** — *itself a small world*: its
  shape and material are a nested `WorldSector` (with the full recursive
  machinery), letting the entity move freely relative to the world it belongs to.
  (Recursive sub-entities — e.g. a knight holding a sword — are a future step.)

### `World` (the top-level value class)

`World` is an immutable **value class** (no longer a record — it has grown past what
a record can hold and needs to encapsulate some state) tying together four things:

```text
WorldSector                       root          // spatial tree; grows outward (infinite)
Association<Long, Entity>         entities       // id → actual entity (cameras included)
Association<ScreenId, Screen>     screens        // render targets the world knows about
WorldGenerator?                   generator      // optional: how the world builds itself
(private) per-screen input state                 // currently-held keys, between updates
```

The `root` holds only `WorldTreeEntityId`s; the `entities` association is the
authoritative store. `withEntity` / `withoutEntity` / `withMovedEntity` keep the two
in sync for **voxel** entities. **Cameras**, by contrast, have no voxel presence, so
`createCamera` / `destroyCamera` touch only the entity lookup and never grow the tree
(nothing queries cameras positionally, and a flying camera would otherwise churn it).

**Screens & cameras (multi-screen support).** A `Screen` is a render target: a
`ScreenId`, a pixel `width`/`height`, and the id of the camera it shows. A screen
references its camera **one-way**, by id, so the same camera can drive several screens
at once. `createScreen` / `destroyScreen` / `resizeScreen` / `bindScreenToCamera`
manage them; `screen(id)` / `allScreens()` query them. A screen is unbound until bound,
and rendering an unbound or dangling screen simply produces nothing.

**The update step.** `World update(EngineInputs)` is the function an engine loop applies
each tick. `EngineInputs` carries a `dtSeconds` and an `Association<ScreenId, ScreenInputs>`;
each `ScreenInputs` is an ordered `Tuple<ScreenInputEvent>` — an **event log** of what
happened on that screen since the last update (`KeyPressed`/`KeyReleased`,
`CursorMoved`/`CursorDown`/`CursorUp` with a `PointerId` for multi-touch, `Scrolled`).
Events report *changes*, so the world **remembers held keys between updates** (the
encapsulated per-screen state); update folds the events into that state and turns held
keys + accumulated cursor delta into **camera mutations** on each screen's bound camera
via the pure `CameraFlight` controls (the free-fly logic, lifted out of the demo into
the engine). Keys are the engine-neutral `Key` enum, so the world model never sees AWT.

**Refinement around cameras — the infinite, level-of-detail world.** A world can own a
`WorldGenerator` (`World.of(generator)`); `update` then **refines a single continuous LoD octree
around every camera** (`refineAroundCameras`), and the world is effectively **unbounded** yet
**memory-bounded**. It starts as one large coarse sector (described top-down by
`generator.etherOf`, no sub-tree) covering the view range. Each tick, after the camera mutations:

- the root is first **grown** (`growToContain`) to cover a `VIEW_DISTANCE` box around each camera
  — re-rooting 8× larger and filling the new cells with coarse leaves (rare: the root already
  spans the view range); then
- the tree is walked and each sector, by how big it is **on screen** (`detailCells` — see below), is
  **refined**, **kept**, or **collapsed**:
  - *refine* — a **non-homogeneous** (surface-straddling) sector wanting more than `REFINE_CELLS` cells
    of detail across its edge is subdivided into eight finer **coarse-leaf children** (each described
    top-down, cheaply, by `generator.representativeEtherOf`); at the **chunk level** (`chunkSize`) it is
    instead `generator.generate`d to full voxel detail. A **homogeneous** region (`generator.isHomogeneous`
    — solid rock, open air) is *never* refined: it is identical at every level of detail, so its coarse
    leaf already *is* the full detail. This restriction is what keeps refinement on the **2D terrain
    surface** rather than the 3D solid/empty volume, which bounds memory;
  - *keep* — within the hysteresis band, unchanged;
  - *collapse* — a refined sector now too small on screen (past `REFINE_CELLS / COLLAPSE_HYSTERESIS`)
    drops its sub-tree back to one coarse leaf, freeing memory.

**One shared, resolution-independent LoD metric.** Both the world (here) and the renderer (§7) measure a
sector's on-screen size with the *same* function, `detailCells(bounds, eye) = LOD_DETAIL × radius /
distance` — how many cells of detail it wants across its edge, depending only on its size relative to
its distance, **never on pixels or viewport height**. (An earlier pixel-based metric scaled with screen
resolution, so a 4K window descended ~8× deeper than a small one and collapsed distant terrain into giant
childless boxes; tying both sides to one resolution-independent number is what fixed that.) `LOD_DETAIL`
is the single knob — bigger means more detail and deeper descent. `REFINE_CELLS` (the refine threshold)
is the res-1→res-8 boundary of the renderer's mesher, and is **below the renderer's draw threshold**
(`LOD_COLLAPSE_CELLS`, §7), so the coarse sector the renderer actually *draws* still has children and is
therefore **greedy-meshed at res-8 from its sub-sectors** — showing the terrain's *shape* — instead of as
one flat box. The materialized sectors form a **cone of
detail along the surface** around each camera — full voxels up close, progressively coarser surface
sectors outward, with uniform interiors left as single coarse boxes — so **memory is bounded by the
surface cone, not the distance travelled**, and the renderer draws far terrain as shaped surface
instead of not at all. Materializations are **budgeted** (`REFINE_BUDGET_PER_UPDATE`, nearest-first),
so a tick never stalls and detail streams in over following ticks; an already-settled world is
returned by **identity** (so the renderer's caches keep hitting). The nearest-first ordering exists
only to aim that budget, so the walk **skips the per-node sort once the budget is spent** (the bulk
of the walk only detects far-collapses, which are order-independent); the sorts that remain pack
distance+index into a `long[]` and use the primitive `Arrays.sort` — no boxed `Integer[]`, no
`Comparator` — keeping the refinement walk off the GC and out of the flame graph. On top of that, a
**movement gate** avoids the walk altogether when it would change nothing: once a walk *settles*
(changes nothing) the world records the camera positions (`_settledEyes`, a derived hint excluded
from `equals`/`hashCode`), and later updates return by **identity without walking** until some camera
drifts past `chunkSize × REFINE_REANCHOR_FRACTION` of that anchor — so a near-stationary or slowly
drifting camera costs nothing, and a fast one re-walks roughly once per re-anchor distance travelled
instead of every tick. The gate only engages *after* settling, so budgeted detail still streams to
completion while a freshly-arrived camera holds still. Because terrain is a deterministic
function of the seed, collapse is **lossless** — approaching again re-refines it byte-for-byte.

So a generator world is the unified LoD octree: refine toward cameras, collapse away, grow to
follow. A world built without a generator (`World.of(root)`) skips this step. `sectorAt(p)` (the
deepest sector at a world point) doubles as a "how refined is here" query — the smaller its
bounds, the finer the detail materialized there. (Once chunks can carry **edits** not reproducible
from the seed, the collapse step is the seam where they would instead be persisted to disk before
being dropped — see §10. The GPU backend independently evicts the VBOs of sectors it stops being
handed, so VRAM is bounded too.)

`World` is also the **query API** for everything that interrogates the world rather than
mutating it — most importantly the per-frame visibility walk `collectSectorsForRendering`
(frustum + occlusion + LoD culling), so a renderer consumes a clean stream of visible
sectors instead of coupling to the tree (see §7).

---

## 6. Procedural generation (`app.engine.world.gen`)

### `PerlinNoise`

A deterministic, **seeded** 3D Perlin gradient noise (improved Perlin with a
Fisher–Yates–shuffled permutation table) plus fractal Brownian motion
(`fbm(x,y,z, octaves, persistence, lacunarity)`). Same seed ⇒ same landscape,
which makes generation reproducible and testable.

### `WorldGenerator`

Turns noise into a `WorldSector` tree. Material is a height-field function of
position:

```
materialAt(p):
    surface = surfaceLevel + fbm(x, z) · amplitude
    if p.y > surface          → AIR (or WATER below sea level)
    else by depth below surface:
        ≤ grassDepth          → GRASS
        ≤ soilDepth           → SOIL
        otherwise             → ROCK   (with 3D noise carving caves → AIR)
```

Generation is **adaptive**: a region is only subdivided into 512 children if its
sample points (8 corners + center) disagree on material. Large stretches of pure
air or pure rock collapse into a single leaf voxel, so detail concentrates around
surfaces — exactly what the tree is designed for. A leaf is always a **single
material** (a "block" is one type): a homogeneous region takes that material, and a
bottomed-out mixed region takes its *dominant* sampled material — no percentages.
Its ether is uniform (the material's appearance on all six faces); per-side
appearance only becomes meaningful higher up, once `aggregated()` summarizes each
face from the children on it. The result is returned already `aggregated()`.

The generator carries three pieces of config the world uses to drive itself:
`generationDistance` (the **full-detail radius** — within it, refinement bottoms out in
`generate`d voxel chunks; see §5), `chunkSize` (the world-space edge of one generated chunk — the
level at which refinement switches from coarse `etherOf` leaves to full voxel detail), and
`detailDepth` (how deep `generate(bounds)` subdivides a chunk; the finest voxel is
`chunkSize / 8^detailDepth`). It is **owned by the `World`** (see §5)
rather than called from outside; the standalone `generate(bounds, maxDepth)` remains for
tests and one-off builds. Making generation *pluggable* (an interface the world depends on,
with `WorldGenerator` as one impl) is a natural future step — and would also dissolve the
current `world ↔ world.gen` coupling.

### Top-down description — `etherOf(bounds)`

`generate` works **bottom-up**: build a region down to leaves, then `aggregated()` summarizes
each branch from its children. For terrain that stretches *kilometres* into the distance that
is the wrong direction — a coarse far-away sector can't afford to materialize (or even own) its
whole sub-tree just to know what it looks like. So the generator also describes a region
**top-down**: `etherOf(BoundsF64)` returns a representative `WorldSectorEtherData` (whole-cube
material + per-side `TextureProfile`) **directly from the noise, without building a sub-tree**.
It samples the region's own `8³` grid of cells (each cell's `dominantMaterial`), merges their
ids for the cube material, and averages the boundary cells' textures for each face — i.e. it
computes exactly what `aggregated()` *would* produce, but analytically. By construction
`etherOf(b)` equals `generate(b, 1).ether()` (a uniform region short-circuits to its single
exact material, like a leaf), so a coarse node and its one-level refinement **agree** — which is
what stops level-of-detail transitions from popping. This is the foundation of the top-down LoD
model: a sector can carry a faithful appearance while its detail is unloaded, so the renderer (§7)
can draw distant terrain coarsely and the tree can stay sparse far from any camera (§5).

For the *many* coarse leaves the refinement cone (§5) materializes, `etherOf`'s full
{@code 8³}-cell sampling is too costly, and a per-side summary is not worth it for something drawn
as a single box. So there is also **`representativeEtherOf(bounds)`** — the cheap counterpart: one
uniform appearance from the region's single `dominantMaterial` (a handful of samples). The few large
sectors (the root, re-root wrappers) use the faithful `etherOf`; the cone's leaves use the cheap one.

---

## 7. Rendering (`app.engine.world.render`)

Rendering is a **pure function of world state**; it owns no simulation state. Two
things make it pluggable: the world decides *what* is visible (one tree walk, shared
by every backend), and a small **`Renderer` SPI** abstracts *how* those visible units
become pixels — so the software path and the GPU path are interchangeable behind one seam.

### The backend SPI (`Renderer`)

A `Renderer` is a whole-renderer backend, deliberately coarse (3D rendering is retained
and batched, and OpenGL vs. Vulkan differ far too much to hide at the level of individual
primitives):

```java
interface Renderer extends AutoCloseable {
    Component  viewportFor(ScreenId);   // the AWT component a screen draws into
    void       setWorld(World);         // publish the latest immutable world (thread-safe)
    FrameStats stats(ScreenId);         // per-frame diagnostics (faces drawn, sectors culled)
    void       close();
}
```

The viewport is a `java.awt.Component` — the common supertype of a lightweight `JPanel`
(software) and a heavyweight `AWTGLCanvas` (OpenGL) — so the host code adds it to a window
without knowing which backend it got. There are two implementations:

- **`Graphics2DRenderer`** (backend #1) — the dependable software fallback: a `JPanel` and a
  Swing `Timer` loop driving the `WorldRenderer` `SectorDrawCollector` onto `Graphics2D`.
- **`gl.GlRenderer`** (backend #2) — the real GPU renderer (below).

A `World` is a deeply immutable value, so `setWorld` crosses from the update thread to a
renderer's own paint thread with no locking (it is just an `AtomicReference` swap).

### What is visible vs. how it looks (`World.collectSectorsForRendering`)

Deciding *what* is visible is a world concern. The whole per-frame visibility walk lives on
**`World`**, not on any renderer:

```java
World.RenderStats collectSectorsForRendering(
        ScreenId screenId, double chunkSize, SectorDrawCollector collector)
```

It is asked to render a **screen**: the screen resolves to its bound camera and pixel size
(the camera's aspect is overridden to the screen's), so the viewpoint is implied by world
state rather than passed in. (An unknown, unbound or dangling screen collects nothing —
`RenderStats.NONE`.) It walks the tree applying **frustum culling**, **occlusion culling**
and **chunking** (below), handing every render unit worth drawing to the `collector` with a
`ViewInfo` (the frame's camera + frustum + projection). A render unit is a whole sub-tree to
be meshed as one; the collector decides how it becomes pixels but never traverses the tree.
It returns a small `RenderStats` (collected, occlusion-culled).

This is the clean seam between the world and any backend (or test): the same walk serves
every renderer, and culling/chunking behaviour is unit-tested by collecting into a list with
**no surface and no renderer instantiated** (`CollectSectorsForRendering_Spec`).

### Frustum culling (deciding *whether* to descend)

The walk is gated by the camera's `frustum()`. Each sector is first tested with
`frustum.intersects(sector.bounds())`; if its bounds fall entirely outside the view volume it
is skipped, with its **entire sub-tree**. An all-empty sub-tree (`sector.isVoid()`) is pruned
just as cheaply — open sky costs nothing. So the walk only descends into the slice of the world
the camera can see. The frustum is built once per frame (cached on the camera).

### Occlusion culling (skipping what's *hidden*)

Frustum culling drops what's off-screen, not what's on-screen *behind a wall*. For that the
walk goes **near → far** (children recursed nearest-first) carrying a `CoverageGrid` — a coarse
grid of screen tiles flagged "already blocked":

- Before descending, a sector's 8 world corners are projected to a screen rectangle. If **every
  tile it touches is already covered**, the sector (and its sub-tree) is hidden behind nearer
  solid geometry, so it is skipped.
- A sector that is `isSolidOpaque()` (every voxel inside fully opaque — a perfect occluder,
  memoized lazily bottom-up) is collected as one unit and the walk **marks the tiles inside its
  silhouette**. Front-to-back order guarantees those marks come from *closer* geometry.

The rules are deliberately conservative so culling never hides something visible: marking is
*inner* (only tiles fully inside an occluder), testing is *outer* (cull only if the whole
rectangle is covered). One test in front of a wall prunes everything behind it.

### Level of detail (deciding *how deep* to descend, and *how finely* to mesh)

For each surviving sector the walk measures its on-screen size with the **same** `detailCells` metric
the world refines by (§5) — `LOD_DETAIL × radius / distance`, **resolution-independent**, no pixels —
so the renderer and the world never disagree about what detail exists where. Then:

- a **leaf**, a **solid occluder**, a sector at or below the **`chunkSize` floor**, or one small
  enough on screen to span only a few cells (`LOD_COLLAPSE_CELLS`) is **collected as one render
  unit**, together with the chosen grid **resolution** (`meshResolutionFor` — a power of
  `RESOLUTION`: `1, 8, 64`);
- a sector still **big on screen above the floor** is **descended into** (nearest child first), so
  its children carry the detail and occlusion can act between them.

So `detailCells` picks both *what* to collect and *how finely* to mesh it: near terrain is handed over
as `chunkSize`-sized units at full resolution; distant terrain as a few **big coarse units**. The
`chunkSize` floor is never descended below, so near terrain stays batched into chunk-sized meshes.
Because the world's refine threshold (`REFINE_CELLS`) sits below the renderer's descend threshold
(`LOD_COLLAPSE_CELLS`) on this shared scale, every coarse sector the renderer draws at res-8 is
guaranteed to have the sub-sectors it meshes from. (`projectedEdgePixels`/`focalLengthPx` remain for the
actual camera projection in `ViewInfo`, no longer for the LoD decision.)

### Meshing a unit at a level of detail: face culling + greedy meshing (deciding *which faces*)

Turning a unit into one cube per voxel is wasteful: a solid region draws the faces *between*
adjacent voxels only to overdraw them. So `SectorMeshCache` meshes a collected unit over a
**rasterized voxel grid** at the requested **resolution**: it descends the sub-tree filling a flat
`res³` grid, then **greedy-meshes** it. At full resolution each grid cell is a leaf's ether; at a
**coarser resolution the rasterizer stops higher up and fills each cell with that *branch*
sector's own (LoD-aggregated) ether** — so a distant unit is greedy-meshed *from big blocks of
branch sectors, not leaves*, for a few coarse quads instead of thousands (a checkerboard chunk
that needs hundreds of faces up close collapses to a 6-face box far away). For each face direction
and layer a face is kept only if the neighbouring grid cell is empty (culling **internal faces**
between adjacent voxels *and* between adjacent sub-blocks), then coplanar adjacent faces of the
same appearance (`TextureProfile`) merge into the largest rectangles. Merging stops at appearance
boundaries. (Valid resolutions are powers of `RESOLUTION` — `1, 8, 64` — since each tree level
divides the grid by that factor.)

Meshing is relatively expensive — but a `WorldSector` is an **immutable value**, the perfect cache
key. `SectorMeshCache` keys a `WeakHashMap<WorldSector, …>` by sector (so meshes of unreferenced
sectors are GC'd) and, within it, caches each resolution the sector was meshed at: a unit
re-encountered at the same distance reuses its mesh for free. Lookups stay cheap because
`WorldSector` **memoizes its deep hash** and `equals` short-circuits on identity — which is
precisely why the §4/§5 *structure-sharing* aggregation is what keeps this cache hitting as new
terrain streams in. (Unit-boundary faces are drawn conservatively — we don't peek into the
neighbouring unit — a small, correct over-draw.)

`SectorGeometry.emitChunk` adapts a collected unit (and its resolution) into the stream of
world-space **`Quad`s** (4 corners + outward normal + per-face `TextureProfile`) both backends
consume. The retained GPU backend keys its VBOs by `(sector, resolution)`, so the same sector
drawn at two distances keeps two meshes.

### Backend #1 — software (`WorldRenderer` / `Graphics2D`)

`WorldRenderer` is the `SectorDrawCollector` that targets `Graphics2D`. Per quad it does
back-face culling, projects the 4 corners via the view-projection matrix (skipping anything
at/behind the camera or off-viewport), applies flat directional `Shading`, depth-sorts
far→near (painter's algorithm) and fills with `Graphics2D.fillPolygon(int[],int[],4)`. Face
colour comes from `TexturePalette` — an intensity-weighted blend of a tint per `Texture`,
**memoized per `TextureProfile`**. Its `fillPolygon` throughput is the ceiling that motivated
the GPU backend; it stays as the reliable, surface-free fallback.

### Backend #2 — OpenGL (`gl.GlRenderer`)

The GPU backend draws directly into a heavyweight `AWTGLCanvas` (GL 3.3 core, 24-bit depth) —
no read-back, a real **z-buffer** (early-z kills overdraw; no painter's sort). A dedicated
`gl-renderer` daemon thread owns the GL context and all GL objects; it paints each viewport on
its own loop, reading the latest world from the `AtomicReference`.

- **Retained per-chunk geometry.** Each collected chunk is meshed once, uploaded to its own
  VBO/VAO, and **kept on the GPU keyed by the immutable `WorldSector`** (a GPU mirror of
  `SectorMeshCache`). Because structural sharing keeps static terrain's chunk instances stable,
  those chunks are **never re-meshed or re-uploaded** — the cache hits on identity. Chunks
  unused for a number of frames are evicted.
- **Procedural textures.** Each distinct appearance (`TextureProfile`) is baked once by
  `TextureBaker` into a seamless tile and uploaded into one layer of a `GL_TEXTURE_2D_ARRAY`
  (one bound texture for the whole frame). `TextureBaker` colours a base tint from
  `TexturePalette` and modulates it with an intensity-weighted blend of the engine-owned
  **`Noise`** patterns the profile's qualities call for (grainy→speckle, fibrous→wood grain,
  crystalline→facets, liquid→smooth waves, …); seamlessness comes from a 4-corner cross-fade
  over the tile period. `Noise` is our own value-noise/fBm/Worley toolkit — inspired by the
  classic techniques, not a dependency.
- **Texture LoD (mip pyramid).** Tiles bake at high resolution so close surfaces stay crisp,
  and a full box-filtered mip chain (`glGenerateMipmap`) is built so distant surfaces sample a
  pre-averaged level instead of aliasing the high-frequency noise. Minification is trilinear,
  with anisotropic filtering where the driver offers it.
- **Vertices** carry position, **world-space UVs** (taken from the quad's in-plane axes, so
  `GL_REPEAT` tiles continuously and adjacent quads line up), the appearance's texture-array
  layer, and the flat `Shading` brightness as a scalar the fragment shader multiplies the texel
  by. A `sampler2DArray` resolves the layer.

The OpenGL/Vulkan/WebGPU choice lives behind the `Renderer` SPI: OpenGL first (AWT-surface
integration, simplest path); a Vulkan/WebGPU backend can slot in later without touching the world.

### Result

A recognizably blocky landscape (grass surface, soil/rock below, blue sky) — flat-shaded under
the software backend, and under the GPU backend richly **procedurally textured** (speckled grass,
rough rock, grainy sand) with crisp detail up close and clean, mip-filtered terrain into the
distance.

---

## 8. Running the demo

`app.engine.world.demo.WorldEngineDemo` is a self-contained, runnable window. The Gradle task
picks the backend behind the `Renderer` SPI:

```bash
./gradlew runWorldDemo                       # software Graphics2D backend (default)
./gradlew runWorldDemo -Dengine.renderer=gl  # OpenGL backend
```

It builds an **infinite** generator-backed world (`World.of(generator)`, 64-unit chunks), then
drives everything through the **real engine pipeline**: it `createCamera`s a camera entity,
`createScreen`s a screen bound to it, picks a `Renderer` from the `engine.renderer` property and
adds `renderer.viewportFor(screenId)` to the frame (a `JPanel` for software, an `AWTGLCanvas` for
GL — same call site either way). There is no fixed region — the world owns the `WorldGenerator`
and streams chunks in via `World.update` around the camera, growing its tree as you fly. The demo
only *translates* raw Swing input into `ScreenInputEvent`s and calls
`world.update(EngineInputs(dt, {screen: events}))`; the panel's size is mirrored onto the screen
via `resizeScreen`. The camera orbits on its own until you press a movement key (**W/A/S/D**,
**Q/E** or **Space** for down/up, **Shift** to sprint) or **move the mouse**, at which point
control passes to you — the fly logic lives in the engine (`CameraFlight`, exercised by
`World.update`), not the demo. It is intentionally isolated from the main Tribalism application.

Because a `World` is a **deeply immutable value, it crosses threads with no locking**: the demo
runs `World.update` (input + terrain generation) on a background **`world-updater`** thread,
publishing each new world to the renderer via `setWorld` (an `AtomicReference` swap), while the
backend paints the latest published world on its own loop (a Swing `Timer` for software; the
dedicated `gl-renderer` thread for GL). So the next world is computed while the current one is
drawn. Input events flow EDT → updater through a `ConcurrentLinkedQueue`; the EDT only refreshes
a diagnostics title from `renderer.stats(screenId)`. Nothing else is shared.

---

## 9. Testing

Spock specifications live under `src/test/groovy/engine/`, mirroring the package
structure:

| Spec | Covers |
|------|--------|
| `primitives/VecF64_Spec`    | vector algebra, value semantics, lerp/distance |
| `primitives/BoundsF64_Spec` | containment, `subdivide` grid ordering, union |
| `primitives/Mat4F64_Spec`   | immutability, transforms, `M·M⁻¹ = I`, singular detection |
| `primitives/CameraF64_Spec` | forward/view matrix, frustum validation, value equality, matrix memoization |
| `primitives/Frustum_Spec`   | plane extraction, conservative box culling, point containment |
| `util/Lazy_Spec`            | compute-once memoization, concurrent first-access safety |
| `world/TextureProfile_Spec` | independent qualities, clamping, averaging, blend, invisibility, value equality, per-face inset (clamp, value identity, `sameAppearance` ignores it, averaged) |
| `world/MaterialId_Spec`     | Specific vs Diverse, merge of agreeing/disagreeing ids |
| `world/Material_Spec`       | starter registry, unique ids, air = invisible null substance |
| `world/WorldSectorEtherData_Spec` | one material per cube + per-side appearance, combined, value equality, per-side insets + `shrink` (fit box, identity when none, slab on crossed insets) |
| `world/SectorAggregation_Spec` | appearance averaged from boundary sub-sectors, material merge, solid-occluder detection (bottom-up coherence) |
| `world/LightSource_Spec`    | sum-type variants, bounds, exhaustive matching |
| `world/WorldTree_Spec`      | 512-node layout, fall-down, per-side LoD + material merge |
| `world/Entity_Spec`         | camera/voxel entities, sum-type matching |
| `world/World_Spec`          | add/remove/move keeping tree + lookup in sync |
| `world/WorldScreens_Spec`   | screen/camera lifecycle, one-way binding, resize, cameras stay out of the tree |
| `world/WorldUpdate_Spec`    | inputs → camera mutations, held keys remembered between updates, unbound no-op |
| `world/WorldGeneration_Spec`| infinite LoD refinement: starts as one coarse sector, refines full detail near a camera + coarse far, larger reach refines further, root grows to follow a far camera, idempotent once settled, no-generator skip, detail collapses behind a moved camera + re-refines losslessly on return, movement gate (tiny moves skipped, count-change/far-move re-walk), generator insets wired through to the world's coarse leaves |
| `world/CameraFlight_Spec`   | pure free-fly math: move/strafe/sprint/look, dt scaling, no-input identity |
| `world/CollectSectorsForRendering_Spec` | the visibility walk (frustum + occlusion + chunk floor + distance-based level-of-detail resolution) tested with no renderer |
| `world/CoverageGrid_Spec`   | conservative mark/test, off-screen handling, occlusion of covered rects |
| `world/gen/PerlinNoise_Spec`| determinism, range, lattice zeros |
| `world/gen/WorldGenerator_Spec` | material classification, adaptive subdivision, reproducibility, top-down `etherOf` (faithful to a one-level build, coarse description without a sub-tree), top-down `insetsOf` (surface region recesses its top, uniform region has none, deterministic) |
| `world/render/WorldRenderer_Spec` | culling maths, frustum culling, majority-opaque, texture→colour, occlusion culling behind solids, render smoke test |
| `world/render/SectorMeshCache_Spec` | chunk face culling (incl. internal sub-block boundaries), greedy merge (per-appearance, height-independent), coarse meshing from branch sectors at a chosen resolution, res-1 box shrunk to the ether's per-side insets, mesh memoization |
| `world/render/TextureBaker_Spec`    | procedural tiles: sized + opaque, deterministic, varied (not flat), per-appearance distinct, air bakes cleanly |

Run them with:

```bash
./gradlew test --tests "engine.*"
```

---

## 10. Status & next steps

**Built:** math primitives (incl. a lazily-caching `CameraF64` and a `Frustum`);
the full immutable world tree (sectors, nodes, ether, entities, lights, traces);
the appearance/material model (`Texture` qualities, `TextureProfile`, `MaterialId`
sum type, `Material` starter registry); entity fall-down, per-side LoD aggregation —
now **incremental and structure-sharing** (splicing a chunk re-aggregates only its
root-to-leaf path, `withAggregatedChildren`, leaving every other chunk identity-stable) —
and per-side **insets carried on each face's `TextureProfile`**, described top-down by the
generator; the `Entity` sum type; the `World` **value class**
with multi-**screen** support (screens bound one-way to camera entities by id), an
event-based input model (`EngineInputs` → per-screen `ScreenInputs` event logs) and a
`World.update` step that folds input into **camera mutations** via the pure `CameraFlight`
controls (held state remembered between updates); a **`WorldGenerator` owned by the world**
that `update` uses to **stream** terrain in chunks around cameras within a configured
`generationDistance` — **budgeted** (a bounded number of nearest chunks per tick) so flying
never stalls, and **evicted** again past an unload radius (deterministic regeneration makes this
lossless) so memory stays bounded — an effectively **infinite world** whose octree root grows
outward (re-roots) to follow cameras wherever they fly. Rendering is a swappable **`Renderer` SPI** with two
backends: a software **`Graphics2D`** path (frustum + near→far occlusion culling, chunk-sized
greedy-meshed surfaces, painter's sort — the dependable fallback) and a real **OpenGL**
backend (`GlRenderer`: a true z-buffer, retained per-chunk VBOs keyed by the immutable sector,
and **procedural textures** baked from the engine-owned `Noise` into a **mip-mapped**
`GL_TEXTURE_2D_ARRAY`). A free-fly **and** auto-orbit demo drives either backend end-to-end
through `World.update` on a **background thread** (the immutable world is rendered while the
next one is computed), with a live face-count / cull-count HUD. The hot aggregation path is
allocation-/hash-free (flat ordinal-indexed arrays for `TextureProfile` / `WorldSectorEtherData`),
and `WorldSector` memoizes its deep hash and bottom-up predicates so it is a cheap cache key.

### The long-term rendering vision

The split introduced by the appearance/material model is deliberate and points at
the real renderer to come:

- **Appearance drives the picture.** A surface's look is a `TextureProfile` — a set
  of independent, shader-friendly *hints* (`GRAINY`, `LIQUID`, `REFLECTIVE`,
  `MOLTEN`, `HAIRY`, …), not a fixed image or a material enum. The intended renderer
  feeds these straight into a **procedural noise shader**: each quality biases the
  noise field and lighting model (its frequency and anisotropy, specular/metallic
  response, emission, displacement, surface flow, …) so a material's appearance
  *emerges procedurally* and scales to thousands of materials without texture
  assets. The GPU backend's `TextureBaker` is the **first realization** of this — it
  bakes each `TextureProfile` from the engine's own `Noise` into a tiled, mip-mapped
  texture; folding the qualities into a richer lit/displaced *fragment* shader (rather
  than a pre-baked tile) is the next step. `TexturePalette`'s flat tint-blend remains
  only as the software backend's stand-in and the baker's base colour.
- **Material is for gameplay.** The single per-cube `MaterialId` is what the
  simulation reasons about ("this block is ore"); the renderer ignores it. No
  percentages, no "what's inside" — at the point something matters to a player it is
  already a discrete item, not a fraction of a voxel.

**Not yet built (future steps):**

- **Top-down level-of-detail — kilometre view distance at bounded cost (done).** Terrain is drawn
  kilometres out and the tree stays **sparse** (a coarse sector exists without its sub-tree), so
  neither memory nor render cost scales with view distance. Three steps, all landed:
  **(1)** `WorldGenerator.etherOf(bounds)` describes a region top-down (§6); **(2)** the render walk
  descends by *projected size* and **greedy-meshes coarse branch sectors** at a chosen resolution
  (§7); **(3)** the generation model is a single **refinement-driven LoD octree** (§5) — coarse
  nodes exist childless (described by `etherOf`/`representativeEtherOf`), the walk refines toward
  cameras and collapses away (subsuming the old chunk-grid streaming + eviction). `aggregated()` now
  remains only for *edited* sub-trees the generator can't describe — which dovetails with persistence.
  Refinement is restricted to **non-homogeneous (surface) sectors** so the cone follows the 2D terrain
  surface, not the 3D volume; and the world and renderer share **one resolution-independent LoD metric**
  (`detailCells`, §5/§7) with the refine threshold below the renderer's draw threshold, so drawn coarse
  sectors keep children and are **greedy-meshed at res-8 from their sub-sectors** (surface shape), not
  flat boxes — and the result no longer changes with window size. *Tuning follow-ups:* the cost of res-8
  coarse terrain is the 512-branching (each surface sector drags 512 children, mostly homogeneous) — a
  compact per-node baked LoD grid would cut that; LoD-pop smoothing (geomorph) is open. `LOD_DETAIL` is
  the single quality knob, with `VIEW_DISTANCE` / `REFINE_BUDGET_PER_UPDATE` for reach and streaming rate.
- **Disk persistence for *edited* sectors (when edits exist).** Refinement + deterministic
  regeneration bound memory today, so pristine terrain needs no disk — regeneration *is* the
  persistence. The remaining step lands once a sector can carry changes **not** reproducible from
  the seed (player/AI edits): tag sectors pristine vs modified, and have the **collapse** step (§5)
  write only *modified* sub-trees before dropping them, reading them back on re-refine. Planned
  design: persist at sector granularity keyed by integer grid coordinate (not float bounds — fragile
  in filenames), bucketed into **region files**; store only the sub-tree *shape* + **leaf material
  ids** (branch ether recomputed via `aggregateEtherOf`/`etherOf`, bounds from tree position), so it
  is a tiny, compressible stream; do the I/O **async** off the update thread (the loop only splices
  in completed loads, exactly as it adopts completed generations). A natural companion is making a
  coarse leaf a **lazy/loadable handle** (*coarse | refined | on-disk*).
- **Parallel generation** on a worker pool — now worthwhile, since a tick no longer redoes O(world)
  work; refinement requests fan out and completed sub-trees splice in over following ticks.
- A richer GPU **fragment shader** consuming `TextureProfile` hints directly (lit/displaced/
  flowing), beyond today's pre-baked tiles; plus dynamic 64→32-bit scaling concerns.
- Extending `World.update` beyond camera control: **entity behaviour** and **light-trace
  propagation/radiation** as part of the same per-tick step.
- Making **generation pluggable** (an interface the world depends on), which would also break
  the current `world ↔ world.gen` package coupling.
- Recursive sub-entities inside `VoxelEntity`.
- A richer, registry-backed material system (resolving `MaterialId` to gameplay substances) as
  the world gains items and interactions.
- Integration of a world view into the main Tribalism application.