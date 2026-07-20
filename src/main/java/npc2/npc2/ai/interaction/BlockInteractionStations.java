package npc2.npc2.ai.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.NpcMemories;
import npc2.npc2.ai.util.ReachableApproach;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared discovery and reservation service for blocks an NPC operates while standing beside them. */
public final class BlockInteractionStations {
    private static final Map<Key, UUID> RESERVATIONS = new HashMap<>();

    private BlockInteractionStations() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, Kind kind, int radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        NpcMemories.RememberedStation known = npc.getMemories().knownStations.get(kind);
        Target remembered = known != null && known.dimension().equals(level.dimension())
                && level.hasChunkAt(known.target().blockPos) ? known.target() : null;
        if (remembered != null && isUsable(npc, remembered)) {
            Vec3 approach = ReachableApproach.beside(npc, remembered.blockPos);
            if (approach != null) return new Target(kind, remembered.blockPos, approach);
        }
        // A remembered block is useful to planning only while it can produce an
        // interaction target. Keeping an unreachable memory suppresses crafting a
        // replacement station and leaves the NPC in a permanent search state.
        if (remembered != null) npc.getMemories().knownStations.remove(kind);
        BlockPos origin = npc.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.withinManhattan(origin, radius, 6, radius)) {
            if (level.hasChunkAt(pos) && kind.matches(level.getBlockState(pos)) && isAvailable(npc, kind, pos)) {
                candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos pos : candidates) {
            Vec3 approach = ReachableApproach.beside(npc, pos);
            if (approach != null) return new Target(kind, pos, approach);
        }
        return null;
    }

    public static boolean hasKnownTarget(FakeNpcEntity npc, Kind kind) {
        NpcMemories.RememberedStation known = npc.getMemories().knownStations.get(kind);
        return known != null && known.dimension().equals(((ServerLevel)npc.level()).dimension())
                && npc.level().hasChunkAt(known.target().blockPos)
                && kind.matches(npc.level().getBlockState(known.target().blockPos));
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        Key key = new Key(level.dimension(), target.kind, target.blockPos);
        UUID owner = RESERVATIONS.get(key);
        if (owner != null && !owner.equals(npc.getUUID())) return false;
        release(npc, target.kind);
        RESERVATIONS.put(key, npc.getUUID());
        npc.getMemories().knownStations.put(target.kind,
                new NpcMemories.RememberedStation(level.dimension(), target));
        return true;
    }

    public static boolean isUsable(FakeNpcEntity npc, Target target) {
        return target.kind.matches(npc.level().getBlockState(target.blockPos))
                && isAvailable(npc, target.kind, target.blockPos);
    }

    public static void release(FakeNpcEntity npc, Kind kind) {
        UUID id = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(id) && entry.getKey().kind == kind);
    }

    /** Drop both the reservation and the NPC's memory of an unusable station. */
    public static void forget(FakeNpcEntity npc, Kind kind) {
        release(npc, kind);
        npc.getMemories().knownStations.remove(kind);
    }

    public static void releaseAll(FakeNpcEntity npc) {
        UUID id = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(id));
        npc.getMemories().knownStations.clear();
    }

    private static boolean isAvailable(FakeNpcEntity npc, Kind kind, BlockPos pos) {
        Key key = new Key(((ServerLevel)npc.level()).dimension(), kind, pos);
        UUID owner = RESERVATIONS.get(key);
        return owner == null || owner.equals(npc.getUUID());
    }

    private static void prune(ServerLevel level) {
        RESERVATIONS.entrySet().removeIf(entry -> entry.getKey().dimension.equals(level.dimension())
                && (!entry.getKey().kind.matches(level.getBlockState(entry.getKey().blockPos))
                || !(level.getEntity(entry.getValue()) instanceof FakeNpcEntity owner)
                || !owner.isAlive()));
    }

    public enum Kind {
        CRAFTING_TABLE {
            @Override
            public boolean matches(BlockState state) {
                return state.is(Blocks.CRAFTING_TABLE);
            }
        },
        FURNACE {
            @Override
            public boolean matches(BlockState state) {
                return state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE);
            }
        },
        CAMPFIRE {
            @Override
            public boolean matches(BlockState state) {
                return state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
            }
        };

        public abstract boolean matches(BlockState state);
    }

    public record Target(Kind kind, BlockPos blockPos, Vec3 approachPosition) {
    }

    private record Key(ResourceKey<Level> dimension, Kind kind, BlockPos blockPos) {
    }

}
