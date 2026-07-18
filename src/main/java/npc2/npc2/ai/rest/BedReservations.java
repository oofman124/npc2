package npc2.npc2.ai.rest;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.util.ReachableApproach;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bed discovery and per-NPC reservations. */
public final class BedReservations {
    private static final Map<BedKey, UUID> RESERVATIONS = new HashMap<>();

    private BedReservations() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, int radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        BlockPos origin = npc.blockPosition();
        return level.getPoiManager()
                .findAll(
                        holder -> holder.is(PoiTypes.HOME),
                        pos -> isAvailable(npc, pos) && isUsableBed(level, pos),
                        origin,
                        radius,
                        PoiManager.Occupancy.ANY
                )
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                .map(pos -> createTarget(npc, pos))
                .filter(target -> target != null)
                .map(target -> (Target)target)
                .findFirst()
                .orElse(null);
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        BedKey key = new BedKey(level.dimension(), target.bedPos);
        UUID owner = RESERVATIONS.get(key);
        if (owner != null && !owner.equals(npc.getUUID())) {
            return false;
        }
        release(npc);
        RESERVATIONS.put(key, npc.getUUID());
        return true;
    }

    public static boolean isStillUsable(FakeNpcEntity npc, Target target) {
        return isUsableBed((ServerLevel)npc.level(), target.bedPos)
                && isAvailable(npc, target.bedPos);
    }

    public static void release(FakeNpcEntity npc) {
        UUID npcId = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(npcId));
    }

    private static @Nullable Target createTarget(FakeNpcEntity npc, BlockPos bedPos) {
        Vec3 approach = ReachableApproach.beside(npc, bedPos);
        return approach == null ? null : new Target(bedPos.immutable(), approach);
    }

    private static boolean isUsableBed(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED);
    }

    private static boolean isAvailable(FakeNpcEntity npc, BlockPos pos) {
        ServerLevel level = (ServerLevel)npc.level();
        UUID owner = RESERVATIONS.get(new BedKey(level.dimension(), pos));
        return owner == null || owner.equals(npc.getUUID());
    }

    private static void prune(ServerLevel level) {
        RESERVATIONS.entrySet().removeIf(entry -> {
            BedKey key = entry.getKey();
            if (!key.dimension.equals(level.dimension())) {
                return false;
            }
            return !(level.getEntity(entry.getValue()) instanceof FakeNpcEntity owner)
                    || !owner.isAlive()
                    || !(level.getBlockState(key.pos).getBlock() instanceof BedBlock);
        });
    }

    public record Target(BlockPos bedPos, Vec3 approachPosition) {
    }

    private record BedKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
