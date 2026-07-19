package npc2.npc2.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread-only claims for desirable ground loot.
 *
 * A single item may only attract one NPC. Items dropped into the same block are
 * assigned different approach slots around the pile, preventing every path from
 * terminating in the same suffocation-prone block.
 */
public final class LootReservations {
    private static final int APPROACH_SLOT_COUNT = 16;
    private static final double APPROACH_RADIUS = 1.15D;
    private static final Map<UUID, Claim> CLAIMS = new HashMap<>();

    private LootReservations() {
    }

    public static boolean isAvailable(FakeNpcEntity npc, ItemEntity item) {
        prune((ServerLevel) npc.level(), npc);
        Long avoidedUntil = npc.getMemories().avoidedLootUntil.get(item.getUUID());
        if (avoidedUntil != null && avoidedUntil > npc.level().getGameTime()) return false;
        Claim claim = CLAIMS.get(item.getUUID());
        return claim == null || claim.npcId.equals(npc.getUUID());
    }

    public static boolean isClaimedBy(FakeNpcEntity npc, ItemEntity item) {
        Claim claim = CLAIMS.get(item.getUUID());
        return claim != null && claim.npcId.equals(npc.getUUID());
    }

    public static boolean claim(FakeNpcEntity npc, ItemEntity item) {
        ServerLevel level = (ServerLevel) npc.level();
        prune(level, npc);

        Claim current = CLAIMS.get(item.getUUID());
        if (current != null) {
            return current.npcId.equals(npc.getUUID());
        }

        release(npc);
        BlockPos pilePos = item.blockPosition();
        ResourceKey<Level> dimension = level.dimension();
        Set<Integer> occupiedSlots = new HashSet<>();
        for (Claim claim : CLAIMS.values()) {
            if (claim.dimension.equals(dimension) && claim.pilePos.equals(pilePos)) {
                occupiedSlots.add(claim.approachSlot);
            }
        }

        int slot = 0;
        while (slot < APPROACH_SLOT_COUNT && occupiedSlots.contains(slot)) {
            slot++;
        }
        if (slot == APPROACH_SLOT_COUNT) {
            slot = Math.floorMod(npc.getUUID().hashCode(), APPROACH_SLOT_COUNT);
        }

        CLAIMS.put(item.getUUID(), new Claim(npc.getUUID(), dimension, pilePos, slot));
        return true;
    }

    public static Vec3 getApproachPosition(FakeNpcEntity npc, ItemEntity item) {
        Claim claim = CLAIMS.get(item.getUUID());
        int slot = claim != null && claim.npcId.equals(npc.getUUID())
                ? claim.approachSlot
                : Math.floorMod(npc.getUUID().hashCode(), APPROACH_SLOT_COUNT);
        double angle = Math.PI * 2.0D * slot / APPROACH_SLOT_COUNT;
        Vec3 position = item.position();
        return new Vec3(
                position.x + Math.cos(angle) * APPROACH_RADIUS,
                position.y,
                position.z + Math.sin(angle) * APPROACH_RADIUS
        );
    }

    public static void release(FakeNpcEntity npc) {
        UUID npcId = npc.getUUID();
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().npcId.equals(npcId));
    }

    public static void avoid(FakeNpcEntity npc, ItemEntity item, int ticks) {
        release(npc);
        npc.getMemories().avoidedLootUntil.put(item.getUUID(), npc.level().getGameTime() + ticks);
    }

    private static void prune(ServerLevel level, FakeNpcEntity npc) {
        npc.getMemories().avoidedLootUntil.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime()
                || !(level.getEntity(entry.getKey()) instanceof ItemEntity item) || !item.isAlive());
        Iterator<Map.Entry<UUID, Claim>> iterator = CLAIMS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Claim> entry = iterator.next();
            if (!entry.getValue().dimension.equals(level.dimension())) {
                continue;
            }
            Entity item = level.getEntity(entry.getKey());
            Entity owner = level.getEntity(entry.getValue().npcId);
            if (!(item instanceof ItemEntity itemEntity)
                    || !itemEntity.isAlive()
                    || itemEntity.getItem().isEmpty()
                    || !(owner instanceof FakeNpcEntity ownerNpc)
                    || !ownerNpc.isAlive()) {
                iterator.remove();
            }
        }
    }

    private record Claim(UUID npcId, ResourceKey<Level> dimension, BlockPos pilePos, int approachSlot) {
    }
}
