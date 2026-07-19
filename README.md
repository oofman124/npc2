# NPC2

### **Run `/give @s npc2:fake_npc_spawn_egg` to get the spawn egg!!!!!!!!!**

NPC2 is an experimental Fabric mod for autonomous survival NPCs. The NPCs use vanilla
mob navigation and physics while a node-based AI coordinates combat and everyday tasks.

Current behaviors include:

- Combat, hunting, retreating, shields, ranged-threat prediction, and equipment selection.
- Ground and chest looting with reservations so NPCs are less likely to crowd one target.
- Food management, healing, resource gathering, tree cutting, and chest stockpiling.
- Basic and crafting-table recipes with situation-aware tool switching.
- Door opening, swimming, terrain assistance, camp preparation, and sleeping.


## Debug HUD

Look directly at an NPC to show its server-authoritative AI, movement, equipment, and
inventory state. Press `X` while looking at it to pin or unpin the panel.

## Development

Run `./gradlew runClient` for an integrated client or `./gradlew runServer` for a dedicated
development server. Build distributable jars with `./gradlew build`.

Minecraft 26.2, Java 25, Fabric Loader 0.19.3, and Fabric API are currently required.

## License

MIT
