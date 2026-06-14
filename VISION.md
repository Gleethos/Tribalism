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
> - [`API_STABILITY.md`](API_STABILITY.md) — which APIs are stable contracts (pin with tests) vs flexible (test behavior, keep the shape free).

---

## 1. What Tribalism is

Tribalism is the ultimate helper tool for **Pen & Paper game masters**. One person
(the GM) runs it as a **desktop application**; their players join over the **local
network** through a **web portal** to see and operate their own characters. Around
that core sit three big capabilities:

1. **Maps** — dungeons, towns and landscapes built in a custom **3D voxel engine**,
   used both for prep (building) and at the table (play).
2. **Campaigns & character sheets** — the structured game data: campaigns, characters,
   roles, abilities, skills, NPCs, and the rules that connect them.
3. **An agentic AI game master** — a sandboxed AI harness (in the spirit of Claude
   Code / OpenCode) purpose-built either to **be** a game master or to **co-pilot**
   a human one, with its own isolated Linux-like playground to work in.

The beating heart of the experience is the **live session**: a shared, real-time
meeting where the GM, each player, and the AI are all **participants**. Each
participant has their *own* view of the world — players see only what their character
can see (a view cone and fog of war on their personal map), while the GM and AI see
everything. They talk through a **built-in messaging system** and resolve outcomes by
**throwing virtual dice** on their own screens. The unifying idea that makes this
tractable: **every participant is "a screen + a message channel + a permission set"**
(§7), and the AI is just another participant — it perceives through a screen and acts
through the same channels and tools the GM uses.

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

## 2. Usage workflows & user stories

Tribalism is used in three situations: **solo prep** (the GM builds the world),
**live multiplayer sessions** over the LAN (the table, possibly remote), and
**AI-assisted or AI-run play**. The unit of live play is a **Session** (§7) — the
shared, real-time object every participant has a personalized view onto. The stories
below are the north star; the features in later sections exist to serve them.

### 2.1 The Game Master

*Prep (solo, desktop):*
- "I build the dungeon in **3D Build mode** — carve rooms, place a chest, a trap, a
  staircase — so the space is ready before anyone arrives."
- "I author the **NPCs** (a goblin chief, a barkeep) with their own sheets so I can run
  them in combat and conversation."
- "I tweak the **ruleset** — the campaign's ability/skill/role registries — to fit my
  homebrew."
- "I prepare **character archetypes** players can clone into their own characters."

*Running a session (live):*
- "I **start the LAN server** and share the URL; my players connect from their laptops
  and phones."
- "I open the **play view** (top-down, the view plane floats above the roofs so I'm
  never blocked) and drag tokens into the room."
- "As the party advances, I **reveal** the next room — each player's fog of war updates
  to show only what *their* character can now see."
- "I **whisper** to the rogue 'you spot a tripwire' without the others seeing; I
  **broadcast** scene description to everyone."
- "I drag the goblin's token, **roll** its attack, and the result drops into the shared
  log; I knock 4 off the fighter's HP and their sheet updates live on their phone."
- "I **hand the tavern scene to the AI copilot** to run the small talk while I prep the
  ambush, watching its proposed messages and approving them."

### 2.2 The Player

- "I open the **web portal** on my phone, log in, and pick my character — I see my
  **sheet**: HP, abilities, skills, inventory, notes."
- "I see the map **only as far as my character can see** — a view cone ahead of me, a
  dimmed memory of rooms I've explored, and black where I've never been. It is *my*
  screen, not the GM's god view."
- "I tap my **Perception** skill to roll a check — it pulls my modifier from the sheet,
  rolls the dice on my screen, and shows the GM the result."
- "I **message** the party 'I sneak ahead to scout', and the GM (or the AI) responds in
  the shared chat."
- "I **move my token** within my movement range on my own map view; the GM sees where I
  went."
- "I watch my **HP drop** and a *poisoned* status appear when the trap goes off — pushed
  to me in real time."

### 2.3 The AI agent (autonomous GM or copilot)

The agent is a first-class participant: it **perceives** the world, **decides**, and
**acts** — through the same screens, channels and tools as a human (§9).

- *Perceive:* "I read the current scene — the active map, who is present and where (within
  what I'm allowed to see), their sheets, the recent messages, the dice log, whose turn it
  is — and I can **look through my own camera**: I get a rendered frame (if I'm a
  vision-capable model) or a generated **textual description** of what is visible from that
  viewpoint, derived from the same visibility walk the renderer uses."
- *Act (autonomous GM):* "When a player says they attack, I **roll** the goblin's defense,
  **apply** damage to the player's sheet, **narrate** the outcome to the party, and **reveal**
  the next area — all through domain tools that are the *same services the human GM uses*."
- *Act (copilot):* "I **propose** an NPC's next move or draft a room description; the GM
  approves before it takes effect."
- *Think:* "I use my **sandbox** (a private Linux-like workspace) to keep campaign notes,
  generate a side-quest, or compute a loot table — scratch work that never touches the host."

### 2.4 The live session loop

A session runs as an event-driven (often turn-structured) loop that ties the personas
together:

```
   GM / AI narrates or acts ─► effects apply (map reveal, HP, token move, dice, message)
            ▲                                   │
            │                                   ▼
   next perception  ◄──── players react (messages, dice rolls, token moves, actions)
```

Every arrow is a **message, a dice roll, a token move, or a state change** streamed to
each participant's client, filtered to what that participant may see. That filtering —
fog of war for players, omniscience for GM/AI — is the same mechanism for everyone (§7).

---

## 3. Design principles (non-negotiable)

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
   immutable world/value; renderers own no simulation state and are swappable. A
   *participant's* view is a pure function of (world, that participant's viewpoint +
   visibility scope) — which is what makes per-player fog of war just another filter.
7. **The AI is sandboxed by construction.** The agent never gets ambient authority
   over the host. Its filesystem, its tools, and its network are an explicit,
   enclosed environment. Its *in-game* authority is likewise explicit — a permission
   set on its participant, gated in copilot mode (§7, §9).

---

## 4. System architecture at a glance

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
│  │ Web portal    │   │  │ model +    │ │ engine     │ │ harness   │ ││     │
│  │ (React, LAN)  │◄──┤  │ Session    │ │ (maps:     │ │ (sandbox, │ ││     │
│  │  per-player   │   │  │ (sheets,   │ │  voxel sim │ │  tools,   │ ││     │
│  │  thin client  │   │  │  chat,dice,│ │ + render + │ │  loop) =  │ ││     │
│  └──────┬────────┘   │  │  fog,rules)│ │ per-screen │ │ a partici-│ ││     │
│         │ websocket  │  └─────┬──────┘ │ visibility)│ │ pant      │ ││     │
│         │  (net,     │        │        └─────┬──────┘ └─────┬─────┘ ││     │
│         └────MVVM────┘        ▼              ▼              ▼       ││     │
│            bridge    │  ┌──────────────────────────────────────┐   ││     │
│  (per-participant    │  │ Persistence: Topsoil ORM (SQLite)     │   ││     │
│   filtered views)    │  │  + value snapshots + git time-travel  │   ││     │
│                      │  └──────────────────────────────────────┘   ││     │
│                      └──────────────────────────────────────────────┘│     │
│                       ┌───────────────────────────────────────────┐  │     │
│                       │ Sandbox (podman + git binaries, shipped)   │◄─┘     │
│                       │  the agent's private Linux-like workspace  │        │
│                       └───────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────────────────┘
```

### Module maturity (be honest about where we are)

| Module | Package(s) | State | Where it's documented |
|---|---|---|---|
| **World engine** | `app.engine.*` | **Mature**, standalone; multi-screen/multi-camera already supported (the substrate for per-player views). Now embeddable via `MapView`. | `ARCHITECTURE.md` |
| **Topsoil ORM** | `dal.*` | **Mature** for both `Model` and `Value` trees (the `CharacterSheet` value tree round-trips). | javadoc in `dal.api`; gotchas in `[[topsoil-orm-gotchas]]` memory |
| **MVVM bridge** | `net.*` | **Working** for primitives/enums/nested VMs; needs lists/collections, binary, lifecycle hardening (keystone for live session streaming). | this doc §11 |
| **Desktop shell + auth** | `app`, `app.user`, `app.dev` | **Early prototype**: login/register, user page, dev inspectors. | — |
| **Web portal** | `src/main/web-portal` | **Early prototype**: mirror MVVM, login/register/user views. | — |
| **Domain model** | `app.models.*` | **Reshaping**: `Campaign` + `GameMap` landed (§5); `CharacterSheet` value tree (§6.3) with a lens-driven desktop view; a `CampaignService` + `CampaignView` roster. | this doc §6 |
| **Map↔engine seam** | `app.maps.*` | **Rendering**: `MapWorlds` builds an engine `World` from a `GameMap`; `MapView` embeds the renderer (free-fly Build mode, software backend). No editing/GL/persistence/fog yet. | this doc §8 |
| **Live play (sessions, chat, dice, fog)** | `app.dice.*`, `app.messaging.*`, `app.session.*` | **Core values built**: dice (notation/roll/checks), messaging (audiences + visibility-filtered log), and a `Session` tying participants + chat + dice + turns — all headless-tested. No fog-of-war/engine integration, GUI, or persistence yet. | this doc §7 |
| **Agent harness** | `app.agent.*` | **In progress:** the sandbox seam (podman + git wrappers, binary resolver) and the `TribalismAgentHarness` facade (messages + event listeners + context/snapshot management) are being built behind SPIs; provider + real podman wiring follow. | this doc §9 (esp. §9.8) |
| **Snapshots / time-travel** | *(none yet)* | **Vision only.** | this doc §10 |

---

## 5. The naming collision we resolved

There were **two** unrelated things called `World`:

- `app.engine.world.World` — the **engine** value: the infinite voxel simulation that
  backs a *map*. Keep this name; it is correct in its own domain.
- `app.models.World` — a **domain/DB** record meaning "a campaign setting". Misleading,
  and it clashed.

**Resolved:** the domain concept is **`Campaign`** (the narrative + roster container a
GM runs); its spatial scenes are **`GameMap`s** (the class is `GameMap`, the concept is
"map"; named to avoid the `java.util.Map` clash), each backed by an engine `World` via
`app.maps.MapWorlds` (§8).

---

## 6. Domain model

The domain is the structured game data. It is described as **immutable value trees**
(records + Sprouts collections) hanging off **timestamped persistence roots** — see §10
for how that maps onto Topsoil.

### 6.1 Entities and relationships

```
User ─┬─ (is) ── GameMaster ──< owns >── Campaign ──┬──< has >── GameMap     (engine World scene)
      │                                              ├──< has >── Character   (NPC or PC instance)
      └─ (is) ── Player ──────< joins >── Campaign   ├──< has >── Session     (a live/saved play meeting)
                    │                                 └──< has >── Faction / Location / Quest (later)
                    └──< controls >── Character (their PCs in that campaign)

Session ─┬── activeMap: GameMap
         ├── participants: Tuple<SessionParticipant>   (GM, players→characters, the AI, observers)
         ├── messageLog:  Tuple<Message>               (chat: broadcast / party / whisper / system)
         ├── diceLog:     Tuple<DiceRoll>
         ├── turnOrder:   initiative / turn pointer
         └── per-character VisionState (fog of war: explored sectors + current view cone)

SessionParticipant ─┬── who: User-as-GM | User-as-player(→Character) | AI | observer
                    ├── screen/camera viewpoint  (their view of the active map)
                    ├── visibilityScope: omniscient | fogged(character)
                    └── permissions: which domain actions they may take

Character ─┬── sheet: CharacterSheet (the rules-bearing value tree)
           ├── Role(s) ──< modifies >── Abilities / Skills
           └── placement: optional (GameMap, position, facing) when on a map

Value records (content, not identity): CharacterSheet, Identity, Vitals, AbilityScore,
   SkillScore, InventoryItem  (built);  Message, DiceRoll, VisionState  (to build).

Rules registries (campaign- or app-level, seeded from JSON today):
   AbilityType, SkillType, RoleType   →   see saves/*.json, app.models.ini.*
```

### 6.2 Entity notes

- **User** — credentials + identity. Can log in from desktop *or* web. One user may be
  a GM in one campaign and a player in another, so **GameMaster** and **Player** are
  *roles a User holds within a Campaign*, not separate accounts.
- **Campaign** (was `models.World`) — the GM's save: name, description, the roster of
  characters/NPCs, its maps, its sessions, and the rules registries in force. The natural
  unit of **export/share** and of **snapshot** (§10).
- **GameMap** — a named spatial scene backed by an engine `World` (§8). A campaign has many.
  Persists as generation params + (later) an edit diff.
- **Character** — a concrete person/creature (PC or NPC). Carries a **CharacterSheet**
  value. On a map it has a **placement** (position + facing) that drives both its token and,
  for a player character, the camera + fog of war of the player viewing through it (§7).
- **Session** — a play meeting, **live or saved** (§7). Holds the active map, the
  participants, the message and dice logs, the turn order, and per-character fog-of-war
  state. A session is the object the live UI streams to each client. Its end takes a
  snapshot, so a campaign is a resumable timeline (§10).
- **Message** / **DiceRoll** / **VisionState** — immutable `Value` records (§7); the
  session's logs are `Tuple<Message>` / `Tuple<DiceRoll>`, and fog of war is a per-character
  `VisionState` of explored sectors.
- **Role / Ability / Skill** — the rules layer. `AbilityType`/`SkillType`/`RoleType` are the
  registries (today loaded from `saves/*.json` via `app.models.ini.*`); long-term **per-campaign,
  editable** so a GM can author a ruleset. Sheet ability/skill scores reference types **by name**.

### 6.3 The `CharacterSheet` is the first real `Value` tree

The sheet is the proving ground for the data-oriented persistence model (§10): a single
immutable record (identity, vitals, `Tuple<AbilityScore>`, `Tuple<SkillScore>`,
`Tuple<InventoryItem>`, notes) edited **entirely through lenses** (`zoomTo`). It is the
shape the React client and SwingTree both bind to, so getting it right exercises the whole
stack end-to-end. **Built**: `app.models.sheet.*`, `CharacterSheetView(Model)`, and a
roster (`CampaignService`/`CampaignView`) that edits persisted sheets.

---

## 7. Live play — sessions, participants, fog of war, messaging, dice

This is the **core multiplayer experience** and, currently, the largest unbuilt piece
of the *user-facing* product (the engine that powers it is mature). It is organized
around one unifying abstraction.

### 7.1 The unifying model: a participant is "a screen + a channel + a permission set"

A **Session** is a set of **participants**. Every participant — the GM, each player, the
AI — is the same shape:

- **A screen + camera** — their viewpoint onto the active map (a player looks through their
  character; the GM has a free/god camera; the AI has its own camera, §9).
- **A visibility scope** — *omniscient* (GM, AI-as-GM) or *fogged to a character* (players).
  This scope filters both what they render and what session state they receive.
- **Channels** — which message audiences they can read and write (§7.3).
- **Permissions** — which domain actions they may take (move only my token vs. move any;
  reveal map; edit sheets; roll publicly/secretly). Copilot-mode AI has its effects gated.

Players, GM, and AI differ only in **scope** and **authority**. This is the lever that
makes the whole feature set cohere: fog of war, chat audiences, dice visibility, and the
AI interface are all just *scope + permission* applied to the same session.

### 7.2 Per-player views & fog of war

Each player's screen is bound to a camera at their character's **placement** (§6.2). What
they see is filtered by a per-character **VisionState**:

- **Currently visible** — sectors within a view cone (facing + FOV) and sight radius, with
  line-of-sight blocked by solid geometry. Rendered fully.
- **Explored but not currently visible** — previously seen; rendered **dimmed** (fog-of-war
  memory). Persisted on the character so it survives across sessions.
- **Never explored** — black.
- The **GM and AI-GM** are omniscient (no fog).

**Engine fit:** the engine already supports **multiple screens, each bound one-way to a
camera** (`ARCHITECTURE.md` §5) — exactly one screen per participant. The per-frame
visibility walk `World.collectSectorsForRendering` already does frustum + occlusion culling;
fog of war is **one more mask** layered on it, keyed by the participant's `VisionState`
(cull/dim sectors outside currently-visible ∪ explored). Token/entity visibility uses the
same test. This keeps fog of war a *rendering/query* concern — a pure function of (world,
viewpoint, scope) — not a mutation of the world (principle 6). The GM "revealing" an area is
just adding sectors to the relevant players' explored sets.

*Play modes (a map view = one engine `Screen` + a mode), revisited from the map side in §8.1.*

### 7.3 Messaging — the session's nervous system

A built-in chat carries narration, table talk, whispers, and system events. Everything in
a session — a reveal, a dice result, a status change — can surface as a message, so the log
is also the play-by-play record.

- **`Message`** (a `Value`): `sender` (a participant: User / NPC / AI / System), `audience`
  (a sum type: `Everyone` | `Party` | `Whisper(toParticipant)` | `GmOnly`), `kind`
  (in-character | out-of-character | system), `timestamp`, `body`, and optional
  **attachments** (a `DiceRoll`, a map ping/coordinate, a sheet reference).
- **Delivery** is real-time via the MVVM bridge: the session view model exposes a
  `Var<Tuple<Message>>` (the log), and each participant's client receives only the messages
  whose audience includes them. (This is a prime consumer of the bridge's collection support,
  §11.2.)
- **The AI uses the same channel** — it *speaks* by posting messages (as the GM, an NPC, or
  system narration) and *hears* by reading the log. There is no separate AI I/O path (§9).

### 7.4 Dice

- **`DiceNotation`** — parse `"2d6+3"`, `"1d20"`, advantage/disadvantage, keep-highest, etc.
  (a small pure parser → an AST value).
- **`DiceRoll`** (a `Value`) — the result: the notation, each die's face, the total, crit
  flags, a `roller` (participant) and a `visibility` (`Public` | `Party` | `Whisper` |
  `GmOnly`). Deterministic-from-seed option for reproducible/auditable rolls.
- **Sheet-linked checks** — rolling a skill/ability check pulls the modifier from the
  `CharacterSheet` (e.g. a Perception check reads the relevant `SkillScore`). The result
  posts to the message log with the appropriate visibility.
- Players throw dice **on their own screen**; the GM and AI roll too (NPC actions,
  adjudication). All rolls land in the session's `diceLog` (snapshotable, §10).

### 7.5 Why this section comes before the agent

Sessions/participants/messaging/dice are the **substrate the AI plugs into**. Build the
human-multiplayer loop first; the AI (§9) then joins as one more participant rather than as
a bolt-on. The build order in §12 reflects this.

---

## 8. Maps & the world engine

The world engine (`app.engine`) already exists and is powerful (immutable voxel tree,
infinite LoD streaming, software + OpenGL backends, **multi-screen/multi-camera** — see
`ARCHITECTURE.md`). The remaining work is **integration, authoring tools, and per-player
views**, plus finishing the engine's own LoD roadmap (`WORLD_ENGINE_LOD_DESIGN.md`).

### 8.1 Three interaction modes (a map view = one engine `Screen` + a mode)

A "mode" is a camera rig + tool set + input mapping over the same `World`.

1. **God / Build mode** — free-fly camera, advanced editing: place/remove material, stamp
   prefabs, run generators, sculpt terrain. Used for prep. **Built** (view + free-fly; tools
   pending) as `app.maps.MapView`.
2. **First-person / Survival mode** — walk the map, collide, destroy/place blocks.
3. **Table / Play mode** — top-down with a **view plane above the players' heads** so the
   camera is never occluded by roofs. The mode shown at the table and mirrored to players;
   each *player's* screen in this mode is **fog-of-war filtered** (§7.2). Tokens move here.

### 8.2 Integration work (the actual tasks)

- **Embed the renderer viewport in a SwingTree view.** `Renderer.viewportFor(screenId)`
  yields a `java.awt.Component`; wrap with `UI.of(component)`. **Done** in `MapView`.
- **A `MapEditorViewModel`** holding the current `World`, the active mode, the selected
  tool/material, and camera bindings; **editing tools** that mutate the world (place/remove
  voxel, stamp prefab) — these are also the AI's map domain tools (§9).
- **Per-participant screens + fog of war** (§7.2): one screen per participant, a fog mask on
  the visibility walk keyed by each character's `VisionState`.
- **Persist maps**: seed + generation params + an edit diff (depends on §10 and the engine's
  planned per-sector edit persistence in `ARCHITECTURE.md` §10).
- **Stream the play view to the web portal**: the GM/host renders each player's fogged screen;
  players receive a frame stream (or a thin remote view) and token interactions travel back
  over the websocket. Start desktop-only.
- **Object/prefab generators**: a small starter library (chairs, tables, furniture, houses)
  as procedural `WorldSector` builders.

### 8.3 Engine-internal roadmap

Owned by `WORLD_ENGINE_LOD_DESIGN.md`. Current standing (per project memory): the power-of-two
mesh LoD ladder (step **A**) is **done**; `SurfacePatch`/`VolumePatch` far-field work (step
**B**) is **next**. The occlusion invariant *marked ⊆ drawn ⊆ tested rect* must hold after every
change. Do not re-plan the engine here — extend that doc. (Fog of war, §7.2, is a new mask that
must respect the same invariant: it only *removes* drawn geometry, never adds.)

---

## 9. The agent harness (the defining feature — currently unbuilt)

Tribalism's differentiator is an **agentic AI** that can run a game or assist a GM. It is an
agent loop in the spirit of Claude Code, but **purpose-built for tabletop** and **sandboxed**
so a locally-run model can never touch the host. Crucially, the AI is **a session participant**
(§7.1): it perceives through a screen, communicates through the message channel, and acts
through the same services the GM uses — with an explicit, gateable permission set.

### 9.1 Two modes of operation

- **Autonomous GM** — the AI runs the session: narrates, controls NPCs, adjudicates rules,
  updates maps and sheets, rolls dice, responds to players.
- **GM Copilot** — a human runs the session; the AI suggests, drafts NPCs/encounters, answers
  rules questions, performs bookkeeping the GM approves.

Same harness; the difference is the participant's **permission set** and whether its domain
effects **auto-apply or require a GM gate**.

### 9.2 How the AI *sees* the world (perception)

Two complementary channels, both derived from the same world state:

1. **Symbolic / structured (the reliable, model-agnostic default).** The world is queryable:
   the active map's terrain summary, entity/token positions **within the AI's visibility scope**,
   each character's `CharacterSheet`, the message log, the dice log, the turn order, campaign
   notes. This is assembled into the prompt context (not free-form file scraping). The engine
   answers the positional queries (`sectorAt`, the visibility walk).
2. **Visual (the AI literally has a screen).** As a participant, the AI has its own camera +
   screen (§7.1). The engine renders the frame. A **vision-capable** model is given the image; a
   **text** model is given a **textual scene description** generated from the *same visibility
   walk* that produces the render ("a stone corridor north; a goblin ~10 ft ahead; a chest against
   the east wall"). So "look at the scene" is one mechanism, unifying the AI with players.

### 9.3 How the AI *runs commands* (action) — domain tools = the GM's services

Tool calls are structured (name + JSON args), dispatched to two tiers:

- **Domain tools** — the **same services a human GM uses**, exposed as typed operations:
  `narrate(text, audience)` / `whisper(player, text)` (→ messaging, §7.3),
  `rollDice(notation, visibility)` / `check(character, skill)` (→ dice, §7.4),
  `setHp(character, value)` / `applyStatus(...)` / `editSheet(...)` (→ the sheet lenses, §6.3),
  `moveToken(character, position)` / `revealRegion(map, area, forPlayer?)` (→ map + fog, §7.2/§8),
  `createNpc(...)` (→ `CampaignService`), `advanceTurn()`, `placeVoxel/removeVoxel(...)` /
  `spawnPrefab(...)` (→ map editing, §8.2). These are the safe, high-level surface — the AI
  affects the game **without any shell access**. In copilot mode each effect is **proposed and
  gated**; in autonomous mode it applies directly. Because they are the GM's own services, AI and
  human are symmetric and the same audit log covers both.
- **Sandbox tools** — `bash`, file read/write, `curl` (policy-gated), later Python, **inside the
  podman container** (§9.5) — for open-ended scratch work (notes, generation, computation) that
  must never touch the host.

### 9.4 The harness loop

```
   ┌── perceive: session context (visible scene + sheets + message/dice log + turn) ──┐
   │     (symbolic state, plus a rendered frame or textual scene from the AI's screen) │
   ▼                                                                                   │
 build prompt ─► call provider (local model OR configured API) ─► parse tool calls ────┤
   ▲                                              │                                     │
   │                              ┌── domain tool? ──► apply via GM services            │
   │                              │     (copilot: GM gate; autonomous: direct)          │
   │                              └── sandbox tool? ──► run in podman container          │
   │                                              │                                     │
   └──────────────── effects stream to participants (messages, dice, map, sheets) ◄─────┘
```

- **Provider abstraction.** One interface, implementations for **local models** and
  **user-configured providers** (Anthropic, OpenAI-compatible, …). Per-user, stored locally.
  For Claude specifically, consult the `claude-api` reference and default to the latest models.
- **Context** is assembled from the session (§7), filtered to the AI's visibility scope, plus its
  own rendered/described view.

### 9.5 The sandbox (security boundary)

> Principle 7: the agent gets a playground, never the host filesystem.

- **Ship a `podman` binary and a `git` binary.** The agent's workspace is a hidden directory in
  the app's data dir, owned by a podman container; the app wraps it in a **git repo** from outside.
- **The harness loop lives in the Java app.** Sandbox tool calls execute **against the container**,
  never the host shell.
- **Network is policy-gated** (default-deny except provider endpoints + an allowlist).
- **No host mounts** beyond a single explicit workspace volume.

### 9.6 Snapshots tie into §10

Both the **agent's context** and its **workspace** are snapshot over time (git for the workspace;
value-root snapshots for context and domain state), so a GM can **rewind and continue**. Same
time-travel mechanism as domain persistence — see §10.

### 9.7 Suggested build order

1. **Provider interface + one configured remote provider**, plain chat (no tools/sandbox).
2. **The AI as a session participant that posts/reads messages** (§7.3) — it can converse in a
   live session before it can change anything.
3. **Domain tools + copilot GM gate** — propose sheet/NPC/map changes the GM approves. *No shell.*
4. **The podman+git sandbox** and shell/file tools.
5. **Autonomous mode** + visual perception (its own screen/scene description) + local models.
6. **Context & workspace snapshotting** (folds into §10).

### 9.8 The `TribalismAgentHarness` facade (current build)

Everything in §9.1–§9.6 is reached through **one Java interface, `TribalismAgentHarness`** (package
`app.agent`), which hides the podman container, the git-versioned workspace, the provider call and
the agent loop behind a small, stable surface. The rest of the app talks to *this* — never to
`podman`/`git` directly — so the sandbox stays an implementation detail (principle 7).

The facade owns four responsibilities:

1. **Messaging — talk to the agent.** `send(String)` / `send(AgentMessage)` hands the agent a turn of
   input; the agent's replies (and its tool activity) come back **asynchronously** through an
   **event-listener** mechanism: `addListener(AgentEventListener)` receives a stream of `AgentEvent`s
   (a sum type: assistant text/Δtext, a tool call dispatched, a tool result, turn started/ended,
   errors). This is the same shape as the in-game message channel (§7.3) but for the harness's own
   chat with the model, so the GM can watch the agent think and act.
2. **The loop.** Behind `send`, the harness runs the §9.4 loop: build prompt from context → call the
   `AgentProvider` (SPI: local model or configured API) → parse tool calls → dispatch **domain tools**
   to the GM's services or **sandbox tools** into the **podman container** → stream effects back as
   events → repeat until the turn settles. Providers and tools are SPIs with deterministic fakes so
   the loop is unit-testable without a model or a container (per `API_STABILITY.md`).
3. **Context management.** `context()` exposes the current immutable `AgentContext` (system prompt,
   the running transcript, token/turn budget); `clearContext()` / `compactContext()` / `setSystemPrompt(..)`
   reshape it. Because it is an immutable value, it snapshots for free.
4. **State management across time (the headline).** `snapshot()` captures **one coherent
   point-in-time** — the `AgentContext` value **and** a `git commit` of the container's workspace —
   and returns a **`SnapshotKey` keyed by date-time** (§10.2). `snapshots()` lists them;
   `restore(SnapshotKey)` switches the live agent back to that moment (context value + `git checkout`
   of the workspace). This is the agent half of the unified time-travel in §10.

Sandbox plumbing under the facade:

- **`SandboxRuntime`** — SPI over the container engine. `PodmanSandboxRuntime` runs a **rootless**
  container via the shipped `podman`; a host-isolated `LocalSandboxRuntime` is a **dev/test double only —
  explicitly not the security boundary**. The static podman build's helpers (`crun`/`conmon`/
  `fuse-overlayfs`…) aren't at their compile-time paths, so the runtime **generates** a
  `containers.conf`/`storage.conf` pointing at the extracted binaries with a self-contained graph root.
- **The one un-bundleable dependency:** rootless containers need the **setuid-root** `newuidmap`/
  `newgidmap` to map a subordinate-UID range into the container's user namespace. Setuid-root can only be
  granted by a privileged install, so these **must come from the OS** (`uidmap` on Debian/Ubuntu,
  `shadow-utils` on Fedora/RHEL — auto-installed with a distro podman, which we bypass by shipping our
  own). The installer therefore **declares `uidmap` as a dependency** (`packaging/`, `ext.osRuntimeDependencies`),
  and `PodmanSandboxRuntime.preflight()` checks every prerequisite and refuses to start with a
  **distro-aware** fix (e.g. `sudo apt install uidmap`) rather than a cryptic podman error.
- **`WorkspaceRepo`** — wraps the shipped `git` binary over the container's working directory: `commit`
  (returns a snapshot id), `checkout` (force + clean for a faithful rewind), `log`. This gives §10.2 its
  "git for the agent workspace".
- **`SandboxBinaries`** — locates `podman`/`git`: a bundled per-platform dir first, then a configured
  override, then system `PATH` (git only); reports cleanly when a binary is absent so the harness can
  refuse sandbox tools rather than fall back to the host.

---

## 10. Persistence, snapshots & time-travel

### 10.1 Evolve `Model` into a timestamped root over `Value` trees

Today Topsoil persists `dal.api.Model` interfaces as eagerly-written proxies (a row per field,
relations as tables). The vision (and principle 1) wants the opposite: **a `Model` becomes a thin,
timestamped *root* wrapping a large immutable `Value` data structure**, persisted as a content
value rather than a spray of mutable columns.

- Keep `Model` as **identity + timestamp + pointer** (id, created/updated, current value-root ref).
- Store the heavy state as **`dal.api.Value`** trees — `CharacterSheet` (§6.3) is the first, already
  migrated; `Message`/`DiceRoll`/`VisionState` (§7) are next.
- Because the value is immutable and structurally shared, **writing a new version is cheap and the
  old version stays addressable** — which makes snapshots and undo nearly free.

### 10.2 Snapshots = addressable past value-roots; git for the agent workspace

- **Domain snapshots** — the set of value-root pointers at a moment (per campaign/session). "Rewind"
  repoints to an earlier root. A session takes one at its end so a campaign is a resumable timeline.
- **Agent workspace snapshots** — the container's working dir is a git repo; a snapshot is a commit.
- The two are taken **together** on "save progress" for one coherent point-in-time.

### 10.3 Tasks

- Define the `Value`-root persistence pattern in `dal` and apply it to the session logs.
- A `SnapshotService`: take/list/restore campaign & session snapshots.
- Wire git commits of the agent workspace to the same save action (once §9.5 exists).

---

## 11. The dual frontend (desktop + LAN web), one set of view models

### 11.1 How it works today (keep and extend)

- Java **view models** expose Sprouts properties (`Var`/`Val`) and `void`/value methods.
- The desktop binds **directly** (SwingTree, see `SWING_TREE_SKILL.md`).
- The web client binds **over a websocket** (`net.*`): `WebUserSession` reflects a VM into JSON,
  streams property changes, dispatches method calls; the React side mirrors it
  (`web-portal/src/mvvm/*`).
- **A feature is built once** (the Java VM) and rendered twice.

### 11.2 Known gaps to close

- **Collections over the bridge.** `Vars<T>`/`Var<Tuple<T>>` and per-item lenses (the heart of
  sheets, rosters, **and the live message/dice logs**) are not yet first-class — today it handles
  scalars, enums, colours, nested VMs. This is the **highest-leverage** bridge task; live sessions
  (§7) cannot stream their logs without it.
- **Lens parity.** The React `Var` should support the same `zoomTo`-style focusing the Java side
  uses (mirror `SWING_TREE_SKILL.md` §4 in `web-portal/src/mvvm`).
- **Per-participant filtering.** The bridge must deliver each client only the session state its
  participant may see (fog, whisper audiences) — a server-side scope filter on the streamed VM.
- **Lifecycle/observer cleanup**, **auth/session** over the socket, and **binary/stream** channels
  (for map frame streaming, §8.2).
- **React UI kit**: grow `components/atoms` to render a sheet, a chat panel, a dice tray, a map view.

### 11.3 Long-term information architecture

- **Desktop (GM):** a workspace shell — **Campaigns**, **Roster/Sheets**, **Maps** (the three-mode
  editor/player, §8), **Session** (the live table: play-mode map + chat + dice + turn order), **AI**
  (chat + tool log + gate, §9), **Sessions/Timeline** (snapshots, §10), **Dev** tools (behind
  `--show-dev-views`).
- **Web (player):** log in → pick my character → my **sheet**, my **fogged map view**, the **chat**,
  my **dice tray**. Strictly scoped to what my participant may see and do.

---

## 12. Roadmap — phased, pickable by agents

Each phase lists outcomes an agent can scope independently. Earlier phases unblock later ones.
Engine-internal LoD work proceeds in parallel under `WORLD_ENGINE_LOD_DESIGN.md`. Status markers:
**[done]**, **[in progress]**, unmarked = not started.

### Phase 0 — Foundations & naming — **[done]**
- **[done]** `World`→`Campaign` rename; `GameMap` seam (§5).
- **[done]** `CharacterSheet` as the first `Value` tree (§6.3).

### Phase 1 — Character sheets & roster (proves the stack) — **[in progress]**
- **[done]** `CharacterSheet` value + lens-driven desktop sheet view (rendered + screenshotted).
- **[done]** Roster: `CampaignService` + `CampaignViewModel`/`CampaignView` (rendered + screenshotted);
  edits round-trip to the database via the persisted-lens path.
- **Bridge: collections + lenses** over the websocket (§11.2) — *Java side unit-testable; React side
  needs a browser.* Keystone for both web sheets and live-session logs.
- **Web** sheet view binding the same VM.
- Wire the roster into the real login flow (`ContentViewModel`/`UserContext`).

### Phase 2 — Maps in the app — **[in progress]**
- **[done]** `MapWorlds` seam (§8); **[done]** `MapView` embedding the renderer (Build mode, software).
- Map **editing tools** (place/remove material, prefabs) — also the AI's map tools (§9.3); GL embed.
- Map persistence (seed + edit diff, §10).

### Phase 3 — Live play: sessions, messaging, dice (the multiplayer core, §7) — **[in progress]**
- **[done]** **Dice** (`app.dice`): `DiceNotation` parser + `DiceRoll` value + injectable-randomness
  roller + sheet-linked checks + crit helpers (fully unit-tested).
- **[done]** **Messaging** (`app.messaging`): `Message`/`Sender`/`Audience` values + a
  visibility-filtered `MessageLog` (everyone/party/GM-only/whisper, GM omniscient).
- **[done]** **Session** (`app.session`): `SessionParticipant` (+ `VisibilityScope`) and a `Session`
  value tying participants + chat + dice + turn order; rolling records *and* announces.
- Desktop **chat panel + dice tray**; a `SessionViewModel` over `Var<Session>` (binds the above).
- **Per-player views & fog of war** (§7.2): per-character `VisionState`, one screen per participant,
  a fog mask on the engine visibility walk; GM "reveal" actions. *(Engine-side; bigger.)*

### Phase 4 — Multiplayer over the LAN — **NEW**
- Per-participant **scope filtering** in the MVVM bridge (§11.2): each client sees only its
  participant's state (fog, whisper audiences).
- Web **player session view**: fogged map + sheet + chat + dice tray.
- Stream the play-mode map to players (§8.2).

### Phase 5 — AI copilot (safe subset first, §9)
- Provider interface + one configured remote provider; plain AI chat.
- AI as a **session participant** posting/reading messages (§7.3).
- **Domain tools + GM gate**: AI proposes sheet/NPC/map/dice/message actions for approval (§9.3).

### Phase 6 — The sandbox & autonomous GM (§9.5) — **[in progress]**
- **[done]** Ship `podman` + `git`: `./gradlew fetchSandboxBinaries` downloads + SHA-256-verifies
  podman-static and an embeddable git into a gitignored per-platform bundle (Linux x86-64), found at
  runtime by `app.agent.sandbox.SandboxBinaries`.
- **[done]** The `TribalismAgentHarness` facade (§9.8): messages + event listeners, the agent loop,
  context management, and date-time-keyed snapshot/restore (context value + workspace git commit).
  Built behind provider/tool/runtime SPIs; end-to-end tested with a mocked provider (`agent.*` specs).
- **[done]** Sandbox seam: `SandboxRuntime` (`PodmanSandboxRuntime` real boundary + `LocalSandboxRuntime`
  dev/test double), `WorkspaceRepo` (git time-travel), shell/file sandbox tools.
- Real provider implementations (local models + configured APIs); domain tools wired to GM services;
  network policy hardening; autonomous mode + visual perception (its own screen/scene description).

### Phase 7 — Time-travel & sharing (§10)
- `Value`-root snapshots + `SnapshotService`; session-end snapshots; campaign rewind.
- Git-commit the agent workspace on save; combined point-in-time restore. Campaign export/import.

### Continuous — engine LoD/render
- Execute `WORLD_ENGINE_LOD_DESIGN.md` (step B next), preserving its invariants.

---

## 13. How agents should use this document

- **Pick a section, then go to the code.** Each module's *real* contract is in the code and its
  package javadoc; this doc gives intent and the seams between modules.
- **Lead with the user stories (§2).** Every feature exists to serve a GM, player, or AI-agent
  story; if a change doesn't, question it.
- **Live-play work (§7)** → it all hangs off the *participant = screen + channel + permissions*
  model; build the human-multiplayer substrate before bolting on the AI (§9 plugs into §7).
- **Engine work** → read `ARCHITECTURE.md` and `WORLD_ENGINE_LOD_DESIGN.md` first; extend those.
  Honor the occlusion/value-semantics invariants (fog of war is a new mask under the same rules).
- **Any GUI or property-binding work** (Java *or* React) → read `SWING_TREE_SKILL.md`; the React
  MVVM in `web-portal/src/mvvm` mirrors Sprouts semantics, lenses included.
- **Persistence work** → respect the `Model`-as-timestamped-root direction (§10); prefer `Value`
  trees over wide mutable models for new state.
- **Agent/harness work** → security first (§9.5): no host filesystem, ever; the AI is a participant
  with an explicit, gateable permission set; domain tools before shell tools; copilot gate before
  autonomy.
- **When in doubt, follow the principles in §3** — they override convenience.
- Keep this file current: when a module graduates maturity, update the table in §4 and the relevant
  phase in §12.
```