package npc2.npc2.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.ai.CoolEntity;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.NpcMemories;
import npc2.npc2.ai.survival.SurvivalPlanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative, throttled snapshots for the client hover debugger. */
public final class NpcDebugNetworking {
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
                    || !npc.isAlive() || context.player().distanceToSqr(npc) > 32.0D * 32.0D) {
                return;
            }
            ServerPlayNetworking.send(context.player(), createSnapshot(npc));
        });
    }

    private static NpcDebugSnapshotPayload createSnapshot(CoolEntity npc) {
        NpcBrain brain = npc.brain;
        List<String> ai = new ArrayList<>();
        ai.add("State: " + activeState(brain));
        ai.add("Target: " + (brain.memories.target == null ? "none" : brain.memories.target.getName().getString()));
        ai.add("Plan: " + (brain.memories.returningHome
                ? "return home"
                : brain.memories.plan.shouldGather()
                ? "gather"
                : brain.memories.plan.action().name().toLowerCase(Locale.ROOT)));
        for (SurvivalPlanner.Need need : brain.memories.plan.rankedNeeds().stream().limit(2).toList()) {
            ai.add(String.format(Locale.ROOT, "%s %d/%d  %.0f",
                    need.resource(), need.current(), need.target(), need.score()));
        }
        NpcMemories.UnavailableResource unavailable = brain.memories.unavailableResource(npc);
        if (unavailable != null) {
            ai.add("Unavailable nearby: " + unavailable.resource() + " (retry "
                    + Math.max(1L, (unavailable.ticksRemaining() + 19L) / 20L) + "s)");
        }
        if (brain.memories.resourceTarget != null) {
            ai.add("Resource: " + brain.memories.resourceTarget.kind() + " " + compact(brain.memories.resourceTarget.blockPos()));
        }
        if (brain.memories.lootTarget != null) ai.add("Loot: " + brain.memories.lootTarget.getItem().getHoverName().getString());
        if (brain.memories.chestTarget != null) ai.add("Chest: " + compact(brain.memories.chestTarget));
        if (brain.memories.homeBedPosition != null) ai.add("Home: " + compact(brain.memories.homeBedPosition));
        ai.add("Flags: " + activeFlags(brain));

        Vec3 velocity = npc.getDeltaMovement();
        List<String> movement = new ArrayList<>();
        movement.add(String.format(Locale.ROOT, "Pos %.1f  %.1f  %.1f", npc.getX(), npc.getY(), npc.getZ()));
        movement.add(String.format(Locale.ROOT, "Velocity %.2f  %.2f  %.2f", velocity.x, velocity.y, velocity.z));
        movement.add("Ground " + yesNo(npc.onGround()) + "  Water " + yesNo(npc.isInWater()));
        Path path = npc.getNpcNavigation().getPath();
        boolean workingInRange = isWorkingInRange(brain);
        String pathState;
        if (workingInRange) {
            pathState = "Path: arrived (working)";
        } else if (!brain.memories.plan.shouldGather()
                && brain.memories.plan.action() == SurvivalPlanner.Action.HAND_CRAFT) {
            pathState = "Path: no movement required";
        } else if (path == null) {
            pathState = isAcquiringWorkTarget(brain) ? "Path: acquiring work target" : "Path: none";
        } else {
            pathState = "Path: " + path.getNextNodeIndex() + "/" + path.getNodeCount()
                    + (npc.getNpcNavigation().pathActuallyReachesTarget() ? " reachable" : " invalid-end");
        }
        movement.add(pathState + "  stall " + npc.getNpcNavigation().getNoProgressTicks()
                + "/" + npc.getNpcNavigation().getPartialPathTicks()
                + (npc.getNpcNavigation().needsRecovery() ? " !" : ""));

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
                ai, movement, equipment, inventory
        );
    }

    private static String activeState(NpcBrain brain) {
        if (brain.npc.isSleeping()) {
            return brain.memories.floorSleeping ? "sleeping on floor" : "sleeping";
        }
        if (brain.memories.blockingMob) return "blocking";
        if (brain.memories.retreating) return "retreating";
        if (brain.memories.floating) return "swimming up";
        if (brain.memories.hunting) return "hunting";
        if (brain.memories.seekingLoot) return "collecting loot";
        if (brain.memories.seekingChest) return "looting chest";
        if (brain.memories.depositing) return "depositing";
        if (brain.memories.seekingCraftingTable) return "crafting";
        if (brain.memories.processingFurnace) return "smelting";
        if (brain.memories.gatheringResource) return "gathering";
        if (brain.memories.returningHome && brain.memories.target != null) return "clearing home";
        if (brain.memories.returningHome) return "returning home";
        if (brain.memories.seekingBed) return "seeking bed";
        if (brain.memories.target != null) return "combat";
        if (brain.memories.plan.shouldGather()) return "searching resources";
        if (brain.memories.plan.action() == SurvivalPlanner.Action.HAND_CRAFT) return "hand crafting";
        if (brain.memories.plan.action() == SurvivalPlanner.Action.CRAFTING_TABLE) return "seeking crafting table";
        if (brain.memories.plan.action() == SurvivalPlanner.Action.FURNACE) return "seeking furnace";
        if (brain.memories.wanderTarget != null) return "wandering";
        return "idle";
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
}
