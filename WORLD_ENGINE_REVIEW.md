# World Engine — Code Review & Improvement Report

*Scope: the `app.engine.world` pipeline — generator → recursive `WorldSector` tree → LoD/culling traversal → CPU (`Graphics2D`) and GPU (`GlRenderer`) backends. Written 2026‑06‑08.*

---

## 0. TL;DR

Four of your reported symptoms map to **three concrete, verifiable bugs and one architectural limitation**. None of them is the generator's fault — the generator and the core tree are sound. The trouble is concentrated in the **LoD / culling / meshing layer**, which is also where most of the accidental complexity lives.

| # | What you see | Root cause | Confidence | Fix size |
|---|--------------|-----------|-----------|----------|
| B1 | Meshes are two‑sided; you see their inside | GL backface culling is **never enabled**, and it *can't* be until the cube winding is fixed — 3 of the 6 faces wind **inward** | **Certain** (proven) | small |
| B2 | Fly close to terrain → the whole 64³ chunk vanishes / collapses | `meshResolutionFor(+∞) == 1` integer overflow: camera *inside* a chunk ⇒ `detailCells = +∞` ⇒ chunk meshed at the **coarsest** LoD instead of the finest | **Certain** (proven) | 1 line |
| B3 | Far LoD boxes get culled at the top edge of the screen | Far clip plane (2000) **< view/refine distance (2048)**: a ring of coarse terrain at the horizon is frustum‑clipped. Software occlusion grid is a secondary suspect | **High** (needs a visual confirm) | small |
| B4 | The far field is *many* big blocks, culled individually, never one big greedy mesh | By design each coarse leaf is its **own** render unit; the greedy mesher never spans sectors, and `refine` collapses distant sectors to childless leaves the mesher can't merge | **Certain** (by design) | architectural |

**"Less is more" verdict:** yes. The single biggest source of complexity‑without‑payoff is the **software occlusion culler** (`CoverageGrid` + the near→far sort + the convex‑hull machinery). On the GPU path it is redundant with the depth buffer, it is only *approximately* near→far (so it under‑culls anyway), and it is the most likely culprit for spurious edge‑culling. Recommend deleting it from the GL path. The **inset/shrink** system is the second (it has been rewritten ~6 times per the design log) and is a good candidate for replacement by a small baked per‑node LoD grid.

---

## 1. How the engine fits together (shared mental model)

```
WorldGenerator ──etherOf/representativeEtherOf/generate──► WorldSector tree (immutable, 8³ branching)
        │                                                          │
        │ height field (Perlin fBm) + per‑side insets              │ World.refineAroundCameras():
        │                                                          │   grow root, subdivide toward cameras,
        ▼                                                          ▼   collapse far sectors  (budget‑gated)
   materialAt(p) ─► dominant material per cell            World.collectSectorsForRendering(screen, 64, sink):
                                                            frustum cull → occlusion cull → LoD pick → emit unit
                                                                           │
                                            ┌──────────────────────────────┴───────────────────────┐
                                            ▼                                                        ▼
                              Graphics2DRenderer (CPU)                                   GlRenderer (GPU)
                              SectorGeometry.emitChunk → SectorMeshCache.meshOf(res)  ── same seam ──┘
                              project + painter's sort + fillPolygon              retained VBO per (sector,res) + depth buffer
```

Key invariant the whole thing rests on: **the world and the renderer share one LoD metric** — `World.detailCells(bounds, eye) = LOD_DETAIL * (maxEdge/2) / distance` (`World.java:514`). `refine` uses it to decide what to materialize; `collect` uses it to decide what to draw. When they agree, the renderer never tries to descend into a sector that `refine` didn't build. That part is well‑designed.

The data model is clean and worth keeping:
- `WorldSector` (immutable, lazy `isSolidOpaque`/`isVoid`, memoized hash) + `WorldTreeNode` (bare `WorldSector[512]`).
- `WorldSectorEtherData` = one `MaterialId` + six `TextureProfile` faces (appearance qualities + an inset).
- `WorldGenerator` = Perlin fBm height field, adaptive subdivision (homogeneous regions collapse to a leaf). **No bugs found here.**

---

## 2. The four reported symptoms

### B1 — Two‑sided meshes / no backface culling  *(Certain)*

**Two independent problems, and they compound.**

1. **GL never enables culling.** `GlRenderer.initGL` (`GlRenderer.java:259‑286`) enables `GL_DEPTH_TEST` but there is no `glEnable(GL_CULL_FACE)` anywhere in the codebase (verified). So every quad renders from both sides. Flying up through a terrace, you see the *underside* of the grass surface; inside any shell you see its back faces.

2. **The cube winding is inconsistent, so culling can't simply be switched on.** `Cubes.FACE_CORNERS` (`Cubes.java:21‑28`) claims "a consistent outward‑facing winding (matching `Side.normal()`)", and `Quad`'s doc repeats it (`Quad.java:11`). It is **false**. Computing the geometric winding normal `(v1‑v0)×(v2‑v0)` for each face vs. its stored `Side.normal()`:

   ```
   NEG_Y  geom(0,-1,0)  stored(0,-1,0)  → OK
   POS_Y  geom(0,-1,0)  stored(0,+1,0)  → INVERTED
   NEG_X  geom(+1,0,0)  stored(-1,0,0)  → INVERTED
   POS_X  geom(+1,0,0)  stored(+1,0,0)  → OK
   NEG_Z  geom(0,0,+1)  stored(0,0,-1)  → INVERTED
   POS_Z  geom(0,0,+1)  stored(0,0,+1)  → OK
   ```

   The NEG/POS pairs were generated by just toggling the axis bit (e.g. `POS_Y = NEG_Y + 2`), which keeps the *same* traversal order and therefore flips inside/outside for one of each pair. Result: **POS_Y, NEG_X, NEG_Z wind inward.** If you naively `glEnable(GL_CULL_FACE)` today, those three faces would be culled when seen from *outside* — you'd see *through* the top, −X and −Z of everything. That is almost certainly why culling was left off.

   The CPU renderer dodges this because it culls by **normal dot‑product** (`WorldRenderer.emitQuad:87`) and `fillPolygon` is winding‑agnostic — so fixing the winding is safe for it.

**Fix:** reverse the corner order of the three inverted faces in `FACE_CORNERS` so all six produce an outward geometric normal, then enable hardware culling.

```java
// Cubes.java — make all six outward (reverse POS_Y, NEG_X, NEG_Z)
{ 0, 1, 5, 4 }, // NEG_Y   (unchanged, already outward)
{ 6, 7, 3, 2 }, // POS_Y   was { 2, 3, 7, 6 }
{ 4, 6, 2, 0 }, // NEG_X   was { 0, 2, 6, 4 }
{ 1, 3, 7, 5 }, // POS_X   (unchanged)
{ 2, 3, 1, 0 }, // NEG_Z   was { 0, 1, 3, 2 }
{ 4, 5, 7, 6 }  // POS_Z   (unchanged)
```
```java
// GlRenderer.initGL(), after the depth setup
GL11.glEnable(GL11.GL_CULL_FACE);
GL11.glCullFace(GL11.GL_BACK);
GL11.glFrontFace(GL11.GL_CCW);
```
Verify visually; if it culls the wrong side, flip `glFrontFace` to `GL_CW` (cheap A/B). Halves submitted triangles as a bonus.

---

### B2 — Whole chunk culled when the camera gets close  *(Certain — proven)*

This is an **integer‑overflow bug in `meshResolutionFor`** (`World.java:781‑789`).

When the camera is *inside* a sector's bounds, `distanceToBounds` returns 0 and `detailCells` returns `+Infinity` (`World.java:514‑519`). At the chunk floor the traversal then calls `meshResolutionFor(+Infinity)` (`World.java:848‑851`):

```java
int exponent = (int) Math.round(Math.log(desiredCells) / Math.log(8)); // Math.round(+∞)=Long.MAX_VALUE
                                                                        // (int)Long.MAX_VALUE = -1  ← truncation!
for (int i = 0; i < exponent; i++) res *= 8;   // exponent == -1 → loop never runs
return Math.min(res, MAX_MESH_RESOLUTION);      // returns 1
```

Verified empirically:
```
meshResolutionFor(+Inf)  = 1     ← camera INSIDE the chunk
meshResolutionFor(2560)  = 64    ← camera 0.5u away
(int)Math.round(+Inf)    = -1
```

So the **instant** the camera crosses into a chunk's AABB, that chunk flips from res‑64 (full voxel detail) to **res‑1 (a single coarse box)** — `ether.shrink(bounds)`, one flat slab at the recessed surface height. The detailed terrain you were just looking at disappears and is replaced by a slab that (from inside, with no backface culling — see B1) reads as "the chunk got culled." It is the *opposite* of what LoD should do up close.

**Fix (1 line of intent):**
```java
private static int meshResolutionFor( double desiredCells ) {
    if ( desiredCells <= 1 ) return 1;
    if ( desiredCells >= MAX_MESH_RESOLUTION ) return MAX_MESH_RESOLUTION; // huge/+∞ (camera inside) → finest
    int exponent = (int) Math.round(Math.log(desiredCells) / Math.log(WorldTreeNode.RESOLUTION));
    int res = 1;
    for ( int i = 0; i < exponent; i++ ) res *= WorldTreeNode.RESOLUTION;
    return Math.min(res, MAX_MESH_RESOLUTION);
}
```
This is independently a good guard (the unbounded `for` loop on a large finite `exponent` is also wasteful). Add a unit test: `meshResolutionFor(Double.POSITIVE_INFINITY) == MAX_MESH_RESOLUTION`.

---

### B3 — Far LoD boxes culled at the top edge  *(High confidence; please confirm visually)*

I could not reproduce this purely by static analysis, but the configuration makes one cause very likely and gives a second to rule out.

**Leading cause — far clip plane is closer than the world is built/refined.** In the demo the camera is `near=0.5, far=2000` (`WorldEngineDemo.java:86`), but `World.VIEW_DISTANCE = 2048` (`World.java:315`) — the root is grown, and surface sectors are refined, out to **2048**. So there is a 2000–2048 ring of large coarse‑leaf boxes (512u+ at that distance) that the **far plane frustum‑clips** even though they're "on screen." In orbit/level flight the horizon sits in the upper part of the view, so this clipping reads as **"far boxes popping out at the top edge."** A hard circular far‑clip with no fog looks like culling regardless.

*Fix:* make the far plane ≥ the view distance (e.g. `far = 2200`, or derive both from one constant), and ideally fade terrain toward the far plane (distance fog → sky colour) so the boundary isn't a hard pop. Note the two distances are currently declared in different files with no link between them — a refactor target.

**Secondary suspect — the software occlusion grid.** `CoverageGrid.isOccluded` treats fully‑off‑screen rectangles as occluded and culls a sector whose whole screen‑space AABB falls in already‑covered tiles (`World.java:824‑829`, `CoverageGrid.java:49‑66`). For convex cube occluders the marking is geometrically exact, so I don't think it's *wrongly* culling visible sky‑edge geometry — but it's the cheapest thing to falsify: temporarily make `collectSectorsForRendering` skip the occlusion test and see if the top‑edge culling disappears. If it does, that's your answer; if not, it's the far plane. (Either way, see §4 — I recommend removing it from the GL path.)

---

### B4 — Distant terrain is many big blocks, not one greedy mesh  *(Certain — by design)*

This is not a bug so much as a **design limitation with two reinforcing parts**:

1. **The greedy mesher never spans sectors.** `SectorMeshCache` meshes *one render unit's* sub‑tree into a single grid and greedy‑merges within it (`SectorMeshCache.java:152`). Two adjacent sectors are always two separate meshes / draws / cull units. So the far field — a shell of coarse‑leaf boxes — is inherently "many blocks, culled individually."

2. **`refine` collapses distant sectors to *childless* leaves, which the mesher then can't merge.** A super‑sector is only meshed as one res‑8 greedy unit *if it still has children*. But `refine` collapses any sector below `REFINE_CELLS/COLLAPSE_HYSTERESIS ≈ 1.9` cells to a childless coarse leaf (`World.java:466,501`), and a childless leaf can only be drawn as a single res‑1 box. So exactly the far shells that you'd want batched into one big mesh have had their children thrown away.

There's also visible **factor‑8 LoD popping**: `meshResolutionFor` only ever returns 1, 8, or 64 (`World.java:781`), so detail jumps in 8× steps with no blend.

**Options (smallest → biggest):**
- *Cheap:* widen the band where a branch keeps children so the renderer can mesh more super‑sectors as one greedy unit — i.e. align the collapse threshold with the renderer's `LOD_COLLAPSE_CELLS=8` instead of `REFINE_CELLS≈2.83`. More memory, fewer draws, bigger merged meshes.
- *Right answer (already noted as deferred in your design log):* give each node a **small baked LoD grid** (e.g. an 8³ or a 2D heightfield summary) so a coarse node can mesh its *surface shape* as one mesh without keeping 512 child sectors. This replaces both the inset hack and the "many boxes" problem.
- *Heaviest:* cross‑sector greedy meshing (stitch neighbour grids). High complexity; I would not start here.

---

## 3. Other bugs & risks found

- **`detailCells` near‑field blow‑up is load‑bearing in two places.** The same `+∞`‑when‑inside value also feeds `refine` (fine there — it just forces descent) and the child‑sort distances. Worth a single clearly‑documented helper rather than `distance <= 0 ? +∞` scattered around.
- **Occlusion ordering is only approximate.** `collect` recurses **depth‑first nearest‑child‑first** (`World.java:858‑863`), which is *not* globally near→far: the entire subtree of the nearest child (including its far parts) is marked before the second child is touched. This makes the occluder under‑effective (misses culls), not wrong — but it means the grid pays its full cost for partial benefit. Another argument for removing it on GL.
- **`isMajorityOpaque` is a misnomer and its doc is stale.** It's `combined().isOpaque()` = average OPACITY ≥ `OPACITY_THRESHOLD` (`WorldSectorEtherData.java:175`, `TextureProfile.java:43`), where the threshold is **0.25**, not a "majority." The design log and some comments say 0.5. Rename to something like `readsAsSolidSurface()` and fix the threshold references.
- **`VIEW_DISTANCE` (2048) and the demo's far plane (2000) and `generationDistance` (160) are three independent magic numbers** with real ordering constraints between them (`generationDistance ≤ view ≤ far`) and no single source of truth. B3 is the visible consequence.
- **Dead pixel‑LoD code.** `World.projectedEdgePixels` (`World.java:769`) is referenced **only by tests** now; `World.focalLengthPx` and `ViewInfo.focalLengthPx` are computed and stored but never read by any renderer (projection uses the matrix directly). Leftovers from the pre‑"resolution‑independent LoD" era — safe to delete (and drop the tests that pin them).

---

## 4. "Less is more" — what to simplify or delete

Ranked by payoff:

1. **Delete the software occlusion culler from the GL path.** `CoverageGrid` (142 lines) + Andrew's‑monotone‑chain convex hull + point‑in‑polygon + per‑frame coverage buffer + the near→far child sort in both `refine` and `collect`. On GPU it duplicates the depth buffer, it's only approximately ordered (so it under‑culls), and it's the prime suspect for edge‑culling artifacts. Keep frustum culling (cheap, correct, essential). If you want it for the CPU fallback, gate it behind a flag there only. **This removes the most code for the least risk.**

2. **Replace the inset/shrink machinery with a baked per‑node LoD summary** (see B4). Per your own design log this is the most‑rewritten subsystem in the engine (≈6 iterations). It exists solely so a childless coarse box isn't a full cube. A tiny baked heightfield/occupancy per node would subsume it *and* fix B4, *and* let you drop `WorldSectorEtherData.shrink`, `insetsBySide`, `emptyFraction`, the per‑face inset on `TextureProfile`, and the res‑1 special case in the mesher. Big net simplification — but it's a real piece of work; do it deliberately, not now.

3. **De‑duplicate the small axis/geometry helpers.** `otherAxis` is copy‑pasted in `SectorMeshCache` and `GlRenderer`; `dominantAxis`, `component`, the `coord` mapping, `clampCell` (in both `World` and `WorldSector`), and `cellContaining`/`cellOf` are near‑duplicates. Pull a tiny `Axis`/`GridMath` helper. Low risk, improves readability.

4. **Collapse the LoD constants into one documented place.** `LOD_DETAIL`, `REFINE_CELLS`, `COLLAPSE_HYSTERESIS`, `LOD_COLLAPSE_CELLS`, `MAX_MESH_RESOLUTION`, `VIEW_DISTANCE`, near/far — with the ordering invariants stated as asserts. Right now the relationships live only in prose comments and the design log.

5. **Reconsider keeping two renderers long‑term.** The CPU `Graphics2DRenderer`/`WorldRenderer` is a useful reference/fallback today, but it carries its own painter's sort + occlusion path. Once GL is trustworthy, demoting it to "debug only" (or deleting it) removes a parallel code path you currently keep in sync.

---

## 5. Documentation drift to fix

- `Cubes.java:20` and `Quad.java:11`: "winding consistent with normal" — **false** until B1 is fixed; update the comment when you fix the array.
- `WorldRenderer` class doc (`WorldRenderer.java:13‑30`) and `Graphics2DRenderer.java:19` describe back‑face culling as a property of "the renderer," but only the CPU path culls (by normal); the GL path doesn't cull at all. Clarify per‑backend.
- "majority opaque ≥ 0.5" in comments vs. `OPACITY_THRESHOLD = 0.25` in code (see §3).
- `World.collectSectorsForRendering` doc still describes the LoD in pixel terms in places; the metric is now resolution‑independent (`detailCells`). Mostly updated, but `projectedEdgePixels`'s doc ("the pure heart of the level‑of‑detail decision", `World.java:766`) is no longer true — it isn't used by the decision anymore.

---

## 6. Suggested order of work

| Step | Change | Risk | Payoff |
|------|--------|------|--------|
| 1 | **B2**: clamp `meshResolutionFor` for `+∞`/huge | trivial | close terrain stops vanishing — biggest visible win per line |
| 2 | **B1**: fix `FACE_CORNERS` winding + enable `GL_CULL_FACE` | low | no more see‑through/inside‑out meshes; ~½ the triangles |
| 3 | **B3**: far plane ≥ view distance (+ optional fog); unify the distance constants | low | horizon stops popping |
| 4 | **§4.1**: delete `CoverageGrid` from the GL path | low | large code reduction; rules out occlusion artifacts |
| 5 | Docs/§5 + dead‑code (`projectedEdgePixels`/`focalLengthPx`) + helper de‑dup | low | clarity |
| 6 | **B4 / §4.2**: baked per‑node LoD grid (replaces insets, fixes "many boxes") | high | the real far‑field fix + the engine's biggest simplification |

Steps 1–4 are small, high‑confidence, and address every reported symptom; I'd do them first, run the engine test suite, and confirm visually with `./gradlew runWorldDemo -Dengine.renderer=gl`. Step 6 is the strategic one and deserves its own design pass.

---

*Nothing in the generator or the core `WorldSector`/`WorldTreeNode`/`BoundsF64` math needs changing — those are in good shape. The whole report is about the ~6 files of the LoD/culling/meshing layer.*