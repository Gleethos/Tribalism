# API Stability — what's stable, what stays flexible

> Companion to [`VISION.md`](VISION.md). It says which parts of the codebase are **stable
> contracts** (pin them with exhaustive tests; change them deliberately and with a migration) and
> which are **flexible** (expected to churn; test their *behavior*, not their exact shape). The
> goal is to spend test effort where it pays off and to avoid freezing things that should still
> move.

A simple rule of thumb: **immutable value types and pure functions over them are stable; anything
that touches the UI, the wire protocol, or the still-forming domain graph is flexible.**

---

## Stable — pin with edge-case tests, evolve via additive changes only

| Area | Package(s) | The contract | Test coverage |
|---|---|---|---|
| **Dice** | `app.dice.*` | `DiceNotation` grammar + canonical round-trip; `DiceRoll`/`TermRoll` shape; roller semantics — **kept dice returned in roll order**, totals, **crit detected only on a *kept* die**; `Checks` = `1d20 + sheet modifier`. | `Dice_Spec`, `Dice_Hardening_Spec` (exact-value, scripted RNG) |
| **Messaging** | `app.messaging.*` | `Message`/`Sender`/`Audience` shape; `MessageLog.visibleTo` visibility matrix (everyone/party/GM-only/whisper; GM omniscient; sender sees own). | `Messaging_Spec`, `Messaging_Hardening_Spec` (full matrix) |
| **Session core** | `app.session.Session` | The *operations* and their semantics: `post/announce/whisper/system/roll/advanceTurn/messagesFor`, and "`roll` records **and** announces". The record's field *list* will grow (see flexible). | `Session_Spec` |
| **Character sheet** | `app.models.sheet.*` | The value tree shape and wither/upsert semantics (`withAbilityLevel` updates in place, no reorder/dup); record validation; value equality; inventory-item identity via `uid`/`HasId`. | `CharacterSheet_Spec`, `CharacterSheet_Hardening_Spec`, `CharacterSheetViewModel_Spec` |
| **Map seam** | `app.maps.MapWorlds` | `generatorOf`/`worldOf` signatures and the **determinism** contract (same seed ⇒ identical terrain). | `MapWorlds_Spec` |
| **Topsoil ORM** | `dal.api.*` (+ `dal.impl`) | `Model`/`Value` persistence: value dedup, reference counting, recursive cascade cleanup, query API. Mature. | `dal.*` specs |
| **World engine** | `app.engine.*` | The immutable world model + render SPI; documented in `ARCHITECTURE.md`. Extend per `WORLD_ENGINE_LOD_DESIGN.md`, honoring its invariants (`marked ⊆ drawn ⊆ tested rect`). | `engine.*` specs |

**Evolving a stable API:** prefer **additive** changes — new `DiceNotation` syntax, new `Audience`
variant, a new sheet field with a wither, a new `Session` operation. If a value record *must* grow
a field, add it with a sensible default and a new `with…`; update the constructor call sites. Never
silently change an existing function's result for the same input — that breaks the pinned tests on
purpose, which is your signal to write a migration and bump expectations consciously.

---

## Flexible — test behavior through the seams, don't freeze the shape

| Area | Package(s) | Why it will change | How to test it |
|---|---|---|---|
| **View models & views** | `app.user.*`, `app.campaign.*`, `app.session.SessionView*`, `app.maps.MapView` | UI is iterated constantly; layouts, widgets, and VM method sets shift. | Test VM *logic* headlessly (lens edits round-trip, service actions mutate state); confirm *rendering* with screenshots, not asserts. |
| **Domain graph** | `app.models.*` (`Campaign`, `GameMap`, `Player`, `GameMaster`, `Character` relations) | Still reshaping: per-campaign rule registries, `Session`/`VisionState` persistence, factions/quests/locations to come. | Test via services (`CampaignService`) and round-trips, not the exact interface field set. |
| **Session shape** | `app.session.Session` *fields* | Will gain fog state, per-participant permissions, initiative/combat structure. | Test operations and outcomes; avoid asserting the full record component list. |
| **MVVM bridge protocol** | `net.*`, `web-portal/src/mvvm` | Will gain collections, lens parity, per-participant scope filtering, auth, binary/stream channels; protocol constants may change. | Test the Java serialization/dispatch units; the React half needs a browser. |
| **Rules registries** | `app.models.ini.*`, `saves/*.json` | Moving from global JSON to per-campaign, editable rulesets. | Test loading/round-trip, not the on-disk format specifics. |
| **Agent harness** | *(unbuilt)* | Entirely open (providers, tools, sandbox, perception). | Build behind a provider/tool interface; test tool dispatch against fakes. |
| **Fog of war / VisionState** | *(unbuilt)* | Shape of explored-set + view-cone TBD; sits on the engine visibility walk. | Once built: test the visibility mask as a pure function of (world, viewpoint, scope). |

---

## Testing posture & known gotchas

- **Value types & pure functions** (the stable column) → exhaustive, exact-value unit tests. Use an
  injectable/deterministic source where randomness or time is involved (e.g. `DiceRoller.seeded`, a
  scripted `RandomGenerator`) so assertions are exact.
- **Services over the DB** → register the model closure and assert persistence round-trips. Topsoil
  gotchas are recorded in the `topsoil-orm-gotchas` project memory: pre-create the `.db` file in
  test setup; a `Value` record must not name a component `id` (reserved PK) — use `uid` + `HasId`.
- **GUI** → headless tests for the view-model logic; visual verification for rendering via
  `./gradlew runMain -PmainClass=<FQN>` then a screenshot (a display is available in dev).
- **Engine** → see `ARCHITECTURE.md`/`WORLD_ENGINE_LOD_DESIGN.md`; preserve the occlusion invariant.

When you add a feature, ask: *is this a value/pure-function (pin it) or a UI/protocol/domain-graph
piece (test the behavior, keep the shape free)?* — and put the test effort accordingly.
