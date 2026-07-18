package npc2.npc2.ai.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.util.ReachableApproach;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Throttled crafting-table discovery with reservations to avoid crowding one side. */
public final class CraftingStations {
    private static final Map<Key, UUID> RESERVATIONS = new HashMap<>();

    private CraftingStations() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, int radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        BlockPos origin = npc.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.withinManhattan(origin, radius, 6, radius)) {
            if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE) && isAvailable(npc, pos))
                candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos pos : candidates) {
            Vec3 approach = ReachableApproach.beside(npc, pos);
            if (approach != null) return new Target(pos, approach);
        }
        return null;
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        Key key = new Key(level.dimension(), target.tablePos);
        UUID owner = RESERVATIONS.get(key);
        if (owner != null && !owner.equals(npc.getUUID())) return false;
        release(npc);
        RESERVATIONS.put(key, npc.getUUID());
        return true;
    }

    public static boolean isUsable(FakeNpcEntity npc, Target target) {
        return npc.level().getBlockState(target.tablePos).is(Blocks.CRAFTING_TABLE)
                && isAvailable(npc, target.tablePos);
    }

    public static void release(FakeNpcEntity npc) {
        UUID id = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(id));
    }

    private static boolean isAvailable(FakeNpcEntity npc, BlockPos pos) {
        UUID owner = RESERVATIONS.get(new Key(((ServerLevel)npc.level()).dimension(), pos));
        return owner == null || owner.equals(npc.getUUID());
    }

    private static void prune(ServerLevel level) {
        RESERVATIONS.entrySet().removeIf(entry -> entry.getKey().dimension.equals(level.dimension())
                && (!level.getBlockState(entry.getKey().pos).is(Blocks.CRAFTING_TABLE)
                || !(level.getEntity(entry.getValue()) instanceof FakeNpcEntity owner)
                || !owner.isAlive()));
    }

    public record Target(BlockPos tablePos, Vec3 approachPosition) {
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
