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
├── primitives          Math primitives (pure 64-bit value types)
│   ├── VecF64          3D vector
│   ├── BoundsF64       Axis-aligned bounding box
│   ├── Mat4F64         4×4 matrix (transforms, projection)
│   └── CameraF64       A viewpoint + frustum, derives view/projection matrices
│
└── world               The world data model and its tooling
    ├── Material                Enum of materials (AIR, WATER, SOIL, GRASS, …)
    ├── MaterialDistribution    A mixture of material fractions (e.g. 90% air, 10% rock)
    ├── Side                    One of the six cube faces (NEG_X … POS_Z)
    ├── WorldSectorEtherData    Per-side ether: one MaterialDistribution for each Side
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
    │   ├── MaterialPalette     Material → AWT Color (keeps AWT out of the model)
    │   └── WorldRenderer       Walks the tree, draws shaded voxels with LoD
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
A viewpoint as pure data: `position`, `target`, `up`, plus the frustum
(`fovYRadians`, `aspect`, `near`, `far`). It derives `viewMatrix()` (look-at),
`projectionMatrix()` (perspective) and `viewProjectionMatrix()`. Rendering stays
a separate function of this state.

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
record WorldSector(
    BoundsF64                   bounds,       // the region it occupies
    WorldSectorEtherData       ether,        // what it is made of
    ValueSet<WorldTreeEntityId> entities,     // entities positioned here
    ValueSet<LightSource>       lights,       // lights positioned here
    Tuple<LightTrace>           lightTraces,  // light radiating through here
    @Nullable WorldTreeNode     children      // null = leaf voxel; else 512 sub-sectors
)
```

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

### Material & ether data

- **`Material`** — an enum: `AIR, WATER, SOIL, GRASS, SAND, ROCK, WOOD, METAL,
  ORGANIC`.
- **`MaterialDistribution`** — a mixture of material fractions (e.g. 90% air, 5%
  soil, 5% rock), stored in an `Association<Material, Double>`. Provides
  `fractionOf`, `dominantMaterial`, `normalized`, `blend`, and the static
  **`average(samples)`** used for level-of-detail aggregation.
- **`Side`** — one of the six cube faces (`NEG_X, POS_X, NEG_Y, POS_Y, NEG_Z,
  POS_Z`), each with an `axis()`, `isPositive()`, outward `normal()` and
  `opposite()`.
- **`WorldSectorEtherData`** — *what a sector is made of*, stored as **one
  `MaterialDistribution` per `Side`** rather than a single whole-sector mixture.
  Provides `sideOf(side)`, `withSide(side, dist)`, the convenience `combined()`
  (the average of all six sides as one mixture) and `dominantMaterial()`.

  Storing material *per face* is the key to correct, cheap level of detail:
  materials are primarily a **visual** property, and only the outer faces of a
  cube are ever seen. So a super-sector summarizes each of its faces from only
  the matching faces of the sub-sectors lying on that face — never the hidden
  interior.

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

A parent sector summarizes its whole sub-tree **per side**. Leaves keep their own
ether; a branch first aggregates each child recursively, then computes each of
its six faces independently: for a given `Side`, it averages that same face of
only the sub-sectors on the parent's boundary layer for that side
(`WorldTreeNode.boundaryCells(side)` — the 8×8 = 64 children touching that face).
The hidden interior never contributes.

Because each face averages over a 64-cell layer (not the full 512-cell volume), a
single deep voxel contributes `1/64` per level it climbs on the faces it lies on,
and `0` to the faces it never touches. So any sub-tree collapses into one
*visually faithful* representative voxel — e.g. a super-sector straddling the
ground shows grass/soil on its `POS_Y` (top) face and rock on `NEG_Y` (bottom) —
which is what makes cheap, directionally-correct LoD rendering possible.

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
surfaces — exactly what the tree is designed for. Leaf ether is **uniform** (the
same sampled mixture on all six sides, since a leaf has no directional detail);
the per-side ether only becomes meaningful higher up, once `aggregated()`
summarizes each face from the children on it. The result is returned already
`aggregated()` for correct LoD ether.

---

## 7. Rendering (`app.engine.world.render`)

The renderer is a **pure function of world state**; it does not own any
simulation state.

### Level-of-detail selection

`WorldRenderer` walks the tree from the root and, for each sector, estimates how
big it would appear on screen:

```java
projectedEdgePixels(edgeLength, distance, focalLengthPx) = edgeLength · focal / distance
focalLengthPx(camera, viewportHeight)                    = (height/2) / tan(fovY/2)
```

If a sector's projected edge exceeds a pixel threshold **and** it has children,
the renderer recurses; otherwise it draws the sector as a single "super-voxel".
Thus distant geometry is drawn coarsely (high in the tree) and nearby geometry
finely — the LoD story made visible. These functions are pure and unit-tested.

### Drawing

Renderable voxels (those with at least one non-AIR face) are collected, sorted
far-to-near (painter's algorithm), and each cube is drawn by:

1. projecting its 8 corners to screen via the camera's view-projection matrix
   (skipping voxels with a corner at/behind the camera),
2. **back-face culling** (only faces whose outward normal points toward the
   camera),
3. colouring each face by **its own `Side`'s** dominant material
   (`ether.sideOf(side).dominantMaterial()`), skipping faces that are AIR — so a
   single super-voxel can be, say, grass on top and rock on the sides,
4. flat directional shading (ambient floor + diffuse against a fixed light),
5. filling the face polygons via `Graphics2D`.

`MaterialPalette` maps each material to an AWT `Color`, keeping AWT out of the
data model.

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
`CameraEntity`, and orbits the camera around the world with a ~30 FPS Swing timer,
re-rendering each frame. It is intentionally isolated from the main Tribalism
application.

---

## 9. Testing

Spock specifications live under `src/test/groovy/engine/`, mirroring the package
structure:

| Spec | Covers |
|------|--------|
| `primitives/VecF64_Spec`    | vector algebra, value semantics, lerp/distance |
| `primitives/BoundsF64_Spec` | containment, `subdivide` grid ordering, union |
| `primitives/Mat4F64_Spec`   | immutability, transforms, `M·M⁻¹ = I`, singular detection |
| `primitives/CameraF64_Spec` | forward/view matrix, frustum validation |
| `world/MaterialDistribution_Spec` | fractions, dominant material, normalize, averaging |
| `world/WorldSectorEtherData_Spec` | per-side distributions, uniform/combined, value semantics |
| `world/LightSource_Spec`    | sum-type variants, bounds, exhaustive matching |
| `world/WorldTree_Spec`      | 512-node layout, fall-down, per-side LoD aggregation |
| `world/Entity_Spec`         | camera/voxel entities, sum-type matching |
| `world/World_Spec`          | add/remove/move keeping tree + lookup in sync |
| `world/gen/PerlinNoise_Spec`| determinism, range, lattice zeros |
| `world/gen/WorldGenerator_Spec` | material classification, adaptive subdivision, reproducibility |
| `world/render/WorldRenderer_Spec` | LoD maths + an offscreen render smoke test |

Run them with:

```bash
./gradlew test --tests "engine.*"
```

---

## 10. Status & next steps

**Built:** math primitives; the full immutable world tree (sectors, nodes,
ether, entities, lights, traces); entity fall-down and LoD aggregation; the
`Entity` sum type and `World` value; procedural generation; first-draft
Graphics2D rendering with distance LoD; a runnable demo.

**Not yet built (future steps):**

- The **update loop** that advances `World` tick to tick (entity behaviour,
  light-trace propagation/radiation).
- **Generation around camera entities** within a radius (streaming the world as
  the camera moves), rather than a single fixed region.
- Recursive sub-entities inside `VoxelEntity`.
- A real renderer (the `Graphics2D` path is a first draft) and dynamic 64→32-bit
  scaling for GPU rendering.
- Integration of a world view into the main Tribalism application.