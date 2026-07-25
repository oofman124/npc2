# npc2

![GitHub Release](https://img.shields.io/github/v/release/oofman124/npc2)
![GitHub last commit](https://img.shields.io/github/last-commit/oofman124/npc2)
![Minecraft Version](https://img.shields.io/badge/minecraft-26.2-blue)
![Fabric](https://img.shields.io/badge/fabric-blue)

**Modrinth: [modrinth.com/mod/npc2](https://modrinth.com/mod/npc2)**

npc2 is an experimental Fabric mod for Minecraft 26.2 that adds autonomous survival
NPCs. Each NPC has a weighted survival plan, persistent memories, vanilla mob physics,
and a node-based behavior graph for gathering, crafting, combat, storage, movement, and
sleep.

This is an active development build. Back up worlds before testing it, and expect NPCs
to alter the environment by mining and placing blocks.

> [!IMPORTANT]
>Read the requirements and gameplay notes before adding npc2 to a world.
>
>**You need Fabric API and Fabric Language Kotlin in the `mods` folder.**

> [!NOTE]
>The debug overlay will look less crooked at a GUI scale of 3 or less. The bug will be fixed soon.
>
>NPCs are horribly slow when floating in the water. This bug will be fixed soon.
>
> NPCs may occasionally freeze while retreating from a player. They resume  once the player leaves their detection range, enters Creative mode, or the configured player_retaliation_ticks duration expires. This bug will be fixed soon.
## Table of contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Spawning an NPC](#spawning-an-npc)
- [How the NPC works](#how-the-npc-works)
  - [Finding and gathering resources](#finding-and-gathering-resources)
  - [Crafting, furnaces, and placement](#crafting-furnaces-and-placement)
  - [Loot and storage](#loot-and-storage)
  - [Combat and survival](#combat-and-survival)
  - [Beds, home, and floor sleep](#beds-home-and-floor-sleep)
- [Configuration](#configuration)
- [Debug HUD](#debug-hud)
- [Common problems](#common-problems)
- [Building and development](#building-and-development)
  - [Build troubleshooting](#build-troubleshooting)
- [License](#license)

## Requirements

- Minecraft 26.2
- Java 25 or newer
- Fabric Loader 0.19.3 or newer
- Fabric API compatible with Minecraft 26.2 (the project currently uses
  `0.154.2+26.2`)
- Fabric Language Kotlin `1.13.13+kotlin.2.4.10` or newer
- npc2 on both the server and every joining client

See [CHANGELOG.md](CHANGELOG.md) for release changes.

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API, Fabric Language Kotlin, and the npc2 jar in the instance's `mods`
   directory.
3. In multiplayer, install all three jars on the dedicated server and on each client.
4. Enter a world and confirm that npc2's load notice and quickstart appear in chat.

When building from source, use the normal jar in `build/libs`, not the `-sources` jar.

## Spawning an NPC

The Survivor NPC Spawn Egg is in the vanilla Creative inventory's `Spawn Eggs` tab. It
uses its own editable texture at
`src/main/resources/assets/npc2/textures/item/fake_npc_spawn_egg.png`. It can also be
given with:

```mcfunction
/give @s npc2:fake_npc_spawn_egg
```

Use the egg on solid, open ground. A forest edge, plains biome near trees, or exposed
stone hillside gives a new NPC the best chance to find food, logs, stone, coal, wool,
and surface-accessible iron.

When the client enters a world, npc2 adds a load notice and this spawning quickstart to
game chat. The notice appears once per world connection, including singleplayer, LAN,
and dedicated-server connections. The message supports multiple formatted lines. Its
spawn-egg command and issue-tracker link are underlined and clickable, and it lists the
current keys for pinning the NPC panel and toggling all npc2 debugging.

Players are friendly by default. An NPC only retaliates against a survival or adventure
player who harms that specific NPC, and it forgets the attack after the configured
retaliation time. Creative and spectator players are never combat targets.

## How the NPC works

The NPC is autonomous; there are no direct orders yet. It repeatedly scores its current
needs and chooses between gathering and production. A deficit does not automatically
win: urgency, tool progression, stockpile levels, possible recipes, smelting inputs,
and learned local availability all affect the score.

The main stockpile targets are:

| Resource | Target | Typical purpose |
| --- | ---: | --- |
| Food | 8 | Healing and survival |
| Logs | 16 | Planks, sticks, tools, beds, and workstations |
| Cobblestone | 24 | Stone tools and furnaces |
| Soil blocks | 12 | Terrain assistance and emergency cover |
| Coal or charcoal | 8 | Fuel and torches |
| Torches | 16 | Portable light |
| Iron, raw plus smelted | 12 | Iron tool progression and stockpiling |

Tool progression adds urgent requirements. For example, an NPC without a pickaxe first
prioritizes logs, a wooden pickaxe leads to cobblestone, and a stone pickaxe makes iron
and furnace support more important.

### Finding and gathering resources

Resource discovery has two layers:

- The primary search checks a bounded area around the NPC in multi-tick slices. The NPC
  may stand still while the HUD says `searching resources`; that is a calculation phase,
  not wandering.
- A low-budget background survey continuously scans loaded chunks in outward rings and
  records categorized resources in `NpcMemories`. It never owns movement by itself.

The background survey only remembers blocks considered accessible from outside air, so
sealed deep ore does not lure an NPC toward an impossible target. It searches loaded
chunks only. If a resource cannot be found or reached repeatedly, its local confidence
and effective score decay, allowing another need to win. Confidence recovers over time
or immediately after a successful harvest.

After mining, the NPC waits briefly before selecting the next block. This prevents rapid
gather/explore state churn and lets drops enter its inventory.

### Crafting, furnaces, and placement

The production plan can hand-craft basic supplies, use a crafting table, or smelt with a
furnace. An NPC can use an accessible workstation already in the world or place one from
its bag. Placement requires a replaceable block, a sturdy floor, and a reachable adjacent
approach position. If no nearby site works, the NPC relocates to another reachable patch
and searches again instead of waiting forever at the blocked location.

For reliable testing, leave at least a two-block-high walking route and a small area of
flat solid floor near crafting tables, furnaces, beds, and chests.

### Loot and storage

NPCs collect useful dropped items and useful contents from accessible chests. Their loot
score considers current needs, food, beds, wool, equipment upgrades, shields, totems, and
tool durability. They do not pick up every item unconditionally.

NPCs reserve loot, resources, beds, and containers so multiple NPCs are less likely to
crowd the same target. When their 27-slot bag becomes crowded or a stockpile exceeds its
target, they can deposit excess supplies into an accessible chest. Chest lids visibly
open while the NPC is interacting with them.

Dropping a useful item near an NPC or placing it in a reachable chest is the easiest way
to supply it. If it refuses an item, check the HUD's resource needs and inventory first.

### Combat and survival

NPCs fight monsters and bees, and retaliate against survival/adventure players who harm
them. They can hunt adult food animals when food is the highest survival need and sheep
when wool is needed for a bed. NPCs equip better weapons and armor, use shields and
totems, retreat at critical health, respond to ranged threats, and may place a carried
block as emergency creeper cover when no shield is available.

Sleeping reduces hostile detection and defensive engagement ranges to 25 percent of
their normal values.

### Beds, home, and floor sleep

At night an NPC searches for a nearby reachable bed or places a carried bed. The last bed
it successfully slept in becomes its remembered home while that bed still exists. At
later nights, returning home takes priority over ordinary gathering and crafting. A
nearby bed can be used instead, and an unreachable or threatened home is deferred rather
than permanently locking the NPC onto it.

If no bed can be found or placed by midnight, the NPC sleeps directly on the floor. It
wakes at daylight or for a valid threat. Floor sleep uses ground height rather than the
vanilla bed-height offset.

NPC bag contents, home, remembered resources and stations, learned local availability,
and active player retaliation survive chunk unloading and world restarts. Bag contents
drop into the world when the NPC dies.

## Configuration

On first launch, npc2 creates:

```text
.minecraft/config/npc2/npc2.properties
```

This is a commented Java properties file that can be edited with any text editor while
Minecraft is closed. Restart the game or dedicated server after changing it.

| Setting | Default | Purpose |
| --- | ---: | --- |
| `startup_message` | `true` | Show the formatted quickstart when entering a world |
| `debug_hud` | `true` | Initial visibility of NPC debugging; `F8` can still toggle it |
| `debug_path_rendering` | `true` | Draw the selected NPC's path in the world |
| `debug_max_distance` | `128.0` | Maximum server distance for debug snapshots |
| `player_retaliation_ticks` | `600` | How long an NPC remembers a player attack; `0` disables retaliation |
| `resource_search_block_budget` | `8192` | Work allowed for each primary resource-search slice |
| `resource_survey_block_budget` | `256` | Background resource checks per NPC tick |

Lowering either search budget reduces per-tick work but makes resource discovery slower.

## Debug HUD

Close menus and aim near a living NPC to open the compact debug panel. It shows the NPC's
name, entity ID, health, and the configured pin and full-debug toggle keys in a smaller
frame. Selection uses a forgiving angular cone instead of requiring an exact crosshair
hit, works at any range at which the client is actually tracking the entity, and requires
an unobstructed line of sight.

Press `X` while hovering an NPC to pin it. Press `X` again to unpin it. The binding is
listed under the `Debug` category in Minecraft's Controls menu. A pin is automatically
cleared if its NPC dies, is removed, unloads, or the client leaves the world, so another
NPC can be inspected immediately. Pinning expands the compact panel to show the full
debug information below.

Press `F8` to hide or show all npc2 debugging, including the HUD, glow, and path trace.
Both bindings can be changed under the `Debug` category in Controls.

The selected or pinned NPC receives a glowing outline on the local client. The panel is
server-authoritative and refreshes roughly twice per second. Its sections show:

- `Status`: compact icons for active survival and work states.
- `Decision`: active state, movement owner, target, weighted plan, selected resource,
  search radius or survey ring, workstation relocation, home, and active flags.
- `Movement / path`: position, dimension, velocity, environment, path progress, stall
  counters, partial-path time, retry count, and recovery state.
- `Resource needs`: item icon, current and target counts, adjusted score, and learned
  local confidence.
- `Equipment` and `Inventory`: the six equipped slots and all 27 bag slots.

While the panel has a fresh snapshot, the current path is drawn in the world:

- Yellow connects the NPC to its next path node.
- Cyan marks the remaining reachable route.
- Gray marks nodes already passed.
- Red marks an invalid or partial route.
- Green marks a reachable destination; red marks an invalid destination.

`Path: arrived (working)` means the NPC is intentionally stationary while mining or
interacting. `Path: acquiring work target` means its plan needs movement but a target is
still being searched for. `Path: none` is normal for hand crafting, sleeping, and some
idle phases.

## Common problems

| Symptom | What it usually means | What to do |
| --- | --- | --- |
| The game reports missing Kotlin classes or refuses to load npc2 | Fabric Language Kotlin is missing or older than npc2's required runtime | Install Fabric Language Kotlin `1.13.13+kotlin.2.4.10` or newer on the client and server |
| The HUD does not appear | No living, client-tracked NPC is inside the selection cone, a block obstructs sight, or a menu is open | Close menus, move into the NPC's tracked area, and aim near its body with clear line of sight |
| `X` does nothing | The cursor is not currently selecting an NPC or another key binding conflicts | Confirm the HUD is visible, then check Controls > Debug and rebind `Pin NPC Debug Panel` |
| A pinned NPC died and another HUD will not open | This was caused by a stale pinned entity ID in older builds | Update to the current build; dead, removed, unloaded, and disconnected targets now clear automatically |
| The panel appears and then vanishes | The server stopped returning snapshots, commonly because the entity died/unloaded or client and server mod versions differ | Keep the NPC loaded and install the same npc2/Fabric versions on both sides |
| The NPC attacks a player | That player harmed this NPC within the configured retaliation window | Stop attacking and wait for `player_retaliation_ticks` to expire, or set it to `0` and restart |
| `searching resources` shows no movement | The primary block scan is running and deliberately owns no path | Wait for the staged scan to finish; use the path and search-radius lines to confirm progress |
| The NPC repeatedly explores without finding anything | Candidate blocks are absent, unloaded, sealed away from outside air, reserved, or unreachable | Keep surrounding chunks loaded, expose a route to resources, move the NPC to a richer surface area, or provide supplies as drops/chest loot |
| A resource's score keeps decreasing | Repeated complete searches or failed routes reduced learned local availability | This is expected fallback behavior; expose the resource, move the NPC, or wait for confidence recovery |
| A needed dropped item is ignored | The bag is full, the item is not currently useful, another task owns movement, or no path reaches it | Inspect needs/inventory, remove obstructions, or put the item in an accessible nearby chest |
| Path is `none` while work is planned | The NPC is acquiring a target, waiting for a staggered retry, or hand crafting | Check movement owner, flags, target, and search lines before treating it as stuck |
| Path is `0/1`, `invalid-end`, or stall/retry counts rise | Vanilla found only a partial route or no valid approach tile | Open a two-block-high route, remove fences/trapdoors or deep drops, and provide a solid adjacent standing tile |
| The NPC enters water and appears stuck | Water escape is trying to find reachable dry ground | Provide a sloped or stepped shoreline; watch for `escaping water` and whether stall counters recover |
| Crafting table/furnace placement keeps relocating | Every local site is blocked, unsupported, obstructed by an entity, or lacks a reachable side | Clear a flat patch with solid floor and at least one open adjacent tile |
| The NPC will not sleep in a bed | The bed is occupied/reserved, unreachable, threatened, or its approach tile is blocked | Clear space beside the bed, remove nearby threats, or place another bed within 12 blocks |
| The NPC sleeps on the floor | Midnight was reached without an available nearby bed | Provide a reachable bed before midnight; floor sleep is the intended fallback |
| Performance drops with many NPCs | Each NPC performs sensing, planning, pathfinding, and a budgeted background survey | Reduce the NPC count and loaded area; use the HUD to identify repeated failed paths or searches |

When reporting an AI freeze, pin the NPC and capture the full `Decision`, `Movement / path`,
resource needs, and path trace. Include the game log, Minecraft/Fabric/npc2 versions,
dimension, nearby terrain, and whether the behavior recovers after roughly ten seconds.

## Building and development

Clone the repository with its Asterisk submodule, or initialize it in an existing clone:

```bash
git submodule update --init --recursive
```

Useful Gradle commands:

```bash
./gradlew runClient     # Integrated development client
./gradlew runServer     # Dedicated development server
./gradlew build         # Compile, test, validate, and create jars
./gradlew clean build   # Rebuild everything from scratch
```

The main implementation areas are:

- `FakeNpcEntity` / `CoolEntity`: entity, inventory, equipment, and controller lifecycle.
- `NpcBrain`: behavior graph construction, policy ordering, and tick scheduling.
- `NpcMemories`: every AI value that must survive longer than one generated event.
- `SurvivalPlanner`: weighted resource needs, crafting/smelting projections, and tool
  progression.
- `NpcController`: shared world interaction, combat, inventory, and block helpers.
- `NpcPathNavigation`: path validation, throttling, progress detection, and recovery.
- `ai/movement/ResourceSurveyor`: patient background resource discovery.
- `ai/interaction`: reusable block/workstation placement and interaction flows.
- `NpcDebugNetworking` / `NpcDebugHud`: server snapshots and client visualization.
- `modules/asterisk`: the included graph engine dependency.

Stable object references belong in the graph's context template. Per-tick values belong
in the graph's global event context, and state that lasts more than one event belongs in
`NpcMemories`. Behavior nodes should primarily coordinate those systems instead of
keeping persistent state in node instance fields.

### Build troubleshooting

- `invalid source release: 25`, `Unsupported class file`, or the wrong JVM: configure
  `JAVA_HOME` and Gradle's JVM to use Java 25 or newer, then run `./gradlew --version`.
- Missing `:asterisk`, included-build, or Kotlin sources: run
  `git submodule update --init --recursive` and retry.
- Minecraft/Fabric dependency or mixin errors: use the exact versions in
  `gradle.properties`; jars built for another Minecraft release are not compatible.
- Stale generated/remapped classes after a code or mapping change: run
  `./gradlew --stop` followed by `./gradlew clean build`.
- A dedicated server starts without accepting connections on first run: accept Mojang's
  EULA in the generated run directory, then start `runServer` again.

Before committing a behavior change, run `./gradlew build`, test a newly spawned NPC,
inspect it through at least one gather/craft cycle, test an unreachable target, and test
death or chunk unloading while the HUD is pinned. Put identifiable items in the NPC bag,
restart the world, and confirm the bag and remembered home persist; then kill a test NPC
and confirm its bag contents drop.

## License

MIT. See `LICENSE`.
