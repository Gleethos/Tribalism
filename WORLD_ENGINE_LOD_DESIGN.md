# World Engine — Level-of-Detail & Far-Field Design

*Scope: making the `app.engine.world` pipeline look and perform like a proper open-world
voxel engine — smooth detail falloff, a shaped (not slab-flat) far field, and efficiently
batched far draws. Investigated 2026-06-10. Companion to `WORLD_ENGINE_REVIEW.md`
(whose B3 top-edge culling is now resolved: the coverage grid was marking the FULL bounds
silhouette of solid occluders while the renderer draws the inset-SHRUNK box; fixed in
`World.RenderTraversal.collect` + regression test in `CollectSectorsForRendering_Spec`).*

---

## 1. Symptoms

1. **LoD transitions are extreme**: 1u voxel cubes jump straight to 8u cubes, then to one
   flat box per sector — each step is a ×8 change in cell size, clearly visible as popping
   and as a sudden "wall of blockiness" a short distance from the camera.
2. **The far field lacks shape**: distant terrain is a field of single recessed slabs
   (one box per coarse leaf), with one averaged material each — no ridges, no material
   variation, no silhouette.
3. **Far terrain is many small draw units**, not large greedy meshes — draw-call overhead
   and no batching at the horizon.
4. Horizon pops out hard (far plane 2000 < `VIEW_DISTANCE` 2048; no fog).

## 2. Findings

### 2.1 The ×8 ladder is the mesher's choice, not the data structure's

`World.meshResolutionFor` rounds `detailCells` to the nearest power of
`WorldTreeNode.RESOLUTION` (8), so the only resolutions that exist are **1, 8, 64**.
With `LOD_DETAIL = 40` (`detailCells = 20·edge/distance`) a 64u chunk renders:

| Band | Mesh res | Cell size | Distance |
|------|----------|-----------|----------|
| near | 64 | 1u | < 57u |
| mid  | 8  | 8u | 57–452u |
| far  | 1  | one box | > 452u |

The traversal hands a sector over whole once it spans ≤ `LOD_COLLAPSE_CELLS = 8` cells
(distance ≥ 2.5×edge), so the *unit size* already grows ∝ distance and the ideal cell
size under the existing metric is a smooth `distance/20`. The power-of-8 snap quantizes
that ideal into jumps between `distance/57` and `distance/7` — the visible ×8 pop.

Key fact: `SectorMeshCache` rasterizes a render unit into a uniform ether grid and
greedy-meshes it. Nothing requires the grid edge to be a power of 8 — **intermediate
power-of-2 resolutions (2, 4, 16, 32) are buildable by rasterizing at the next power-of-8
the tree supports and downsampling the grid 2× per step** (merge 2×2×2 cells; majority
solid wins; `TextureProfile.average` / `MaterialId.merge` already exist). The tree keeps
its 8³ branching; only the renderer gains LoD levels.

### 2.2 The far field is structurally capped at "one box"

Three reinforcing causes:

1. `World.refine` collapses sectors below `REFINE_CELLS/COLLAPSE_HYSTERESIS ≈ 1.9` cells
   to **childless** coarse leaves; a childless leaf has nothing to rasterize from, so
   res-1 is its ceiling.
2. All the generator gives such a leaf is `representativeEtherOf`: **one dominant visible
   material + six per-side insets** (`WorldGenerator.insetsBySide`). The whole terrain
   surface inside a 512u box is reduced to one recessed slab. Notably, `insetsBySide`
   *already samples an 8×8 footprint of `surfaceHeightAt`* and then throws away
   everything except 6 scalars.
3. `World.collect`'s `isSolidOpaque()` fast path collects every fully-opaque-faced sector
   as a **res-1 box** unconditionally. Childless surface leaves are exactly that (the
   dominant-visible-material fix makes their faces opacity-1), so the entire far field
   takes this path regardless of what the mesher could do.

### 2.3 Draw batching

`GlRenderer` retains one VAO+VBO and issues one `glDrawArrays` per (sector, LoD) unit.
Near units are large meshes (fine); far units are ~6-quad boxes (draw-call bound).
The far set changes only when `refine` changes the root — which the movement gate makes
rare — so it is naturally batchable.

### 2.4 Insets are currently ignored above res-1 (again)

The "insets at all mesh resolutions" mesher was reverted together with the occlusion-bug
fix attempts (`a014a71`); the working tree has the pre-inset greedy mesher, so the mid
band renders full cubes. That work is safe to re-land now: it likely *worsened* the old
occlusion bug (drawn geometry receded while the culler marked full bounds), and the
culler now marks the drawn silhouette.

### 2.5 Invariant learned from the occlusion bug (must be preserved by all LoD work)

For every collected sector: **marked occluder silhouette ⊆ actually drawn pixels ⊆
projected full-bounds rect (the occlusion-test rect).** "Solid opaque by face opacity"
does NOT imply "fills its bounds" — insets (and soon surface patches) recede the drawn
geometry. Whenever the drawn shape of a unit changes, the marking in
`World.RenderTraversal.collect` must change with it.

---

## 3. Design

### A. Power-of-2 mesh LoD ladder *(renderer only — fixes the ×8 pops)*

- `World.meshResolutionFor`: round to the nearest **power of 2** (1,2,4,…,64) in log2
  space; same clamps (`<=1 → 1`, `>= MAX_MESH_RESOLUTION → 64`).
- `SectorMeshCache`: accept power-of-2 caps. `build` rasterizes at the smallest
  power-of-8 ≥ the requested res that the sub-tree supports (unchanged code path), then
  **halves** the ether grid down to the target: per 2×2×2 block, ≥4 solid sub-cells →
  solid merged cell, else air. Merged ether: reuse the sub-ether when all present ones
  are equal (the overwhelmingly common case — uniform terrain), else
  `MaterialId.merge` + per-side `TextureProfile.average`.
- Resulting bands for a 64u chunk: res 64 → 28u, 32 → 57u, 16 → 113u, 8 → 226u,
  4 → 452u, 2 → 905u, 1 beyond — ×2 steps everywhere. (`LOD_DETAIL` stays the single
  quality knob if the absolute distances need taste-tuning.)
- Band (1.41, 2.83] cells wants res-2 but childless leaves clamp to res-1 at the cache —
  same picture as today, filled properly by **B**. Update the stale `REFINE_CELLS` doc
  ("exactly the res-1→res-8 boundary") accordingly.

### B. Baked `VolumePatch` on coarse leaves *(the strategic far-field fix)*

> Revised 2026-06-10 after review: the first draft baked an 8×8 *heightmap*, which
> hard-wires "distant content is terrain" into the engine. Big far objects may be
> anything (overhangs, arches, floating structures, future buildings), so the baked
> summary is **volumetric**: the engine stays content-agnostic and only the *generator*
> knows the world is currently a height field.

**Data.** A baked **8×8×8 grid of materials** per coarse leaf — exactly the per-cell
information `etherOf` already samples and throws away, materialized. One
`materialAt(cell centre)` sample per cell (cheap; a coarse LoD summary, tunable to
multi-sample later), `Material.AIR` = empty. Stored palette-compressed
(`byte[512]` indices + small `Material[]` palette ≈ 0.6 KB). Plus one derived scalar
for occlusion: `solidBaseFraction` — the fraction of the leaf's height up to which
*every* column is fully-opaque from the bottom (recovers terrain-slab occlusion;
naturally 0 for floating content).

**Placement.** `WorldGenerator.volumePatchOf(BoundsF64)`; attached by `World.coarseLeaf`
(and the grown root's leaves) to non-homogeneous leaves only. Carried as an optional
leaf-only field on `WorldSector`, **included in equals/hashCode** so the mesh cache keys
on it. Homogeneous leaves stay patch-free (solid interior or pure air — nothing to shape).

**Meshing.** No new mesh path: a childless patched leaf at res 2–8 synthesizes its ether
grid *from the patch* (one shared ether per distinct material) and flows through the
existing downsample + greedy pipeline from step A unchanged. res-1 keeps today's
`ether().shrink(bounds)` box. Axis-aligned closed cells mean **no cracks/T-junctions
between adjacent units of different LoD** — the voxel aesthetic sidesteps classic
terrain-LoD stitching entirely.

**Traversal.** The `isSolidOpaque` fast path must stop forcing res-1: a patched leaf is
collected at `meshResolutionFor(cells)` (capped at the patch's 8). Occlusion marking
shrinks to what is certainly drawn: the slab `[bounds.min → min.y + solidBaseFraction ×
height]`, skipped when the base is empty (floating content occludes nothing), per
invariant 2.5. The 2×2×2 majority-downsampling preserves the slab (a merged bottom cell
always inherits ≥ half solid inputs), so the marking stays conservative at every res.

**Refinement interplay (follow-up tuning, not part of the first landing).** Once
childless leaves can mesh up to res-8 from their patch, children are only *needed* above
res-8, so `REFINE_CELLS` could rise from ~2.8 toward ~11 — a *shallower* tree cone for a
*better*-looking far field (a patch is ~1 KB vs 512 child sectors). Do this as a separate
measured step.

**Out of scope for the first landing:** patch invalidation under player edits (the far
cone is regenerated deterministically; edits matter only once edited terrain can be far
away *and* persisted — revisit with persistence).

### C. Far-field draw batching *(GL backend)*

Concatenate all collected units meshed at res ≤ 8 into **one shared far-field VBO**, with
one draw call; keep per-chunk retained VBOs for near units. Rebuild the far VBO only when
the root identity changes (refine settled a new tree) — the movement gate already makes
that rare. Measure draw counts before/after; B may shrink unit counts enough that this
becomes optional.

### D. Polish

- Derive the demo camera's far plane from `VIEW_DISTANCE` (single source of truth) and
  add distance fog → sky colour in the GL fragment shader so the last band fades, not
  pops.
- ~~Re-land the per-cell-inset greedy mesher (insets at all resolutions) on top of **A**.~~
  **Done 2026-06-10** (pulled forward after review: only res-1 boxes shrank, so LoD shapes
  were inconsistently blocky). The reverted `07f9c87` mesher is re-landed (coverage-based
  cull + step walls + flush-only merging; res-1 special case subsumed — one cell at its
  inset planes IS the shrunk box), and `VolumePatch` gained per-column **top insets**
  (byte-quantized, baked from the surface field where it crosses the column's top cell)
  so far terraces also recede to the continuous surface instead of quantizing to cells.
  `solidBaseFraction` subtracts the recessed top of the shortest column, keeping the
  occlusion slab ⊆ drawn geometry (invariant 2.5).

---

## 4. Invariants checklist (verify after every step)

1. Occlusion: marked ⊆ drawn ⊆ tested rect (see 2.5); `CollectSectorsForRendering_Spec`
   "phantom sky-band culling" guards the res-1 case — extend it when patches land.
2. `refine` and the renderer share `detailCells`; every res the renderer requests above
   what a childless leaf can serve must clamp gracefully in the mesh cache (it does:
   `min(cap, gridResolution)`).
3. Generator faithfulness: `etherOf(b) == generate(b,1).ether()`; a patch must be a pure
   function of bounds (determinism) so collapse/re-refine is lossless.
4. Value semantics: anything that changes what a sector draws (ether, patch) must be in
   its equals/hashCode — the mesh cache and GL chunk cache key on it.
5. Resolution independence: no pixels/viewport numbers in any LoD decision.

## 5. Execution order

| Step | Change | Size | Files |
|------|--------|------|-------|
| 1 | **A**: pow-2 `meshResolutionFor` + grid downsampling in the mesher | S | `World`, `SectorMeshCache` + specs |
| 2 | **B**: `VolumePatch` end-to-end (generator → leaf → mesher → traversal/occlusion) | M/L | `WorldGenerator`, `WorldSector`, `World`, `SectorMeshCache` + specs |
| 3 | **D**: far plane from `VIEW_DISTANCE` + fog; re-land insets-at-all-res | S | `WorldEngineDemo`, `GlRenderer`, `SectorMeshCache` |
| 4 | **C**: far-field VBO batching (measure first) | M | `GlRenderer` |
| 5 | Tuning: raise `REFINE_CELLS` once B is visually confirmed | S | `World` |

Each step lands green (`./gradlew test`) and gets a visual check
(`./gradlew runWorldDemo -Dengine.renderer=gl`).

## 6. Deferred / explicitly out of scope

- Geomorphing / cross-fade between LoD levels (×2 steps should be acceptable first).
- Cross-sector greedy meshing (stitching neighbour grids).
- Patch invalidation under edits; disk persistence of edited far terrain.
- Removing the inset system in favour of patches (likely end state, but only after B
  proves itself).