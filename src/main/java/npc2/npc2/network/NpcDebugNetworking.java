package npc2.npc2.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.ai.CoolEntity;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.survival.SurvivalPlanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative, throttled snapshots for the client hover debugger. */
public final class NpcDebugNetworking {
    private static final int PATH_NODE_LIMIT = 128;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private NpcDebugNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(NpcDebugRequestPayload.TYPE, NpcDebugRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NpcDebugSnapshotPayload.TYPE, NpcDebugSnapshotPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NpcDebugRequestPayload.TYPE, (payload, context) -> {
            long now = context.player().level().getGameTime();
            long previous = LAST_REQUEST.getOrDefault(context.player().getUUID(), Long.MIN_VALUE / 2);
            if (now - previous < 5L) return;
            LAST_REQUEST.put(context.player().getUUID(), now);

            if (!(context.player().level().getEntity(payload.entityId()) instanceof CoolEntity npc)
                    || !npc.isAlive()) {
                return;
            }
            ServerPlayNetworking.send(context.player(), createSnapshot(npc));
        });
    }

    private static NpcDebugSnapshotPayload createSnapshot(CoolEntity npc) {
        NpcBrain brain = npc.brain;
        List<String> ai = new ArrayList<>();
        ai.add("State: " + activeState(brain));
        ai.add("Movement owner: " + friendly(brain.movementIntent().name()));
        ai.add("Target: " + (brain.memories.target == null ? "none" : brain.memories.target.getName().getString()));
        ai.add("Plan: " + (brain.memories.returningHome
                ? "return home"
                : brain.memories.plan.shouldGather()
                ? "gather"
                : friendly(brain.memories.plan.action().name()))
                + String.format(Locale.ROOT, "  score %.0f", brain.memories.plan.actionScore()));
        if (brain.memories.resourceTarget != null) {
            ai.add("Resource: " + brain.memories.resourceTarget.kind() + " " + compact(brain.memories.resourceTarget.blockPos()));
        }
        if (brain.memories.resourceSearch != null) {
            ai.add("Search radius: " + brain.memories.resourceSearch.stageRadius());
        }
        if (brain.memories.resourceSurvey != null) {
            ai.add("Survey ring: " + brain.memories.resourceSurvey.currentRing()
                    + "  known: " + brain.memories.knownResourceCount());
        }
        if (!brain.memories.stationPlacementSites.isEmpty()) {
            brain.memories.stationPlacementSites.forEach((kind, site) ->
                    ai.add("Place " + friendly(kind.name()) + ": " + compact(site.blockPos())));
        }
        if (!brain.memories.stationRelocationTargets.isEmpty()) {
            brain.memories.stationRelocationTargets.forEach((kind, target) ->
                    ai.add("Relocating for " + friendly(kind.name()) + ": "
                            + compact(BlockPos.containing(target))));
        }
        if (brain.memories.lootTarget != null) ai.add("Loot: " + brain.memories.lootTarget.getItem().getHoverName().getString());
        if (brain.memories.chestTarget != null) ai.add("Chest: " + compact(brain.memories.chestTarget));
        if (brain.memories.homeBedPosition != null) ai.add("Home: " + compact(brain.memories.homeBedPosition));
        ai.add("Flags: " + activeFlags(brain));

        Vec3 velocity = npc.getDeltaMovement();
        List<String> movement = new ArrayList<>();
        movement.add(String.format(Locale.ROOT, "Pos %.1f  %.1f  %.1f", npc.getX(), npc.getY(), npc.getZ()));
        movement.add("Dimension " + npc.level().dimension().identifier());
        movement.add(String.format(Locale.ROOT, "Velocity %.2f  %.2f  %.2f", velocity.x, velocity.y, velocity.z));
        movement.add("Ground " + yesNo(npc.onGround()) + "  Water " + yesNo(npc.isInWater())
                + "  Fire " + yesNo(npc.isOnFire()));
        movement.add("Armor " + npc.getArmorValue() + "  Age " + npc.tickCount + " ticks");
        Path path = npc.getNpcNavigation().getPath();
        boolean workingInRange = isWorkingInRange(brain);
        String pathState;
        if (workingInRange) {
            pathState = "Path: arrived (working)";
        } else if (!brain.memories.plan.shouldGather()
                && brain.memories.plan.action() == SurvivalPlanner.Action.HAND_CRAFT) {
            pathState = "Path: no movement required";
        } else if (path == null) {
            pathState = brain.memories.resourceTarget != null
                    ? "Path: retrying resource route"
                    : isAcquiringWorkTarget(brain) ? "Path: acquiring work target" : "Path: none";
        } else {
            pathState = "Path: " + path.getNextNodeIndex() + "/" + path.getNodeCount()
                    + (npc.getNpcNavigation().pathActuallyReachesTarget() ? " reachable" : " invalid-end");
        }
        movement.add(pathState + "  stall " + npc.getNpcNavigation().getNoProgressTicks()
                + "/" + npc.getNpcNavigation().getPartialPathTicks()
                + "  retry " + npc.getNpcNavigation().getConsecutiveFailures()
                + (npc.getNpcNavigation().needsRecovery() ? " !" : ""));

        List<NpcDebugSnapshotPayload.NeedEntry> needs = new ArrayList<>();
        for (SurvivalPlanner.Need need : brain.memories.plan.rankedNeeds()) {
            float confidence = (float)brain.memories.resourceAvailability(
                    brain.npc, need.resource()).confidence();
            needs.add(new NpcDebugSnapshotPayload.NeedEntry(
                    new ItemStack(needIcon(need.resource())), friendly(need.resource().name()),
                    need.current(), need.target(), need.score(), confidence, need.reason()));
        }
        List<ItemStack> statusIcons = statusIcons(brain);
        PathSnapshot pathSnapshot = pathSnapshot(path,
                path != null && npc.getNpcNavigation().pathActuallyReachesTarget());

        List<ItemStack> equipment = List.of(
                npc.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                npc.getItemBySlot(EquipmentSlot.OFFHAND).copy(),
                npc.getItemBySlot(EquipmentSlot.HEAD).copy(),
                npc.getItemBySlot(EquipmentSlot.CHEST).copy(),
                npc.getItemBySlot(EquipmentSlot.LEGS).copy(),
                npc.getItemBySlot(EquipmentSlot.FEET).copy()
        );
        List<ItemStack> inventory = new ArrayList<>(npc.getInventory().getContainerSize());
        for (ItemStack stack : npc.getInventory()) inventory.add(stack.copy());

        return new NpcDebugSnapshotPayload(
                npc.getId(), npc.getDisplayName().getString(), npc.getHealth(), npc.getMaxHealth(),
                ai, movement, needs, statusIcons, equipment, inventory,
                pathSnapshot.nodes(), pathSnapshot.nextNode(), pathSnapshot.reachable()
        );
    }

    private static String activeState(NpcBrain brain) {
        if (brain.npc.isSleeping()) {
            return brain.memories.floorSleeping ? "sleeping on floor" : "sleeping";
        }
        return switch (brain.movementIntent()) {
            case BLOCKING -> "blocking";
            case RETREATING -> "retreating";
            case WATER_ESCAPE -> "escaping water";
            case HOME -> brain.memories.target == null ? "returning home" : "clearing home";
            case COMBAT -> brain.memories.hunting ? "hunting" : "combat";
            case BED -> "seeking bed";
            case LOOT -> "collecting loot";
            case CHEST -> "looting chest";
            case DEPOSIT -> "depositing";
            case STATION_SITE -> "finding workstation site";
            case CRAFTING_TABLE -> "crafting";
            case FURNACE -> "smelting";
            case RESOURCE -> "gathering";
            case RESOURCE_EXPLORATION -> "exploring resources";
            case RESOURCE_SEARCH -> "searching resources";
            case PRODUCTION -> brain.memories.plan.action() == SurvivalPlanner.Action.HAND_CRAFT
                    ? "hand crafting" : "preparing production";
            case WANDER -> "wandering";
            case IDLE -> "idle";
            case SLEEPING -> "sleeping";
        };
    }

    private static String activeFlags(NpcBrain brain) {
        List<String> flags = new ArrayList<>();
        if (brain.memories.blockingMob) flags.add("guard");
        if (brain.memories.retreating) flags.add("retreat");
        if (brain.memories.hunting) flags.add("hunt");
        if (brain.memories.seekingLoot) flags.add("loot");
        if (brain.memories.seekingChest) flags.add("chest");
        if (brain.memories.depositing) flags.add("store");
        if (brain.memories.seekingCraftingTable) flags.add("craft");
        if (brain.memories.processingFurnace) flags.add("smelt");
        if (brain.memories.gatheringResource) flags.add("gather");
        if (brain.memories.exploringForResources) flags.add("explore");
        if (brain.memories.returningHome) flags.add("home");
        if (!brain.memories.returningHome && brain.memories.plan.shouldGather()
                && !brain.memories.gatheringResource) flags.add("resource-search");
        return flags.isEmpty() ? "none" : String.join(", ", flags);
    }

    private static boolean isWorkingInRange(NpcBrain brain) {
        if (brain.memories.resourceTarget != null
                && brain.npc.distanceToSqr(brain.memories.resourceTarget.approachPosition()) <= 2.25D) return true;
        if (brain.memories.craftingTableTarget != null
                && brain.npc.distanceToSqr(brain.memories.craftingTableTarget.approachPosition()) <= 2.25D) return true;
        if (brain.memories.furnaceTarget != null
                && brain.npc.distanceToSqr(brain.memories.furnaceTarget.approachPosition()) <= 2.25D) return true;
        if (brain.memories.bedTarget != null
                && brain.npc.distanceToSqr(brain.memories.bedTarget.approachPosition()) <= 2.25D) return true;
        if (brain.memories.chestLootTarget != null
                && brain.npc.distanceToSqr(brain.memories.chestLootTarget.approachPosition()) <= 2.25D) return true;
        return brain.memories.chestDepositTarget != null
                && brain.npc.distanceToSqr(brain.memories.chestDepositTarget.approachPosition()) <= 2.25D;
    }

    private static boolean isAcquiringWorkTarget(NpcBrain brain) {
        return brain.memories.returningHome
                || brain.memories.plan.shouldGather()
                || brain.memories.plan.action() == SurvivalPlanner.Action.CRAFTING_TABLE
                || brain.memories.plan.action() == SurvivalPlanner.Action.FURNACE
                || (brain.memories.gatheringResource && brain.memories.resourceTarget == null)
                || (brain.memories.seekingCraftingTable && brain.memories.craftingTableTarget == null)
                || (brain.memories.processingFurnace && brain.memories.furnaceTarget == null)
                || (brain.memories.seekingBed && brain.memories.bedTarget == null)
                || (brain.memories.depositing && brain.memories.chestDepositTarget == null)
                || (brain.memories.seekingChest && brain.memories.chestLootTarget == null)
                || (brain.memories.seekingLoot && brain.memories.lootTarget == null);
    }

    private static String compact(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String friendly(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static List<ItemStack> statusIcons(NpcBrain brain) {
        List<ItemStack> icons = new ArrayList<>();
        switch (brain.movementIntent()) {
            case SLEEPING, BED -> icons.add(new ItemStack(Items.BED.red()));
            case BLOCKING -> icons.add(new ItemStack(Items.SHIELD));
            case RETREATING -> icons.add(new ItemStack(Items.GOLDEN_APPLE));
            case WATER_ESCAPE -> icons.add(new ItemStack(Items.WATER_BUCKET));
            case HOME -> icons.add(new ItemStack(Items.COMPASS));
            case COMBAT -> icons.add(new ItemStack(Items.IRON_SWORD));
            case LOOT, CHEST, DEPOSIT -> icons.add(new ItemStack(Items.CHEST));
            case STATION_SITE -> icons.add(new ItemStack(
                    brain.memories.stationPlacementSites.containsKey(BlockInteractionStations.Kind.FURNACE)
                            || brain.memories.stationRelocationTargets.containsKey(BlockInteractionStations.Kind.FURNACE)
                            ? Items.FURNACE : Items.CRAFTING_TABLE));
            case CRAFTING_TABLE -> icons.add(new ItemStack(Items.CRAFTING_TABLE));
            case FURNACE -> icons.add(new ItemStack(Items.FURNACE));
            case RESOURCE -> icons.add(new ItemStack(Items.IRON_PICKAXE));
            case RESOURCE_EXPLORATION, RESOURCE_SEARCH, WANDER -> icons.add(new ItemStack(Items.SPYGLASS));
            case PRODUCTION -> icons.add(new ItemStack(Items.CRAFTING_TABLE));
            case IDLE -> icons.add(new ItemStack(Items.CLOCK));
        }
        if (brain.memories.homeBedPosition != null) icons.add(new ItemStack(Items.BED.red()));
        if (brain.memories.target != null) icons.add(new ItemStack(Items.IRON_SWORD));
        return icons;
    }

    private static net.minecraft.world.item.Item needIcon(SurvivalPlanner.Resource resource) {
        return switch (resource) {
            case FOOD -> Items.COOKED_BEEF;
            case LOGS -> Items.OAK_LOG;
            case COBBLESTONE -> Items.COBBLESTONE;
            case SOIL -> Items.DIRT;
            case FUEL -> Items.COAL;
            case IRON_ORE -> Items.RAW_IRON;
            case TORCHES -> Items.TORCH;
            case WOOL -> Items.WOOL.white();
        };
    }

    private static PathSnapshot pathSnapshot(Path path, boolean reachable) {
        if (path == null || path.getNodeCount() == 0) return new PathSnapshot(List.of(), 0, false);
        int firstNode = Math.max(0, path.getNextNodeIndex() - 1);
        int endNode = Math.min(path.getNodeCount(), firstNode + PATH_NODE_LIMIT);
        List<BlockPos> nodes = new ArrayList<>(endNode - firstNode);
        for (int index = firstNode; index < endNode; index++) nodes.add(path.getNodePos(index));
        int nextNode = Math.clamp(path.getNextNodeIndex() - firstNode, 0, nodes.size());
        return new PathSnapshot(List.copyOf(nodes), nextNode, reachable);
    }

    private record PathSnapshot(List<BlockPos> nodes, int nextNode, boolean reachable) {
    }
}
