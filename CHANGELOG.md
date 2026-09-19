# Changelog

All notable changes to npc2 are documented here.

## 0.3.1-alpha+26.3

- Updated the mod, Fabric Loader, Fabric API, Fabric Language Kotlin, and Gradle wrapper for Minecraft 26.3.
- Migrated NPC swing animations to Minecraft 26.3's built-in entity swing state and rendering APIs.
- Updated renamed block-search, key-mapping, and item-drop APIs.
- Refreshed the README with 26.3 requirements and clearer installation and gameplay guidance.

## 0.3.0-alpha+26.2

- Moved the Survivor NPC Spawn Egg into the vanilla Spawn Eggs creative tab.
- Added an independent editable spawn-egg texture.
- Added compact and pinned debug HUD modes plus an `F8` debug-display toggle.
- Added formatted startup help with clickable spawn and issue-tracker links.
- Added editable settings at `config/npc2/npc2.properties`.
- Changed player combat so NPCs retaliate only against players who harm them.
- Added persistence for the NPC bag, home, remembered resources and stations, learned
  resource availability, and player retaliation.
- Made bag contents drop when an NPC dies.
- Limited remote debug snapshots by distance and cleaned up disconnected-player
  rate-limit state.
- Updated deprecated Minecraft and Fabric API calls.
- Tightened Fabric API metadata and CI release artifacts.
