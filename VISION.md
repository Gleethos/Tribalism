# Tribalism — Vision & Feature Spec

> **Status:** living document. It describes where Tribalism is going and how the
> pieces fit, at enough resolution that an agent (or a person) can pick a section
> and break it into concrete work. It is deliberately *design-level*; the deep
> internals of already-built subsystems live in their own docs and are referenced,
> not duplicated:
> - [`ARCHITECTURE.md`](ARCHITECTURE.md) — the world engine as built.
> - [`WORLD_ENGINE_LOD_DESIGN.md`](WORLD_ENGINE_LOD_DESIGN.md) — the engine's level-of-detail roadmap.
> - [`WORLD_ENGINE_REVIEW.md`](WORLD_ENGINE_REVIEW.md) — engine review notes.
> - [`agent-skills/SWING_TREE_SKILL.md`](agent-skills/SWING_TREE_SKILL.md) — how all GUI/binding code is written.

---

## 1. What Tribalism is

Tribalism is the ultimate helper tool for **Pen & Paper game masters**. One person
(the GM) runs it as a **desktop application**; their players join over the **local
network** through a **web portal** to see and operate their own character sheets.
Around that core sit three big capabilities:

1. **Maps** — dungeons, towns and landscapes built in a custom **3D voxel engine**,
   used both for prep (building) and at the table (play).
2. **Campaigns & character sheets** — the structured game data: worlds, characters,
   roles, abilities, skills, NPCs, and the rules that connect them.
3. **An agentic AI game master** — a sandboxed AI harness (in the spirit of Claude
   Code / OpenCode) purpose-built either to **be** a game master or to **co-pilot**
   a human one, with its own isolated Linux-like playground to work in.

It is a **hybrid desktop/web** app: the GM gets the full desktop experience
(SwingTree), players get a thin React web client, and **both bind to the same
Java-side view models** over a websocket bridge — so a feature is built once and
appears in both places.

It is built on **functional, data-oriented** principles: immutable values,
records and sum types, persistent collections, and lenses. Persistence and
**time-travel** (snapshotting state and rewinding) fall out of that same
immutability, and are unified with how the AI agent's workspace is version-tracked.

Because it runs **local models and user-configured AI providers** on a user's own
machine, **security is a first-class concern**: the AI must never be able to roam
the user's filesystem. It gets a sandbox instead.

---

## 2. Design principles (non-negotiable)

These hold across every module. New code that violates them is wrong.

1. **Immutable values with value semantics.** Data structures — however large or
   deep — are immutable and define `equals`/`hashCode` by content. "Change" returns
   a new value. Performance comes from **structural sharing**, **lazy memoization**,
   and **cheap value-keyed caches**, not from mutation. (The world engine is the
   reference implementation of this; see `ARCHITECTURE.md` §1.)
2. **Records + sum types.** Model data as `record`s and sealed interfaces
   (sum types implemented only by records), matched exhaustively. Encapsulate behind
   a `final class` only when a value must carry a private memoization cache (the
   engine's `CameraF64` / `WorldSector` pattern).
3. **Persistent collections from Sprouts.** Use `Tuple` (list), `Association` (map),
   `ValueSet` (set), `Pair`. Never reach for `java.util` mutable collections inside a
   value.
4. **Lenses over setters.** State lives in one immutable root; views `zoomTo` the
   field they edit; a write produces a new root all the way up. (See
   `SWING_TREE_SKILL.md` §4.) This is the bridge between the data model and the UI,
   **and** the substrate for snapshots/undo.
5. **The view layer is thin and dumb.** View models live on the Java side and hold
   no Swing/React types. Properties (`Var`/`Val`/`Vars`) bind automatically to **both**
   the SwingTree desktop UI and the React web client through the websocket bridge.
   A `JComponent` (or a `useState`) in a view model means the architecture is wrong.
6. **Rendering is a pure function of state.** What the user sees is derived from an
   immutable world/value; renderers own no simulation state and are swappable.
7. **The AI is sandboxed by construction.** The agent never gets ambient authority
   over the host. Its filesystem, its tools, and its network are an explicit,
   enclosed environment.

---

## 3. System architecture at a glance

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Tribalism (one desktop process)                   │
│                                                                            │
│  ┌───────────────┐   ┌──────────────────────────────────────────────┐     │
│  │ Desktop UI    │   │              Application core (Java)          │     │
│  │ (SwingTree)   │◄──┤  view models (Sprouts properties) ───────────┐│     │
│  └───────────────┘   │                                              ││     │
│                      │  ┌────────────┐ ┌────────────┐ ┌───────────┐ ││     │
│  ┌───────────────┐   │  │ Domain     │ │ World      │ │ Agent     │ ││     │
│  │ Web portal    │   │  │ model      │ │ engine     │ │ harness   │ ││     │
│  │ (React, LAN)  │◄──┤  │ (campaigns,│ │ (maps:     │ │ (sandbox, │ ││     │
│  │  thin client  │   │  │  sheets,   │ │  voxel sim │ │  tools,   │ ││     │
│  └──────┬────────┘   │  │  rules)    │ │ + render)  │ │  loop)    │ ││     │
│         │ websocket  │  └─────┬──────┘ └─────┬──────┘ └─────┬─────┘ ││     │
│         │  (net,     │        │              │              │       ││     │
│         └────MVVM────┘        ▼              ▼              ▼       ││     │
│            bridge    │  ┌──────────────────────────────────────┐   ││     │
│                      │  │ Persistence: Topsoil ORM (SQLite)     │   ││     │
│                      │  │  + value snapshots + git time-travel  │   ││     │
│                      │  └──────────────────────────────────────┘   ││     │
│                      └──────────────────────────────────────────────┘│     │
│                                                                       │     │
│                       ┌───────────────────────────────────────────┐  │     │
│                       │ Sandbox (podman + git binaries, shipped)   │◄─┘     │
│                       │  the agent's private Linux-like workspace  │        │
│                       └───────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────────────────┘
```

### Module maturity (be honest about where we are)

| Module | Package(s) | State | Where it's documented |
|---|---|---|---|
| **World engine** | `app.engine.*` | **Mature**, standalone. Not yet in the app UI. | `ARCHITECTURE.md` |
| **Topsoil ORM** | `dal.*` | **Mature** for both `Model` and `Value` trees (the `CharacterSheet` value tree round-trips). | javadoc in `dal.api`; gotchas in `[[topsoil-orm-gotchas]]` memory |
| **MVVM bridge** | `net.*` | **Working** for primitives/enums/nested VMs; needs lists, binary, lifecycle hardening. | this doc §9 |
| **Desktop shell + auth** | `app`, `app.user`, `app.dev` | **Early prototype**: login/register, user page, dev inspectors. | — |
| **Web portal** | `src/main/web-portal` | **Early prototype**: mirror MVVM, login/register/user views. | — |
| **Domain model** | `app.models.*` | **Reshaping**: `Campaign` + `GameMap` landed (§4); `CharacterSheet` value tree (§5.3) with a lens-driven desktop view; a `CampaignService` + `CampaignView` roster (create campaign/characters, edit persisted sheets). | this doc §5 |
| **Map↔engine seam** | `app.maps.*` | **Rendering**: `MapWorlds` builds an engine `World` from a `GameMap`; `MapView` embeds the engine renderer (free-fly Build mode, software backend) — a `GameMap` renders as 3D terrain in-app. No editing tools/GL/persistence yet. | this doc §6 |
| **Agent harness** | *(none yet)* | **Vision only — no code.** | this doc §7 |
| **Snapshots / time-travel** | *(none yet)* | **Vision only.** | this doc §8 |

---

## 4. The naming collision we must resolve first

There are **two** unrelated things both called `World` today:

- `app.engine.world.World` — the **engine** value: the infinite voxel simulation that
  backs a *map*. Keep this name; it is correct in its own domain.
- `app.models.World` — a **domain/DB** record meaning roughly "a campaign setting that
  owns characters and NPCs". This name is misleading and clashes.

**Decision for this spec:** the domain concept is renamed **`Campaign`** (the
narrative + roster container a GM runs), and the spatial scenes it owns are
**`Map`s**, each backed by an engine `World`. Wherever this document says *Campaign*
or *Map*, the current `app.models.World` is the seed to evolve. (See §5.)

---

## 5. Domain model

The domain is the structured game data. Today's `app.models.*` interfaces are the
seed; this is the target shape. It is described as **immutable value trees** (records
+ Sprouts collections) hanging off **timestamped persistence roots** — see §8 for how
that maps onto Topsoil.

### 5.1 Entities and relationships

```
User ─┬─ (is) ── GameMaster ──< owns >── Campaign ──┬──< has >── Map        (engine World scene)
      │                                              ├──< has >── Character  (NPC or PC instance)
      └─ (is) ── Player ──────< joins >── Campaign   ├──< has >── Faction / Location / Quest (later)
                    │                                 └──< has >── Session   (a play meeting; log + state)
                    └──< controls >── Character (their PCs in that campaign)

Character ─┬── sheet: CharacterSheet (the rules-bearing value tree)
           ├── Role(s) ──< modifies >── Abilities / Skills
           ├── Abilities: Tuple<Ability(type, level)>
           ├── Skills:    Tuple<Skill(type, level, proficient, learnability)>
           └── placement: optional (Map, position) when on a battle map

Rules registries (campaign- or app-level, seeded from JSON today):
   AbilityType, SkillType, RoleType   →   see saves/*.json, app.models.ini.*
```

### 5.2 Entity notes

- **User** — credentials + identity. Can log in from desktop *or* web. One user may be
  a GM in one campaign and a player in another, so **GameMaster** and **Player** are
  *roles a User holds within a Campaign*, not separate accounts.
- **Campaign** (was `models.World`) — the GM's save: name, description, the roster of
  characters/NPCs, its maps, its sessions, and the rules registries in force. A
  campaign is the natural unit of **export/share** and of **snapshot** (§8).
- **Map** — a named spatial scene backed by an engine `World` (§6). A campaign has many
  (the tavern, the dungeon level, the overworld). A map references generation params +
  a persisted diff of edits (the engine already makes pristine terrain reproducible
  from a seed; only *edits* need storing — see `ARCHITECTURE.md` §10).
- **Character** — a concrete person/creature in a campaign (PC or NPC). Carries a
  **CharacterSheet** value. `CharacterModel` (today's "template") becomes a **sheet
  archetype** — a reusable starting point a player clones into a real Character.
- **Role / Ability / Skill** — the rules layer. A **Role** bundles ability/skill
  modifiers (today's `models.Role`). `AbilityType`/`SkillType`/`RoleType` are the
  registries (today loaded from `saves/*.json` via `app.models.ini.*`); long-term they
  become **per-campaign, editable** so a GM can author a ruleset.
- **Session** — one play meeting: which characters were present, the active map, an
  event/chat/dice log, and a pointer to the snapshot taken at the end (so a campaign is
  a sequence of resumable states).

### 5.3 The `CharacterSheet` is the first real `Value` tree

The sheet is the proving ground for the data-oriented persistence model (§8): a single
immutable record (identity, attributes, `Tuple<Ability>`, `Tuple<Skill>`, inventory,
notes) that the UI edits **entirely through lenses** (`zoomTo`). This is also exactly
the shape the React client and SwingTree both bind to, so getting the sheet right
exercises the whole stack end-to-end.

---

## 6. Maps & the world engine

The world engine (`app.engine`) already exists and is powerful (immutable voxel tree,
infinite LoD streaming, software + OpenGL backends — see `ARCHITECTURE.md`). The work
here is mostly **integration and authoring tools**, plus finishing the engine's own LoD
roadmap (`WORLD_ENGINE_LOD_DESIGN.md`).

### 6.1 Three interaction modes (a map view = one engine `Screen` + a mode)

The engine already models cameras and screens; a "mode" is a camera rig + tool set +
input mapping over the same `World`.

1. **God / Build mode** — free-fly camera, advanced editing: place/remove material,
   stamp prefabs, run generators, sculpt terrain. Used for prep.
2. **First-person / Survival mode** — walk the map, collide, destroy/place blocks. A
   "see it from inside" check and a play style in its own right.
3. **Table / Play mode** — top-down with a **view plane above the players' heads** so
   the camera is never occluded by roofs. This is the mode shown at the table and
   mirrored to players. Tokens (characters placed on the map) move here.

### 6.2 Integration work (the actual tasks)

- **Embed a renderer viewport in a SwingTree view.** The engine's `Renderer` SPI already
  yields a `java.awt.Component` (`viewportFor(screenId)`); wrap it with `UI.of(component)`
  inside a `MapView`. Drive `World.update` on the existing background thread.
- **A `MapEditorViewModel`** holding the current `World` (an `AtomicReference` swap target),
  the active mode, the selected tool/material, and the camera bindings.
- **Translate SwingTree input → `ScreenInputEvent`s** (the demo already does this raw;
  lift it into a reusable input adapter).
- **Persist maps**: seed + generation params + an edit diff (depends on §8 and the
  engine's planned per-sector edit persistence in `ARCHITECTURE.md` §10).
- **Stream the play-mode view to the web portal** (later, harder): the GM renders;
  players receive a frame stream or a thin remote view, and token interactions travel
  back over the websocket. Start desktop-only; treat web map rendering as a later epic.
- **Object/prefab generators**: a small starter library (chairs, tables, furniture,
  houses) as procedural `WorldSector` builders — the near-term content the original
  vision calls for, before full procedural settlements.

### 6.3 Engine-internal roadmap

Owned by `WORLD_ENGINE_LOD_DESIGN.md`. Current standing (per project memory): the
power-of-two mesh LoD ladder (step **A**) is **done**; `SurfacePatch`/`VolumePatch`
far-field work (step **B**) is **next**. The occlusion invariant *marked ⊆ drawn ⊆
tested rect* must hold after every change. Do not re-plan the engine here — extend that
doc.

---

## 7. The agent harness (the defining feature — currently unbuilt)

Tribalism's differentiator is an **agentic AI** that can run a game or assist a GM. It
is an agent loop in the spirit of Claude Code, but **purpose-built for tabletop** and
**sandboxed** so a locally-run model can never touch the host.

### 7.1 Two modes of operation

- **Autonomous GM** — the AI runs the session: narrates, controls NPCs, adjudicates
  rules, updates the map and sheets, responds to players.
- **GM Copilot** — a human runs the session; the AI suggests, drafts NPCs/encounters,
  answers rules questions, and performs bookkeeping the GM approves.

Both share the same harness; the difference is how much authority the loop is granted
and whether actions auto-apply or require GM confirmation.

### 7.2 The sandbox (security boundary)

> Principle 7: the agent gets a playground, never the host filesystem.

- **Ship a `podman` binary and a `git` binary** with the app. The agent's world is a
  hidden directory inside the app's install/data dir, owned by a podman container; the
  app wraps that directory in a **git repo** from the outside.
- **The harness loop lives in the Java application.** Tool calls from the model are
  **executed against the container**, never the host shell. Supported tool surface
  (incremental): `pwd`, `ls`, `cd`, `cat`, `mkdir`, file read/write **within the
  workspace**, `curl` (subject to a network policy), running bash scripts, and later
  Python — all *inside* the container.
- **Network is policy-gated.** Default-deny outbound except the configured AI provider
  endpoints and an explicit allowlist.
- **No host mounts** beyond a single, explicit workspace volume.

### 7.3 The harness loop

```
   ┌── system + campaign context (sheets, map summary, rules, recent log) ──┐
   │                                                                         │
   ▼                                                                         │
 build prompt ─► call provider (local model OR configured API) ─► parse ─────┤
   ▲                                                  │                       │
   │                                          tool call(s)?                   │
   │                                                  │ yes                   │
   │                                   execute in sandbox (podman)            │
   │                                                  │                       │
   │                                   tool results ──┘                       │
   │                                                                          │
   └──────────────── apply effects (with GM gate in copilot mode) ◄──────────┘
                              │
                              ▼
              domain mutations (sheets, NPCs, map) as immutable value updates
```

- **Provider abstraction.** One interface with implementations for (a) **local models**
  and (b) **user-configured custom providers** (Anthropic, OpenAI-compatible, etc.).
  Provider config is per-user, stored locally. When integrating with the Claude API
  specifically, consult `agent-skills` / the `claude-api` reference and default to the
  latest Claude models.
- **Tool calling** is structured (name + JSON args), dispatched to either **sandbox
  tools** (§7.2) or **domain tools** (typed operations on the campaign: "create NPC",
  "set HP", "place token", "reveal map region"). Domain tools are how the AI affects the
  game *without* shell access — they are the safe, high-level surface.
- **Context** is assembled from the domain model (current sheets, map summary, rules in
  force, recent session log), not free-form file scraping.

### 7.4 Snapshots tie into §8

Both the **agent's context** and its **workspace** are snapshot over time (git for the
workspace; value-root snapshots for context and domain state), so a GM can **check out a
previous point in time and continue from there**. This is the same time-travel mechanism
as domain persistence — see §8.

### 7.5 Suggested build order for the harness

1. **Provider interface + one configured remote provider**, with a plain chat view (no
   tools, no sandbox) — proves config, streaming, and the bridge.
2. **Domain tools** (typed campaign operations) with a **copilot GM gate** — the AI can
   propose sheet/NPC/map changes the GM approves. *No shell yet* — high value, low risk.
3. **The podman+git sandbox** and shell/file tools — the playground.
4. **Autonomous mode** + the network policy + local-model support.
5. **Context & workspace snapshotting** (folds into §8).

---

## 8. Persistence, snapshots & time-travel

### 8.1 Evolve `Model` into a timestamped root over `Value` trees

Today Topsoil persists `dal.api.Model` interfaces as eagerly-written, mutable-feeling
proxies (one row per field, relations as tables). The vision (and principle 1) wants the
opposite: **a `Model` becomes a thin, timestamped *root* that wraps a large immutable
`Value` data structure** (records with `Tuple`/`Association` inside), persisted as a
content value rather than a spray of mutable columns.

- Keep Topsoil's `Model` as the **identity + timestamp + pointer** (id, created/updated,
  current value-root reference).
- Store the heavy state as **`dal.api.Value`** trees (already supported as immutable
  DB values) — the `CharacterSheet` (§5.3) is the first one to migrate.
- Because the value is immutable and structurally shared, **writing a new version is
  cheap and the old version is still addressable** — which is what makes snapshots and
  undo nearly free.

### 8.2 Snapshots = addressable past value-roots; git for the agent workspace

- **Domain snapshots**: a snapshot is just the set of value-root pointers at a moment
  (per campaign). "Rewind" repoints the campaign to an earlier root. Sessions (§5.2) take
  one at their end so a campaign is a resumable timeline.
- **Agent workspace snapshots**: the container's working directory is a git repo; a
  snapshot is a commit. Checking out the past restores the agent's files.
- The two are taken **together** when the GM "saves progress", giving a single coherent
  point-in-time for both the game and the AI's scratch space.

### 8.3 Tasks

- Define the `Value`-root persistence pattern in `dal` and migrate `CharacterSheet`.
- A `SnapshotService`: take/list/restore campaign snapshots.
- Wire git commits of the agent workspace to the same save action (once §7.2 exists).

---

## 9. The dual frontend (desktop + LAN web), one set of view models

### 9.1 How it works today (keep and extend)

- Java **view models** expose Sprouts properties (`Var`/`Val`) and `void`/value methods.
- The desktop binds **directly** (SwingTree, see `SWING_TREE_SKILL.md`).
- The web client binds **over a websocket** (`net.*`): `WebUserSession` reflects a VM into
  JSON, streams property changes, and dispatches method calls; the React side mirrors it
  (`web-portal/src/mvvm/*` — `ViewModel.ts`, `Var.ts`, `Val.ts`) so React components bind
  to Java properties almost as if local.
- **A feature is built once** (the Java VM) and rendered twice.

### 9.2 Known gaps to close

- **Collections over the bridge.** `Vars<T>`/`Var<Tuple<T>>` and per-item lenses (the
  heart of sheet/roster UIs) are not yet first-class in the bridge — today it handles
  scalars, enums, colours, nested VMs. This is the **highest-leverage** bridge task,
  because sheets and rosters are list-shaped.
- **Lens parity.** The React `Var` should support the same `zoomTo`-style focusing the
  Java side uses, so web sheets edit immutable roots the same way (the user explicitly
  wants the React frontend to bind to **lens** properties — mirror `SWING_TREE_SKILL.md`
  §4 semantics in `web-portal/src/mvvm`).
- **Lifecycle/observer cleanup** (the commented-out unbind-on-close in `WebUserSession`),
  **auth/session** over the socket, and **binary/stream** channels (for any future map
  streaming, §6.2).
- **React UI kit**: grow `components/atoms` into enough widgets to render a character
  sheet; keep components thin (props ← Java VM).

### 9.3 Long-term information architecture

- **Desktop (GM):** a workspace shell with primary areas — **Campaigns** (pick/manage),
  **Sheets/Roster**, **Maps** (the three-mode editor/player, §6), **AI** (chat + tool
  log + GM gate, §7), **Sessions/Timeline** (snapshots, §8), and **Dev** tools (existing
  DB/server inspectors stay behind the `--show-dev-views` flag).
- **Web (player):** log in → see *my characters in this campaign* → operate my sheet,
  roll dice, react to the GM, and (later) view the shared play-mode map. Strictly scoped
  to what a player may see and do.

---

## 10. Roadmap — phased, pickable by agents

Each phase lists outcomes an agent can scope independently. Earlier phases unblock later
ones. Engine-internal LoD work proceeds in parallel under `WORLD_ENGINE_LOD_DESIGN.md`.
Status markers below: **[done]**, **[in progress]**, unmarked = not started.

### Phase 0 — Foundations & naming (small, do first) — **[done]**
- **[done]** Resolve the `World` collision (§4): renamed `app.models.World` → `Campaign`;
  introduced `GameMap` (the class is `GameMap`, the concept is "map") as the campaign↔engine seam.
- **[done]** `CharacterSheet` written as the first `Value` tree (§5.3); broader domain schema
  (Campaign/Map/Session relations, registries per campaign) still to settle.

### Phase 1 — Character sheets end-to-end (proves the whole stack) — **[in progress]**
- **[done]** `CharacterSheet` value + lens-driven **desktop** sheet view (`CharacterSheetView`,
  `CharacterSheetViewModel`; lenses exposed on the VM, tested headlessly, rendered + screenshotted).
- **[done]** Roster: `CampaignService` (find-or-create GM, create/list campaigns & characters,
  persisted-sheet init) + `CampaignViewModel`/`CampaignView` master-detail (rendered + screenshotted).
  Edits to a selected character's sheet round-trip to the database via the persisted-lens path.
- **Bridge: collections + lenses** over the websocket (§9.2) — unblock list/sheet UIs. *(Java side
  unit-testable; React side needs a browser to verify — deferred until a browser runtime is available.)*
- **Web** sheet view binding the same VM; players edit their own sheet over LAN.
- Wire the roster into the actual login flow (`ContentViewModel`/`UserContext`) so it's reachable
  in-app, not just via the standalone `runMain` demos (uses `AppContext`'s thread-marshalling DB processor).

### Phase 2 — Maps in the app — **[in progress]**
- **[done]** `MapWorlds`: build an engine `World` from a `GameMap` recipe (the seam, §6).
- **[done]** `MapView`: embed the engine renderer (free-fly **Build mode**, software backend) — a
  `GameMap` renders as navigable 3D terrain in a SwingTree component (§6.1/§6.2).
- Editing tools (place/remove material, prefabs); GL backend embed; **Table/Play** + **First-person** modes.
- Map persistence (seed + edit diff), tied to the value/snapshot model.
- Map persistence (seed + edit diff), tied to the value/snapshot model.
- **Table/Play mode** with character tokens; **First-person** mode.
- Prefab/object generators (furniture, simple structures).

### Phase 3 — AI copilot (safe subset first)
- Provider interface + one configured remote provider; plain AI chat view (§7.5.1).
- **Domain tools** + **GM gate**: AI proposes sheet/NPC/map changes for approval (§7.5.2).

### Phase 4 — The sandbox & autonomous GM
- Ship `podman` + `git`; build the workspace container and shell/file tools (§7.2).
- Network policy; local-model support; autonomous mode behind explicit authority (§7.5.4).

### Phase 5 — Time-travel & sharing
- `Value`-root snapshots + `SnapshotService`; session-end snapshots; campaign rewind (§8).
- Git-commit the agent workspace on save; combined point-in-time restore.
- Campaign export/import/share.

### Continuous — engine LoD/render
- Execute `WORLD_ENGINE_LOD_DESIGN.md` (step B next), preserving its invariants.

---

## 11. How agents should use this document

- **Pick a section, then go to the code.** Each module's *real* contract is in the code
  and its package javadoc; this doc gives intent and the seams between modules.
- **Engine work** → read `ARCHITECTURE.md` and `WORLD_ENGINE_LOD_DESIGN.md` first; extend
  those, don't duplicate them here. Honor the occlusion/value-semantics invariants.
- **Any GUI or property-binding work** (Java *or* React) → read `SWING_TREE_SKILL.md`;
  the React MVVM in `web-portal/src/mvvm` deliberately mirrors Sprouts semantics, lenses
  included.
- **Persistence work** → respect the `Model`-as-timestamped-root direction (§8); prefer
  `Value` trees over wide mutable models for new state.
- **Agent/harness work** → security first (§7.2): no host filesystem, ever; domain tools
  before shell tools; copilot gate before autonomy.
- **When in doubt, follow the principles in §2** — they override convenience.
- Keep this file current: when a module graduates maturity (e.g. the harness gets its
  first code), update the table in §3 and the relevant phase in §10.
```