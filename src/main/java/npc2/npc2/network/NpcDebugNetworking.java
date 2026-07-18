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
        ai.add("Target: " + (brain.target == null ? "none" : brain.target.getName().getString()));
        ai.add("Plan: " + (brain.plan.shouldGather()
                ? "gather"
                : brain.plan.action().name().toLowerCase(Locale.ROOT)));
        for (SurvivalPlanner.Need need : brain.plan.rankedNeeds().stream().limit(2).toList()) {
            ai.add(String.format(Locale.ROOT, "%s %d/%d  %.0f",
                    need.resource(), need.current(), need.target(), need.score()));
        }
        if (brain.resourceTarget != null) {
            ai.add("Resource: " + brain.resourceTarget.kind() + " " + compact(brain.resourceTarget.blockPos()));
        }
        if (brain.lootTarget != null) ai.add("Loot: " + brain.lootTarget.getItem().getHoverName().getString());
        if (brain.chestTarget != null) ai.add("Chest: " + compact(brain.chestTarget));
        ai.add("Flags: " + activeFlags(brain));

        Vec3 velocity = npc.getDeltaMovement();
        List<String> movement = new ArrayList<>();
        movement.add(String.format(Locale.ROOT, "Pos %.1f  %.1f  %.1f", npc.getX(), npc.getY(), npc.getZ()));
        movement.add(String.format(Locale.ROOT, "Velocity %.2f  %.2f  %.2f", velocity.x, velocity.y, velocity.z));
        movement.add("Ground " + yesNo(npc.onGround()) + "  Water " + yesNo(npc.isInWater()));
        Path path = npc.getNpcNavigation().getPath();
        String pathState = path == null ? "Path: none" : "Path: " + path.getNextNodeIndex() + "/" + path.getNodeCount()
                + (npc.getNpcNavigation().pathActuallyReachesTarget() ? " reachable" : " invalid-end");
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
        if (brain.npc.isSleeping()) return "sleeping";
        if (brain.blockingMob) return "blocking";
        if (brain.retreating) return "retreating";
        if (brain.floating) return "swimming up";
        if (brain.hunting) return "hunting";
        if (brain.seekingLoot) return "collecting loot";
        if (brain.seekingChest) return "looting chest";
        if (brain.depositing) return "depositing";
        if (brain.seekingCraftingTable) return "crafting";
        if (brain.processingFurnace) return "smelting";
        if (brain.gatheringResource) return "gathering";
        if (brain.seekingBed) return "seeking bed";
        if (brain.target != null) return "combat";
        if (brain.plan.shouldGather()) return "searching resources";
        if (brain.plan.action() == SurvivalPlanner.Action.HAND_CRAFT) return "hand crafting";
        if (brain.plan.action() == SurvivalPlanner.Action.CRAFTING_TABLE) return "seeking crafting table";
        if (brain.plan.action() == SurvivalPlanner.Action.FURNACE) return "seeking furnace";
        if (brain.wanderTarget != null) return "wandering";
        return "idle";
    }

    private static String activeFlags(NpcBrain brain) {
        List<String> flags = new ArrayList<>();
        if (brain.blockingMob) flags.add("guard");
        if (brain.retreating) flags.add("retreat");
        if (brain.hunting) flags.add("hunt");
        if (brain.seekingLoot) flags.add("loot");
        if (brain.seekingChest) flags.add("chest");
        if (brain.depositing) flags.add("store");
        if (brain.seekingCraftingTable) flags.add("craft");
        if (brain.processingFurnace) flags.add("smelt");
        if (brain.gatheringResource) flags.add("gather");
        if (brain.plan.shouldGather() && !brain.gatheringResource) flags.add("resource-search");
        return flags.isEmpty() ? "none" : String.join(", ", flags);
    }

    private static String compact(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
