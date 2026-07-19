package npc2.npc2.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.survival.SurvivalNeeds;
import npc2.npc2.ai.survival.SurvivalPlanner;
import npc2.npc2.ai.crafting.ToolProgression;
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

    private BlockResourceGathering() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, NpcController controller, int radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level, npc);
        BlockPos origin = npc.blockPosition();
        SurvivalPlanner.Plan plan = SurvivalNeeds.planFor(npc, controller);
        EnumMap<Kind, Double> priorities = priorities(npc, plan);
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
        return SurvivalNeeds.planFor(npc, controller).shouldGather();
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level, npc);
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
                && SurvivalNeeds.planFor(npc, controller).score(target.kind.resource) > 0.0D
                && isAvailable(npc, target.blockPos);
    }

    public static void release(FakeNpcEntity npc) {
        UUID id = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(id));
    }

    public static void avoid(FakeNpcEntity npc, Target target, int ticks) {
        ServerLevel level = (ServerLevel)npc.level();
        release(npc);
        npc.getMemories().avoidedResourceBlocksUntil.put(
                new npc2.npc2.ai.NpcMemories.RememberedBlock(level.dimension(), target.blockPos),
                level.getGameTime() + ticks);
    }

    private static EnumMap<Kind, Double> priorities(FakeNpcEntity npc, SurvivalPlanner.Plan plan) {
        EnumMap<Kind, Double> priorities = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            priorities.put(kind, kind.canGather(npc) ? plan.score(kind.resource) : 0.0D);
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
            BlockPos beside = resource.relative(direction);
            if (isOpenApproach(npc, beside) || isOpenApproach(npc, beside.above())) return true;
        }
        return false;
    }

    private static @Nullable Vec3 findApproach(FakeNpcEntity npc, BlockPos resource) {
        List<BlockPos> approaches = findOpenApproaches(npc, resource);
        approaches.sort(Comparator.comparingDouble(pos -> pos.distSqr(npc.blockPosition())));
        for (BlockPos approach : approaches) {
            if (npc.getNpcNavigation().canReach(approach)) return Vec3.atBottomCenterOf(approach);
        }
        return null;
    }

    /** Supports both wall resources and floor resources approached from atop an adjacent block. */
    private static List<BlockPos> findOpenApproaches(FakeNpcEntity npc, BlockPos resource) {
        List<BlockPos> approaches = new ArrayList<>(8);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = resource.relative(direction);
            addIfOpen(npc, approaches, beside);
            addIfOpen(npc, approaches, beside.above());
        }
        return approaches;
    }

    private static void addIfOpen(FakeNpcEntity npc, List<BlockPos> approaches, BlockPos candidate) {
        if (isOpenApproach(npc, candidate) && !approaches.contains(candidate)) {
            approaches.add(candidate.immutable());
        }
    }

    private static boolean isOpenApproach(FakeNpcEntity npc, BlockPos candidate) {
        return npc.level().getBlockState(candidate).isAir()
                && npc.level().getBlockState(candidate.above()).isAir()
                && !npc.level().getBlockState(candidate.below()).isAir();
    }

    private static boolean isAvailable(FakeNpcEntity npc, BlockPos pos) {
        Key key = new Key(((ServerLevel)npc.level()).dimension(), pos);
        Long avoidedUntil = npc.getMemories().avoidedResourceBlocksUntil.get(
                new npc2.npc2.ai.NpcMemories.RememberedBlock(key.dimension, key.pos));
        if (avoidedUntil != null && avoidedUntil > npc.level().getGameTime()) return false;
        UUID owner = RESERVATIONS.get(key);
        return owner == null || owner.equals(npc.getUUID());
    }

    private static void prune(ServerLevel level, FakeNpcEntity npc) {
        npc.getMemories().avoidedResourceBlocksUntil.entrySet().removeIf(entry ->
                entry.getValue() <= level.getGameTime()
                        || !entry.getKey().dimension().equals(level.dimension()));
        RESERVATIONS.entrySet().removeIf(entry -> entry.getKey().dimension.equals(level.dimension())
                && (!(level.getEntity(entry.getValue()) instanceof FakeNpcEntity owner) || !owner.isAlive()));
    }

    public enum Kind {
        WOOD(SurvivalPlanner.Resource.LOGS, ToolProgression.NONE) {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                BlockState below = npc.level().getBlockState(pos.below());
                return state.is(BlockTags.LOGS) && !below.is(BlockTags.LOGS)
                        && !below.is(BlockTags.LEAVES) && !below.isAir();
            }
        },
        STONE(SurvivalPlanner.Resource.COBBLESTONE, ToolProgression.WOOD) {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE);
            }
        },
        FUEL(SurvivalPlanner.Resource.FUEL, ToolProgression.WOOD) {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
            }
        },
        IRON(SurvivalPlanner.Resource.IRON_ORE, ToolProgression.STONE) {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE);
            }
        },
        DIAMOND(SurvivalPlanner.Resource.DIAMOND, ToolProgression.IRON) {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE);
            }
        },
        SOIL(SurvivalPlanner.Resource.SOIL, ToolProgression.NONE) {
            boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc) {
                return state.is(BlockTags.DIRT);
            }
        };

        private final SurvivalPlanner.Resource resource;
        private final int requiredPickaxeTier;

        Kind(SurvivalPlanner.Resource resource, int requiredPickaxeTier) {
            this.resource = resource;
            this.requiredPickaxeTier = requiredPickaxeTier;
        }

        abstract boolean matches(BlockState state, BlockPos pos, FakeNpcEntity npc);

        boolean canGather(FakeNpcEntity npc) {
            return this.requiredPickaxeTier == ToolProgression.NONE
                    || ToolProgression.pickaxeTier(npc) >= this.requiredPickaxeTier;
        }

        public SurvivalPlanner.Resource resource() {
            return this.resource;
        }
    }

    public record Target(BlockPos blockPos, Vec3 approachPosition, Kind kind) {
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Candidate(BlockPos blockPos, Kind kind, double value) {
    }
}
