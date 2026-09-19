# npc2

![GitHub Release](https://img.shields.io/github/v/release/oofman124/npc2)
![GitHub last commit](https://img.shields.io/github/last-commit/oofman124/npc2)
![Minecraft Version](https://img.shields.io/badge/minecraft-26.3-blue)
![Fabric](https://img.shields.io/badge/fabric-blue)

**Modrinth: [modrinth.com/mod/npc2](https://modrinth.com/mod/npc2)**

npc2 is an experimental Fabric mod for Minecraft `26.3` that drops autonomous survival
NPCs into your world. Each one runs on a weighted survival plan, keeps persistent
memories, uses vanilla mob physics, and makes its decisions through a node-based
behavior graph covering gathering, crafting, combat, storage, movement, and sleep.

This is a moving target - an active development build, not a finished product. Back up
your world before you try it, and go in expecting NPCs to reshape the terrain around
them as they mine and build.

> [!IMPORTANT]
> Read the requirements and gameplay notes below before you add npc2 to a world.
>
> **You need Fabric API and Fabric Language Kotlin in your `mods` folder.**
> *Modrinth will show the recommended version for each dependency.*

> [!NOTE]
> The debug overlay looks a bit crooked above a GUI scale of 3 - fix coming soon.
>
> NPCs are painfully slow while floating in water. Also being fixed.
>
> NPCs can occasionally freeze mid-retreat from a player. They'll snap out of it once
> the player leaves detection range, switches to Creative, or `player_retaliation_ticks`
> runs out. Also on the list.

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

- Minecraft `26.3`
- Java `25` or newer
- Fabric Loader 0.19.5 or newer
- Fabric API for 26.3 (currently built against `0.161.0+26.3`)
- Fabric Language Kotlin `1.14.1+kotlin.2.4.20` or newer
- npc2 on the server *and* every client that connects

Check [CHANGELOG.md](CHANGELOG.md) for what's changed release to release.

## Installation

1. Install Fabric Loader for 26.3.
2. Drop Fabric API, Fabric Language Kotlin, and the npc2 jar into your `mods` folder.
3. Playing multiplayer? All three jars need to be on the dedicated server *and* on
   every client.
4. Load into a world - you should see npc2's load notice and a quick spawning guide
   pop up in chat.

If you're building from source, grab the normal jar out of `build/libs`, not the
`-sources` one.

## Spawning an NPC

You'll find the Survivor NPC Spawn Egg in the vanilla Creative inventory under
`Spawn Eggs`. It uses its own editable texture, at
`src/main/resources/assets/npc2/textures/item/fake_npc_spawn_egg.png`, so feel free to
reskin it. You can also just command it in:

```mcfunction
/give @s npc2:fake_npc_spawn_egg
```

Use the egg somewhere open and solid. A forest edge, a plains biome near some trees, or
an exposed stone hillside all give a fresh NPC a decent shot at finding food, logs,
stone, coal, wool, and surface-reachable iron nearby.

The first time you join a world, npc2 posts a load notice in chat along with this same
spawning rundown - happens once per connection, whether that's singleplayer, LAN, or a
dedicated server. It's a few formatted lines, with the spawn-egg command and the
issue-tracker link underlined and clickable, plus a reminder of the current keys for
pinning the NPC panel and toggling debug.

NPCs are friendly out of the box. One will only fight back against a survival or
adventure player who actually hits it, and it forgets the grudge once the configured
retaliation window passes. Creative and spectator players are never targeted, full stop.

## How the NPC works

There's no order-giving yet - the NPC runs itself. It's constantly re-scoring its own
needs and picking between gathering and production accordingly. A shortage in one area
doesn't automatically win out, either; urgency, current tool tier, stockpile levels,
what it can actually craft or smelt right now, and what it's learned is available
locally all factor into the score.

Here's roughly what it's aiming to keep stocked:

| Resource | Target | Typical purpose |
| --- | ---: | --- |
| Food | 8 | Healing and survival |
| Logs | 16 | Planks, sticks, tools, beds, and workstations |
| Cobblestone | 24 | Stone tools and furnaces |
| Soil blocks | 12 | Terrain assistance and emergency cover |
| Coal or charcoal | 8 | Fuel and torches |
| Torches | 16 | Portable light |
| Iron, raw plus smelted | 12 | Iron tool progression and stockpiling |

Tool progression pushes some needs to the front of the line. An NPC with no pickaxe at
all cares about logs first; once it's got a wooden one, cobblestone jumps up; a stone
pickaxe in hand makes iron and furnace access matter a lot more.

### Finding and gathering resources

Resource discovery happens on two layers:

- A primary search that scans a bounded area around the NPC across several ticks. If
  the HUD says `searching resources` and the NPC isn't moving, that's this calculation
  running - not the NPC being stuck.
- A low-cost background survey that's always scanning loaded chunks outward in rings
  and logging what it finds, by category, into `NpcMemories`. This one never takes
  control of movement on its own - it just remembers things for later.

The background survey only logs blocks it can actually reach from outside air, so a
vein sealed away underground won't tempt an NPC toward something it can't get to. It
also only looks at loaded chunks. If a resource keeps turning up unreachable or missing,
its local confidence - and the score tied to it - decays, letting some other need take
priority. That confidence comes back over time, or immediately after a successful
harvest.

After mining a block, the NPC pauses briefly before moving on to the next one. That
small delay is there to stop rapid flip-flopping between gathering and exploring, and to
give drops time to land in its inventory.

### Crafting, furnaces, and placement

For production, the NPC can hand-craft simple items, use a crafting table, or smelt at a
furnace - using one already in the world if it's reachable, or placing one from its own
bag if not. Placing a station needs a replaceable block, solid ground underneath, and a
reachable spot to stand next to it. If nothing nearby works out, the NPC just moves to
another patch and tries again rather than getting stuck waiting.

If you're testing, give crafting tables, furnaces, beds, and chests a clear two-block-high
path and a bit of flat, solid floor around them.

### Loot and storage

NPCs will pick up useful dropped items and raid accessible chests for anything useful -
but not indiscriminately. What counts as "useful" depends on current needs, food, beds,
wool, gear upgrades, shields, totems, and tool durability.

To avoid NPCs dogpiling the same stuff, they reserve loot, resources, beds, and
containers while they're working toward them. Once their 27-slot bag fills up, or a
stockpile goes over target, they'll offload the extra into a nearby chest - you can
actually see the lid pop open while this happens.

Easiest way to help one along: drop a useful item near it, or leave it in a chest it can
reach. If it ignores something, check the HUD's resource needs and inventory first -
there's usually a reason.

### Combat and survival

NPCs fight back against monsters and bees, and retaliate against survival/adventure
players who hit them. They'll hunt adult food animals when food is their top need, and
sheep specifically when they need wool for a bed. They upgrade to better weapons and
armor as they find them, use shields and totems, retreat once health gets critical,
react to ranged attackers, and - if no shield is handy - may slap down a carried block
as makeshift creeper cover.

While sleeping, their detection and engagement ranges drop to a quarter of normal, so
they're a lot less alert.

### Beds, home, and floor sleep

Come nightfall, an NPC looks for a nearby reachable bed, or places one from its bag if
it has to. Whichever bed it last actually slept in becomes its "home," for as long as
that bed still exists - and on later nights, getting back home takes priority over
gathering or crafting. A closer bed can substitute in a pinch, and if home becomes
unreachable or unsafe, the NPC just deprioritizes it instead of getting permanently
stuck on the idea.

If midnight rolls around with no bed found or placed, the NPC just sleeps on the ground
where it stands. It wakes at daylight, or immediately if something threatens it. Floor
sleep is based on ground height rather than the usual bed-height offset.

Bag contents, home, remembered resources and stations, learned local availability, and
active player grudges all survive chunk unloads and world restarts. If the NPC dies,
though, its bag spills out into the world.

## Configuration

The first time you launch, npc2 writes out:

```text
.minecraft/config/npc2/npc2.properties
```

It's a commented Java properties file. Open it in any text editor while
Minecraft is closed. Restart the game or server after making changes for them to take
effect.

| Setting | Default | Purpose |
| --- | ---: | --- |
| `startup_message` | `true` | Show the formatted quickstart when entering a world |
| `debug_hud` | `true` | Initial visibility of NPC debugging; `F8` can still toggle it |
| `debug_path_rendering` | `true` | Draw the selected NPC's path in the world |
| `debug_max_distance` | `128.0` | Maximum server distance for debug snapshots |
| `player_retaliation_ticks` | `600` | How long an NPC remembers a player attack; `0` disables retaliation |
| `resource_search_block_budget` | `8192` | Work allowed for each primary resource-search slice |
| `resource_survey_block_budget` | `256` | Background resource checks per NPC tick |

Turn either search budget down if you want less per-tick work, at the cost of slower
resource discovery.

## Debug HUD

Close any menus and aim near a living NPC - a compact debug panel will show up with its
name, entity ID, health, and the current pin/full-debug key bindings. Selection uses a
forgiving cone rather than demanding a pixel-perfect crosshair hit, works at whatever
range your client is actually tracking the entity at, and needs a clear line of sight.

Press `X` while hovering an NPC to pin it, and again to unpin. You'll find the binding
under `Debug` in the Controls menu. Pins clear themselves automatically if the NPC dies,
despawns, unloads, or you leave the world - so you're never locked out of inspecting a
different one. Pinning also expands the panel to show the full debug breakdown.

`F8` toggles all npc2 debugging at once - HUD, glow, and path trace. Both bindings can
be remapped under `Debug` in Controls.

Whichever NPC is selected or pinned gets a glowing outline on your client. The panel
itself is server-authoritative and refreshes about twice a second, showing:

- `Status`: compact icons for whatever survival/work states are currently active.
- `Decision`: active state, who owns movement right now, current target, the weighted
  plan, selected resource, search radius or survey ring, workstation relocation, home,
  and any active flags.
- `Movement / path`: position, dimension, velocity, environment, path progress, stall
  counters, partial-path timing, retry count, and recovery state.
- `Resource needs`: item icon, current vs. target counts, adjusted score, and learned
  local confidence.
- `Equipment` and `Inventory`: all six equipped slots and the full 27-slot bag.

While there's a fresh snapshot, the current path draws in the world:

- Yellow - the NPC's next path node.
- Cyan - the rest of the reachable route.
- Gray - nodes it's already passed.
- Red - an invalid or partial route.
- Green destination - reachable; red destination - invalid.

`Path: arrived (working)` just means it's intentionally standing still to mine or
interact with something. `Path: acquiring work target` means it wants to move but is
still searching for where. `Path: none` is completely normal during hand crafting,
sleeping, and some idle moments.

## Common problems

| Symptom | What it usually means | What to do |
| --- | --- | --- |
| Game reports missing Kotlin classes, or npc2 won't load | Fabric Language Kotlin is missing or too old | Install Fabric Language Kotlin `1.13.13+kotlin.2.4.10` or newer on both client and server |
| HUD won't show up | No living, tracked NPC is in the selection cone, something's blocking sight, or a menu's open | Close menus, get within the NPC's tracked area, and aim near it with clear line of sight |
| `X` does nothing | Nothing's currently selected, or another binding conflicts | Confirm the HUD is showing, then check Controls > Debug and rebind `Pin NPC Debug Panel` |
| A pinned NPC died and the HUD won't reopen for another | Stale pinned entity ID from an older build | Update - dead/removed/unloaded/disconnected targets now clear themselves |
| Panel shows up, then disappears | Server stopped sending snapshots - usually because the entity died/unloaded, or client and server versions don't match | Keep the NPC loaded, and make sure both sides run the same npc2/Fabric versions |
| NPC is attacking a player | That player hit this NPC recently, within `player_retaliation_ticks` | Stop attacking and wait it out, or set the config to `0` and restart |
| `searching resources` with no movement | Primary block scan is running and deliberately not moving yet | Let the staged scan finish; watch the path and search-radius lines for progress |
| NPC keeps exploring and finding nothing | Candidate blocks are missing, unloaded, sealed off, reserved, or unreachable | Keep nearby chunks loaded, open up a route, relocate the NPC somewhere richer, or just hand it supplies |
| Resource score keeps dropping | Repeated failed searches or blocked routes are lowering learned confidence | Expected behavior - expose the resource, move the NPC, or just wait for confidence to recover |
| A dropped item nearby gets ignored | Bag's full, item isn't currently useful, another task owns movement, or there's no path to it | Check needs/inventory, clear obstructions, or drop it in a chest it can reach |
| Path is `none` while it clearly has a plan | Still acquiring a target, waiting on a staggered retry, or hand crafting | Check movement owner, flags, target, and search lines before assuming it's stuck |
| Path shows `0/1`, `invalid-end`, or rising stall/retry counts | Vanilla pathfinding only found a partial or no valid route | Open a two-block-high route, clear fences/trapdoors/deep drops, and provide a solid adjacent standing tile |
| NPC wanders into water and seems stuck | It's trying to find reachable dry ground | Give it a sloped or stepped shoreline; check whether `escaping water` and stall counters recover |
| Crafting table/furnace placement keeps relocating | Every nearby site is blocked, unsupported, obstructed, or lacks a reachable side | Clear a flat spot with solid floor and at least one open adjacent tile |
| NPC won't sleep in a bed | Bed's occupied, reserved, unreachable, threatened, or its approach tile is blocked | Clear space beside it, deal with nearby threats, or place a second bed within 12 blocks |
| NPC sleeps on the floor | Midnight arrived with no bed available | Have a reachable bed ready before midnight - floor sleep is the intended fallback, not a bug |
| Performance drops with more NPCs | Each one is running sensing, planning, pathfinding, and a background survey | Reduce NPC count and loaded area; use the HUD to spot repeated failed paths or searches |

If you're reporting an AI freeze, pin the NPC first and grab the full `Decision`,
`Movement / path`, resource needs, and path trace. Include the game log, your
Minecraft/Fabric/npc2 versions, dimension, nearby terrain, and whether it recovers on
its own after about ten seconds.

## Building and development

Clone the repo with its Asterisk submodule, or pull it into an existing clone:

```bash
git submodule update --init --recursive
```

Handy Gradle commands:

```bash
./gradlew runClient     # Integrated development client
./gradlew runServer     # Dedicated development server
./gradlew build         # Compile, test, validate, and create jars
./gradlew clean build   # Rebuild everything from scratch
```

Where the actual logic lives:

- `FakeNpcEntity` / `CoolEntity`: entity, inventory, equipment, and controller lifecycle.
- `NpcBrain`: builds the behavior graph, orders policies, and schedules ticks.
- `NpcMemories`: any AI value that needs to outlive a single generated event.
- `SurvivalPlanner`: weighted resource needs, crafting/smelting projections, and tool
  progression.
- `NpcController`: shared world interaction, combat, inventory, and block helpers.
- `NpcPathNavigation`: path validation, throttling, progress detection, and recovery.
- `ai/movement/ResourceSurveyor`: the patient background resource-discovery scanner.
- `ai/interaction`: reusable block/workstation placement and interaction flows.
- `NpcDebugNetworking` / `NpcDebugHud`: server snapshots and client-side visualization.
- `modules/asterisk`: the bundled graph engine dependency.

Stable object references belong in the graph's context template. Per-tick values belong
in the global event context. Anything that needs to persist longer than a single event
belongs in `NpcMemories`. Behavior nodes should mostly just coordinate those three
systems rather than hold onto their own persistent state.

### Build troubleshooting

- `invalid source release: 25`, `Unsupported class file`, or a wrong-JVM error: point
  `JAVA_HOME` and Gradle's JVM at Java 25+, then confirm with `./gradlew --version`.
- Missing `:asterisk`, included-build, or Kotlin sources: run
  `git submodule update --init --recursive` and try again.
- Minecraft/Fabric dependency or mixin errors: stick to the exact versions in
  `gradle.properties` - jars built for a different Minecraft release won't work.
- Stale generated/remapped classes after changing code or mappings: run
  `./gradlew --stop` and then `./gradlew clean build`.
- Dedicated server starts but won't accept connections on first run: accept Mojang's
  EULA in the generated run directory, then start `runServer` again.

Before committing a behavior change: run `./gradlew build`, spawn a fresh NPC and watch
it through at least one gather/craft cycle, test it against an unreachable target, and
test death or chunk unloading while the HUD is pinned. Put identifiable items in its bag,
restart the world, and confirm the bag and remembered home came back - then kill a test
NPC and confirm the bag actually drops.

## License

MIT. See `LICENSE`.