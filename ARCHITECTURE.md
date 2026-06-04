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
    ├── TextureProfile          A set of independent Texture intensities [0,1] (the look of a face)
    ├── MaterialId              Sum type: Specific(int) | Diverse (what a cube is made of)
    ├── Material                Starter substance registry (id + name + default TextureProfile)
    ├── Side                    One of the six cube faces (NEG_X … POS_Z)
    ├── SideInsets              Per-face content recess [0,1] (lets LoD boxes fit content)
    ├── WorldSectorEtherData    Ether: one MaterialId per cube + one TextureProfile per Side
    ├── WorldTreeEntityId       Positional handle: long id + bounds
    ├── LightSource             Sealed light sum type (Sphere | Cube | Plane)
    ├── LightTrace              A ray of light radiating from a source
    ├── WorldTreeNode           A node = Tuple of exactly 512 sectors (8×8×8)
    ├── WorldSector             The recursive cell of the world (the heart)
    ├── Entity                  Sealed entity sum type (CameraEntity | VoxelEntity)
    ├── World                   The whole world as one value (root + entity lookup)
    │
    ├── gen              Procedural generation
    │   ├── PerlinNoise         Deterministic, seeded 3D gradient noise + fbm
    │   └── WorldGenerator      Adaptive noise → sector tree
    │
    ├── render           First-draft Graphics2D rendering
    │   ├── TexturePalette      TextureProfile → AWT Color (keeps AWT out of the model)
    │   ├── Quad                One world-space face (4 corners + normal + profile)
    │   ├── Cubes               Bounds + Side → face Quad (shared box/mesh geometry)
    │   ├── SectorMesh          A sector's occlusion-culled set of visible faces
    │   ├── SectorMeshCache     Builds + memoizes meshes, keyed by the (immutable) sector
    │   └── WorldRenderer       Walks the tree: frustum + LoD + occlusion culling → quads
    │
    └── demo
        └── WorldEngineDemo     Self-contained, runnable demo window
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
maps grid coordinates to that index.

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
    Lazy<SideInsets>            insets;        // derived: how far content is recessed per face
}
```

It is a `final class` rather than a `record` for the same reason as `CameraF64`:
so it can encapsulate one *derived, lazily-memoized* field — its
[`SideInsets`](#side-insets) — while still behaving as an immutable value (its
`equals`/`hashCode` cover only the six defining fields, never the cache).

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
        (leaf voxel)        Tuple<WorldSector>[512]
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
- **`TextureProfile`** — a set of `Texture` intensities in `[0, 1]`, stored in an
  `Association<Texture, Double>`. Crucially they are **independent** and need
  **not sum to anything** — a surface can be a "grainy liquid" with both at full
  strength. `none()` (every quality 0) is the null object: invisible empty space.
  Provides `intensityOf`, `with` (clamped), `isInvisible`, `blend`, and the static
  **`average(samples)`** used for level-of-detail aggregation.
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
  appearance). Provides `material()`/`withMaterial`, `sideOf(side)`/`withSide`, and
  `combined()` (the average of all six side profiles).

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

**3. Side insets — `WorldSector.insets()`** <a id="side-insets"></a>

Per-side appearance fixes the *colour* of a coarse LoD box, but not its *shape*: a
"half-full" sector (solid bottom, air top) drawn as a full cube would either stick
out into empty air or, if skipped, leave a hole. **`SideInsets`** fixes the shape.
Each face carries an inset in `[0, 1]` — the fraction of the sector's extent by
which its content is recessed from that face — and the renderer shrinks the drawn
box accordingly.

Insets are derived bottom-up and **memoized as a single `Lazy<SideInsets>`** on the
sector (a leaf has none — all zero). For each face a branch runs an *inward-moving*
algorithm: starting at the face it peels off whole child layers
(`WorldTreeNode.layerCells(side, depth)`) while **every** sub-sector in the layer
is `isFullyTransparent()`, adding one full layer of inset each time; at the first
layer that holds content it adds the *smallest* matching-side inset among that
layer's non-transparent children (so the inset refines recursively, below child
granularity) and stops. A solid bottom / air top thus yields `POS_Y = 0.5`,
`NEG_Y = 0`; a fully empty branch yields `1.0` on every face.

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

- **`CameraEntity(treeId, CameraF64 camera)`** — a viewpoint into the world.
- **`VoxelEntity(treeId, WorldSector sector)`** — *itself a small world*: its
  shape and material are a nested `WorldSector` (with the full recursive
  machinery), letting the entity move freely relative to the world it belongs to.
  (Recursive sub-entities — e.g. a knight holding a sword — are a future step.)

### `World` (the top-level value)

```java
record World(
    WorldSector             root,      // root of the spatial tree
    Association<Long, Entity> entities   // id → actual entity
)
```

The `root` is used purely for positional queries (it holds only
`WorldTreeEntityId`s); the `entities` association is the authoritative store of
actual entities. `withEntity` / `withoutEntity` / `withMovedEntity` keep the two
in sync — inserting/removing the positional handle in the tree *and* updating the
lookup. `World` is the value an update loop transforms tick to tick.

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

---

## 7. Rendering (`app.engine.world.render`)

The renderer is a **pure function of world state**; it does not own any
simulation state.

### Frustum culling (deciding *whether* to descend)

Before anything else, the tree walk is gated by the camera's `frustum()`. As it
recurses, each sector is first tested with `frustum.intersects(sector.bounds())`;
if the sector's bounds fall entirely outside the view volume it is skipped — and
with it its **entire sub-tree**. So the renderer only ever descends into the
fraction of the world the camera can actually see, instead of traversing the whole
tree every frame. The frustum is built once per frame (cached on the camera) and
threaded down the recursion.

### Level-of-detail selection (deciding *how deep* to descend)

For sectors that survive culling, `WorldRenderer` estimates how big each would
appear on screen:

```java
projectedEdgePixels(edgeLength, distance, focalLengthPx) = edgeLength · focal / distance
focalLengthPx(camera, viewportHeight)                    = (height/2) / tan(fovY/2)
```

If a sector's projected edge is *below* the threshold it is drawn as a single
coarse "super-voxel" (one inset-fitted box); otherwise it needs detail and the
renderer descends. Thus distant geometry is drawn coarsely (high in the tree) and
nearby geometry finely. These functions are pure and unit-tested.

### Occlusion culling (deciding *which faces*)

Descending all the way to individual leaf voxels and drawing each as a cube is
wasteful: a solid region draws the faces *between* adjacent voxels, only to overdraw
them. So when the walk reaches a **full-detail block** — a branch whose children are
all leaves (an 8×8×8 grid of voxels) — it does not recurse into 512 cubes. Instead
it draws the block's **`SectorMesh`**: the set of *exposed* voxel faces, where a face
is kept only if the neighbouring voxel in that direction is empty (or lies outside
the block). Faces buried between two opaque voxels are dropped, collapsing a solid
block from up to `512·6 = 3072` faces to its outer shell (e.g. `384`).

Meshing a block is relatively expensive — but a `WorldSector` is an **immutable
value**, so it is the perfect cache key. `SectorMeshCache` is a `WeakHashMap<WorldSector,
SectorMesh>`: a block re-encountered next frame reuses its mesh for free, and meshes
for blocks the world no longer references are garbage-collected. Lookups stay cheap
because `WorldSector` **memoizes its (otherwise deep) hash code**, and `equals`
short-circuits on identity for the common "same instance again" hit. (Block-boundary
faces are drawn conservatively — we don't peek into the neighbouring block — a small,
correct over-draw. *Greedy meshing* of coplanar same-appearance faces is a natural
future win on top of this.)

### Drawing

The walk produces a flat list of world-space **`Quad`s** from two sources:

- **Coarse boxes** (distant sectors / lone big leaves). A box is emitted only if its
  sector is **majority opaque** — `combined()` `OPACITY` ≥
  `TexturePalette.VISIBILITY_THRESHOLD` (`isMajorityOpaque`) — so a mostly-empty
  super-voxel isn't inflated into a full block. The box is first **shrunk to fit its
  content** (`sector.insets().shrink(sector.bounds())`) so it stops at the content
  surface (no protrusion, no hole). Each face is coloured from **its own `Side`'s**
  `TextureProfile` (`faceProfile`) — grassy on top, rocky on the sides — falling back
  to the sector's `combined()` profile if an aggregated face came out invisible (so a
  drawn box is never holey).
- **Mesh quads** (full-detail blocks), straight from the cached `SectorMesh`.

All quads are then handled uniformly:

1. **back-face culling** (only faces whose outward normal points toward the camera),
2. projecting the 4 corners to screen via the camera's view-projection matrix
   (skipping a quad if any corner is at/behind the camera),
3. flat directional shading (ambient floor + diffuse against a fixed light),
4. depth-sorting far-to-near (painter's algorithm, per quad) and filling the polygon
   via `Graphics2D`.

`TexturePalette` derives a face's colour from its appearance qualities — an
intensity-weighted blend of a tint per `Texture` (grainy→tan, liquid→blue,
hairy/mossy→green, molten→orange, …). This is a **crude stand-in for a future
procedural noise shader** (§10) and keeps AWT out of the data model. Note the
renderer never looks at `MaterialId`: the *appearance* drives the picture, while
the material is reserved for gameplay.

### Result

The output is a recognizably Minecraft-style blocky landscape (grass surface,
soil/rock below, blue sky), with visibly coarser blocks toward the horizon from
the LoD selection.

---

## 8. Running the demo

`app.engine.world.demo.WorldEngineDemo` is a self-contained, runnable window:

```bash
# via the project's run tooling, or directly:
java -cp <classpath> app.engine.world.demo.WorldEngineDemo
```

It procedurally generates a 128-unit landscape (seed `1337`, depth 2), adds a
`CameraEntity`, and orbits the camera around the world with a ~60 FPS Swing timer,
re-rendering each frame. Pressing a movement key (**W/A/S/D**, **Q/E** or
**Space** for down/up, **Shift** to sprint) or **moving the mouse** hands control
to a **free-fly camera** — seeded from the current orbit pose so the view never
snaps — for inspecting the rendering up close. It is intentionally isolated from
the main Tribalism application.

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
| `world/TextureProfile_Spec` | independent qualities, clamping, averaging, blend, invisibility |
| `world/MaterialId_Spec`     | Specific vs Diverse, merge of agreeing/disagreeing ids |
| `world/Material_Spec`       | starter registry, unique ids, air = invisible null substance |
| `world/WorldSectorEtherData_Spec` | one material per cube + per-side appearance, combined |
| `world/SideInsets_Spec`     | clamping, per-face lookup, box shrink, collapse on cross |
| `world/SectorInsets_Spec`   | inward-layer inset algorithm, recursive refinement, appearance derived from sub-sectors |
| `world/LightSource_Spec`    | sum-type variants, bounds, exhaustive matching |
| `world/WorldTree_Spec`      | 512-node layout, fall-down, per-side LoD + material merge |
| `world/Entity_Spec`         | camera/voxel entities, sum-type matching |
| `world/World_Spec`          | add/remove/move keeping tree + lookup in sync |
| `world/gen/PerlinNoise_Spec`| determinism, range, lattice zeros |
| `world/gen/WorldGenerator_Spec` | material classification, adaptive subdivision, reproducibility |
| `world/render/WorldRenderer_Spec` | LoD maths, frustum culling, majority-opaque, texture→colour, render smoke test |
| `world/render/SectorMeshCache_Spec` | occlusion culling (interior faces dropped), shared-face culling, mesh memoization |

Run them with:

```bash
./gradlew test --tests "engine.*"
```

---

## 10. Status & next steps

**Built:** math primitives (incl. a lazily-caching `CameraF64` and a `Frustum`);
the full immutable world tree (sectors, nodes, ether, entities, lights, traces);
the appearance/material model (`Texture` qualities, `TextureProfile`, `MaterialId`
sum type, `Material` starter registry); entity fall-down, per-side LoD
aggregation and lazily-derived per-side `SideInsets`; the `Entity` sum type and
`World` value; procedural generation; first-draft Graphics2D rendering with
distance LoD, frustum culling, inset-fitted LoD boxes **and cached, occlusion-culled
voxel meshes**; a runnable demo.

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
  assets. `TexturePalette`'s tint-blend is just the throwaway 2D stand-in for this.
- **Material is for gameplay.** The single per-cube `MaterialId` is what the
  simulation reasons about ("this block is ore"); the renderer ignores it. No
  percentages, no "what's inside" — at the point something matters to a player it is
  already a discrete item, not a fraction of a voxel.

**Not yet built (future steps):**

- The **update loop** that advances `World` tick to tick (entity behaviour,
  light-trace propagation/radiation).
- **Generation around camera entities** within a radius (streaming the world as
  the camera moves), rather than a single fixed region.
- Recursive sub-entities inside `VoxelEntity`.
- The **procedural noise shader** that consumes `TextureProfile` hints (replacing
  the first-draft `Graphics2D`/`TexturePalette` path), plus dynamic 64→32-bit
  scaling for GPU rendering.
- **Greedy meshing** (merging coplanar same-appearance quads) and content-keyed,
  position-independent meshes on top of the current `SectorMeshCache`.
- A richer, registry-backed material system (resolving `MaterialId` to gameplay
  substances) as the world gains items and interactions.
- Integration of a world view into the main Tribalism application.