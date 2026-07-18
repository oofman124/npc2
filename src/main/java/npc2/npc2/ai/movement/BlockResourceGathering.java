package npc2.npc2.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.survival.SurvivalNeeds;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/** Generic surface-resource discovery driven by stock deficits rather than one resource-specific node. */
public final class BlockResourceGathering {
    private static final int PATHFINDING_SHORTLIST_SIZE = 12;
    private static final Map<Key, UUID> RESERVATIONS = new HashMap<>();
    private static final Map<AvoidKey, Long> AVOID_UNTIL = new HashMap<>();

    private BlockResourceGathering() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, NpcController controller, int radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        BlockPos origin = npc.blockPosition();
        EnumMap<Kind, Double> priorities = priorities(npc, controller);
        PriorityQueue<Candidate> shortlist = new PriorityQueue<>(
                Comparator.comparingDouble(Candidate::value));

        // Scanning block states is cheap; constructing Minecraft paths is not. Rank a small
        // shortlist first so a resource search performs at most a handful of path searches.
        for (BlockPos pos : BlockPos.withinManhattan(origin, radius, 8, radius)) {
            BlockState state = level.getBlockState(pos);
            Kind kind = classify(npc, state, pos, priorities);
            if (kind == null || !isAvailable(npc, pos) || !hasOpenApproach(npc, pos)) continue;
            double value = priorities.get(kind) * 1000.0D - pos.distSqr(origin);
            Candidate candidate = new Candidate(pos.immutable(), kind, value);
            if (shortlist.size() < PATHFINDING_SHORTLIST_SIZE) {
                shortlist.add(candidate);
            } else if (value > shortlist.peek().value()) {
                shortlist.poll();
                shortlist.add(candidate);
            }
        }

        List<Candidate> ranked = new ArrayList<>(shortlist);
        ranked.sort(Comparator.comparingDouble(Candidate::value).reversed());
        for (Candidate candidate : ranked) {
            Vec3 approach = findApproach(npc, candidate.blockPos);
            if (approach != null) return new Target(candidate.blockPos, approach, candidate.kind);
        }
        return null;
    }

    public static boolean needsResources(FakeNpcEntity npc, NpcController controller) {
        return SurvivalNeeds.materials(npc, controller) > 0.0D;
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        Key key = new Key(level.dimension(), target.blockPos);
        UUID owner = RESERVATIONS.get(key);
        if (owner != null && !owner.equals(npc.getUUID())) return false;
        release(npc);
        RESERVATIONS.put(key, npc.getUUID());
        return true;
    }

    public static boolean isUsable(FakeNpcEntity npc, NpcController controller, Target target) {
        return target.kind.canGather(npc)
                && target.kind.matches(npc.level().getBlockState(target.blockPos), target.blockPos, npc)
                && target.kind.priority(npc, controller) > 0.0D && isAvailable(npc, target.blockPos);
    }

    public static void release(FakeNpcEntity npc) {
        UUID id = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(id));
    }

    public static void avoid(FakeNpcEntity npc, Target target, int ticks) {
        ServerLevel level = (ServerLevel)npc.level();
        release(npc);
        AVOID_UNTIL.put(new AvoidKey(npc.getUUID(), level.dimension(), target.blockPos), level.getGameTime() + ticks);
    }

    private static EnumMap<Kind, Double> priorities(FakeNpcEntity npc, NpcController controller) {
        EnumMap<Kind, Double> priorities = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            priorities.put(kind, kind.canGather(npc) ? kind.priority(npc, controller) : 0.0D);
        }
        return priorities;
    }

    private static @Nullable Kind classify(FakeNpcEntity npc, BlockState state, BlockPos pos,
                                            EnumMap<Kind, Double> priorities) {
        Kind best = null;
        double priority = 0.0D;
        for (Kind kind : Kind.values()) {
            double candidate = priorities.get(kind);
            if (candidate > priority && kind.matches(state, pos, npc)) {
                best = kind;
                priority = candidate;
            }
        }
        return best;
    }

    private static boolean hasOpenApproach(FakeNpcEntity npc, BlockPos resource) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos approach = resource.relative(direction);
            if (npc.level().getBlockState(approach).isAir()
                    && npc.level().getBlockState(approach.above()).isAir()) return true;
        }
        return false;
    }

    private static @Nullable Vec3 findApproach(FakeNpcEntity npc, BlockPos resource) {
        List<BlockPos> approaches = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = resource.relative(direction);
            if (npc.level().getBlockState(candidate).isAir()
                    && npc.level().getBlockState(candidate.above()).isAir()) approaches.add(candidate);
        }
        approaches.sort(Comparator.comparingDouble(pos -> pos.distSqr(npc.blockPosition())));
        for (BlockPos approach : approaches) {
            if (npc.getNpcNavigation().canReach(approach)) return Vec3.atBottomCenterOf(approach);
        }
        return null;
    }

    private static boolean hasPickaxe(FakeNpcEntity npc) {
        if (npc.getMainHandItem().is(ItemTags.PICKAXES)) return true;
        for (ItemStack stack : npc.getInventory()) if (stack.is(ItemTags.PICKAXES)) return true;
        return false;
    }

    private static boolean isAvailable(FakeNpcEntity npc, BlockPos pos) {
        Key key = new Key(((ServerLevel)npc.level()).dimension(), pos);
        Long avoidedUntil = AVOID_UNTIL.get(new AvoidKey(npc.getUUID(), key.dimension, key.pos));
        if (avoidedUntil != null && avoidedUntil > npc.level().getGameTime()) return false;
        UUID owner = RESERVATIONS.get(key);
        return owner == null || owner.equals(npc.getUUID());
    }

    private static void prune(ServerLevel level) {
        AVOID_UNTIL.entrySet().removeIf(entry -> entry.getKey().dimension.equals(level.dimension())
                && (entry.getValue() <= level.getGameTime()
                || !(level.getEntity(entry.getKey().npcId) instanceof FakeNpcEntity owner) || !owner.isAlive()));
        RESERVATIONS.entrySet().removeIf(entry -> entry.getKey().dimension.equals(level.dimension())
                && (!(level.getEntity(entry.getValue()) instanceof FakeNpcEntity owner) || !owner.isAlive()));
    }

    public enum Kind {
        WOOD {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                BlockState below = npc.level().getBlockState(pos.below());
                return state.is(BlockTags.LOGS) && !below.is(BlockTags.LOGS)
                        && !below.is(BlockTags.LEAVES) && !below.isAir();
            }
            double priority(FakeNpcEntity npc, NpcController controller) {
                int count = controller.countInventoryTag(npc.getInventory(), ItemTags.LOGS);
                return Math.max(0, SurvivalNeeds.LOG_TARGET - count) * 45.0D / SurvivalNeeds.LOG_TARGET;
            }
        },
        STONE {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE);
            }
            double priority(FakeNpcEntity npc, NpcController controller) {
                int count = npc.getInventory().countItem(Items.COBBLESTONE);
                return Math.max(0, SurvivalNeeds.COBBLESTONE_TARGET - count) * 40.0D / SurvivalNeeds.COBBLESTONE_TARGET;
            }
            boolean requiresPickaxe() {
                return true;
            }
        },
        FUEL {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
            }
            double priority(FakeNpcEntity npc, NpcController controller) {
                int count = SurvivalNeeds.countFuel(npc.getInventory());
                return Math.max(0, SurvivalNeeds.FUEL_TARGET - count) * 32.0D / SurvivalNeeds.FUEL_TARGET;
            }
            boolean requiresPickaxe() {
                return true;
            }
        },
        SOIL {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(BlockTags.DIRT);
            }
            double priority(FakeNpcEntity npc, NpcController controller) {
                int count = npc.getInventory().countItem(Items.DIRT);
                return Math.max(0, SurvivalNeeds.SOIL_TARGET - count) * 24.0D / SurvivalNeeds.SOIL_TARGET;
            }
        };

        abstract boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc);
        abstract double priority(FakeNpcEntity npc, NpcController controller);

        boolean requiresPickaxe() {
            return false;
        }

        boolean canGather(FakeNpcEntity npc) {
            return !requiresPickaxe() || hasPickaxe(npc);
        }
    }

    public record Target(BlockPos blockPos, Vec3 approachPosition, Kind kind) {
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record AvoidKey(UUID npcId, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Candidate(BlockPos blockPos, Kind kind, double value) {
    }
}
