package npc2.npc2.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.survival.SurvivalNeeds;
import npc2.npc2.ai.survival.SurvivalPlanner;
import npc2.npc2.ai.crafting.ToolProgression;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/** Generic surface-resource discovery driven by stock deficits rather than one resource-specific node. */
public final class BlockResourceGathering {
    private static final int PATHFINDING_SHORTLIST_SIZE_PER_RESOURCE = 4;
    private static final int PRIMARY_SEARCH_RADIUS = 48;
    private static final int SEARCH_VERTICAL_RADIUS = 12;
    private static final int EXPLORATION_STEP_RADIUS = 30;
    private static final double DIRECT_PATH_RADIUS_SQR = 48.0D * 48.0D;
    private static final double REMEMBERED_RESOURCE_RADIUS_SQR = 256.0D * 256.0D;
    private static final int OUTSIDE_AIR_SEARCH_RADIUS = 16;
    private static final int OUTSIDE_AIR_NODE_BUDGET = 512;
    private static final Map<Key, UUID> RESERVATIONS = new HashMap<>();

    private BlockResourceGathering() {
    }

    public static Search beginSearch(FakeNpcEntity npc, NpcController controller, int radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level, npc);
        BlockPos origin = npc.blockPosition();
        SurvivalPlanner.Plan plan = SurvivalNeeds.planFor(npc, controller);
        EnumMap<Kind, Double> priorities = priorities(npc, plan);
        Search search = new Search(level, origin.immutable(), priorities, radius);
        seedRememberedResources(npc, search);
        return search;
    }

    /** Advances a search by a bounded number of block checks and at most one path calculation. */
    public static SearchProgress continueSearch(FakeNpcEntity npc, Search search, int blockBudget) {
        ServerLevel level = (ServerLevel)npc.level();
        if (!search.dimension.equals(level.dimension())) return new SearchProgress(null, null, true);

        int checked = 0;
        while (search.positions != null && checked++ < blockBudget && search.positions.hasNext()) {
            BlockPos pos = search.positions.next();
            BlockState state = level.getBlockState(pos);
            Kind kind = classify(npc, state, pos, search.priorities);
            if (kind == null || !isAvailable(npc, pos) || !hasOutsideApproach(npc, pos)) continue;
            npc.getMemories().rememberResource(npc, kind.resource, pos);
            search.addCandidate(pos, kind);
        }

        if (search.positions != null && search.positions.hasNext()) {
            return new SearchProgress(null, null, false);
        }
        if (search.ranked == null) {
            search.positions = null;
            search.ranked = new ArrayList<>();
            search.shortlists.values().forEach(search.ranked::addAll);
            search.ranked.sort(Comparator
                    .<Candidate>comparingDouble(candidate -> search.priorities.get(candidate.kind)).reversed()
                    .thenComparingDouble(candidate -> candidate.blockPos.distSqr(search.origin)));
        }
        while (search.rankedIndex < search.ranked.size()) {
            Candidate candidate = search.ranked.get(search.rankedIndex++);
            if (!candidate.kind.matches(level.getBlockState(candidate.blockPos), candidate.blockPos, npc)
                    || !isAvailable(npc, candidate.blockPos)
                    || !hasOutsideApproach(npc, candidate.blockPos)) continue;
            Vec3 approach = findApproach(npc, candidate.blockPos);
            if (approach != null) {
                npc.getMemories().recordResourceEvidence(npc, candidate.kind.resource);
                return new SearchProgress(
                        new Target(candidate.blockPos, approach, candidate.kind), null,
                        search.rankedIndex >= search.ranked.size());
            }
            search.addExplorationCandidate(candidate);
            // Path construction is substantially more expensive than checking a
            // block. Try every other primary candidate on subsequent NPC ticks
            // before considering movement toward this inaccessible one.
            return new SearchProgress(null, null, false);
        }
        search.recordPrimaryMisses(npc);
        SurvivalPlanner.Plan adjustedPlan = SurvivalPlanner.create(npc, npc.getController());
        if (!adjustedPlan.shouldGather()) {
            return new SearchProgress(null, null, true);
        }
        if (!search.explorationRanked) {
            search.explorationCandidates.sort(Comparator
                    .<Candidate>comparingDouble(value -> adjustedPlan.score(value.kind.resource)).reversed()
                    .thenComparingDouble(value -> value.blockPos.distSqr(search.origin)));
            search.explorationRanked = true;
        }
        while (search.explorationIndex < search.explorationCandidates.size()) {
            Candidate candidate = search.explorationCandidates.get(search.explorationIndex++);
            if (adjustedPlan.score(candidate.kind.resource) <= 0.0D
                    || !candidate.kind.matches(level.getBlockState(candidate.blockPos), candidate.blockPos, npc)
                    || !isAvailable(npc, candidate.blockPos)
                    || !hasOutsideApproach(npc, candidate.blockPos)) continue;
            Vec3 explorationTarget = findExplorationTarget(npc, candidate.blockPos);
            if (explorationTarget != null) {
                return new SearchProgress(null, explorationTarget, true);
            }
            // Limit fallback route construction to one candidate per scheduled
            // search tick just like primary path construction.
            return new SearchProgress(null, null, false);
        }
        return new SearchProgress(null, null, true);
    }

    private static @Nullable Vec3 findExplorationTarget(FakeNpcEntity npc, BlockPos resource) {
        Vec3 destination = Vec3.atCenterOf(resource);
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 candidate = LandRandomPos.getPosTowards(
                    npc, EXPLORATION_STEP_RADIUS, SEARCH_VERTICAL_RADIUS, destination);
            if (candidate != null
                    && npc.distanceToSqr(candidate) > 9.0D
                    && npc.getNpcNavigation().canReach(BlockPos.containing(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static void seedRememberedResources(FakeNpcEntity npc, Search search) {
        ServerLevel level = (ServerLevel)npc.level();
        for (Kind kind : Kind.values()) {
            if (search.priorities.get(kind) <= 0.0D) continue;
            for (npc2.npc2.ai.NpcMemories.RememberedBlock remembered :
                    npc.getMemories().knownResources(kind.resource)) {
                if (!remembered.dimension().equals(level.dimension())
                        || remembered.pos().distSqr(search.origin) > REMEMBERED_RESOURCE_RADIUS_SQR
                        || !level.isLoaded(remembered.pos())) continue;
                BlockState state = level.getBlockState(remembered.pos());
                if (!kind.matches(state, remembered.pos(), npc)) {
                    npc.getMemories().forgetResource(kind.resource, level.dimension(), remembered.pos());
                    continue;
                }
                if (isAvailable(npc, remembered.pos()) && hasOutsideApproach(npc, remembered.pos())) {
                    Candidate candidate = search.candidate(remembered.pos(), kind);
                    if (remembered.pos().distSqr(search.origin) <= DIRECT_PATH_RADIUS_SQR) {
                        search.addCandidate(candidate);
                    } else {
                        search.addExplorationCandidate(candidate);
                    }
                }
            }
        }
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
                && targetStillExists(npc, target)
                && SurvivalNeeds.planFor(npc, controller).score(target.kind.resource) > 0.0D
                && isAvailable(npc, target.blockPos);
    }

    /**
     * Outside-air accessibility is a discovery-time constraint. Re-running that
     * bounded flood fill while following an already validated target can produce
     * a transient false negative around cave mouths and makes the NPC discard a
     * perfectly good claim. Route recovery owns live accessibility after selection.
     */
    public static boolean targetStillExists(FakeNpcEntity npc, Target target) {
        return target.kind.matches(npc.level().getBlockState(target.blockPos), target.blockPos, npc);
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
            // Retain every currently useful, gatherable resource. Each kind owns a
            // separate shortlist, so abundant dirt cannot crowd logs or ores out.
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

    public static @Nullable Kind identify(FakeNpcEntity npc, BlockState state, BlockPos pos) {
        for (Kind kind : Kind.values()) {
            if (kind.matches(state, pos, npc)) return kind;
        }
        return null;
    }

    static boolean isDiscoverable(FakeNpcEntity npc, BlockPos pos) {
        return hasOutsideApproach(npc, pos);
    }

    private static boolean hasOutsideApproach(FakeNpcEntity npc, BlockPos resource) {
        for (BlockPos approach : findOpenApproaches(npc, resource)) {
            if (connectsToOutsideAir(npc, approach)) return true;
        }
        return false;
    }

    private static boolean connectsToOutsideAir(FakeNpcEntity npc, BlockPos start) {
        if (isOutsideAir(npc, start)) return true;
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(start.immutable());
        visited.add(start.immutable());
        int checked = 0;
        while (!open.isEmpty() && checked++ < OUTSIDE_AIR_NODE_BUDGET) {
            BlockPos current = open.removeFirst();
            if (isOutsideAir(npc, current)) return true;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - start.getX()) > OUTSIDE_AIR_SEARCH_RADIUS
                        || Math.abs(next.getY() - start.getY()) > OUTSIDE_AIR_SEARCH_RADIUS
                        || Math.abs(next.getZ() - start.getZ()) > OUTSIDE_AIR_SEARCH_RADIUS
                        || visited.contains(next)
                        || !npc.level().isLoaded(next)
                        || !isAirPassage(npc, next)) continue;
                BlockPos remembered = next.immutable();
                visited.add(remembered);
                open.addLast(remembered);
            }
        }
        return false;
    }

    private static boolean isOutsideAir(FakeNpcEntity npc, BlockPos pos) {
        if (npc.level().canSeeSky(pos) || npc.level().canSeeSky(pos.above())) return true;
        // MOTION_BLOCKING_NO_LEAVES treats the open space beneath a forest canopy
        // as surface air while still rejecting caves beneath solid terrain.
        int surface = npc.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos.getX(), pos.getZ());
        return pos.getY() >= surface - 1;
    }

    private static boolean isAirPassage(FakeNpcEntity npc, BlockPos pos) {
        return npc.level().getFluidState(pos).isEmpty()
                && npc.level().getFluidState(pos.above()).isEmpty()
                && canOccupy(npc, pos)
                && canOccupy(npc, pos.above());
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
        // Vines, grass, and other collision-free blocks are valid occupancy space.
        // Requiring literal air made vine-covered jungle trunks impossible to approach.
        return canOccupy(npc, candidate)
                && canOccupy(npc, candidate.above())
                && !canOccupy(npc, candidate.below());
    }

    private static boolean canOccupy(FakeNpcEntity npc, BlockPos pos) {
        return npc.level().getBlockState(pos).getCollisionShape(npc.level(), pos).isEmpty();
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
                if (!state.is(BlockTags.LOGS)) return false;
                BlockState below = npc.level().getBlockState(pos.below());
                return !below.is(BlockTags.LOGS)
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

    public record SearchProgress(@Nullable Target target, @Nullable Vec3 explorationTarget, boolean complete) {
    }

    public static final class Search {
        private final ResourceKey<Level> dimension;
        private final BlockPos origin;
        private final EnumMap<Kind, Double> priorities;
        private final EnumMap<Kind, PriorityQueue<Candidate>> shortlists = new EnumMap<>(Kind.class);
        private final List<Candidate> explorationCandidates = new ArrayList<>();
        private final EnumSet<SurvivalPlanner.Resource> attemptedResources =
                EnumSet.noneOf(SurvivalPlanner.Resource.class);
        private int stageRadius;
        private @Nullable Iterator<BlockPos> positions;
        private @Nullable List<Candidate> ranked;
        private int rankedIndex;
        private int explorationIndex;
        private boolean explorationRanked;
        private boolean primaryMissesRecorded;

        private final ServerLevel level;

        private Search(ServerLevel level, BlockPos origin,
                       EnumMap<Kind, Double> priorities, int configuredRadius) {
            this.level = level;
            this.dimension = level.dimension();
            this.origin = origin;
            this.priorities = priorities;
            this.stageRadius = Math.max(16, Math.min(PRIMARY_SEARCH_RADIUS, configuredRadius));
            this.positions = positionsFor(this.stageRadius);
            for (Kind kind : Kind.values()) {
                if (priorities.get(kind) > 0.0D) {
                    this.shortlists.put(kind, new PriorityQueue<>(Comparator.comparingDouble(Candidate::value)));
                    this.attemptedResources.add(kind.resource);
                }
            }
        }

        private Iterator<BlockPos> positionsFor(int radius) {
            return new LoadedChunkRingIterator(
                    this.level, this.origin, 0, radius, SEARCH_VERTICAL_RADIUS);
        }

        public int stageRadius() {
            return this.stageRadius;
        }

        private void addCandidate(BlockPos pos, Kind kind) {
            addCandidate(candidate(pos, kind));
        }

        private Candidate candidate(BlockPos pos, Kind kind) {
            double value = this.priorities.get(kind) * 1000.0D - pos.distSqr(this.origin);
            return new Candidate(pos.immutable(), kind, value);
        }

        private void addCandidate(Candidate candidate) {
            Kind kind = candidate.kind;
            PriorityQueue<Candidate> shortlist = this.shortlists.get(kind);
            if (shortlist.size() < PATHFINDING_SHORTLIST_SIZE_PER_RESOURCE) {
                shortlist.add(candidate);
            } else if (candidate.value > shortlist.peek().value()) {
                shortlist.poll();
                shortlist.add(candidate);
            }
        }

        private void addExplorationCandidate(Candidate candidate) {
            if (!this.explorationCandidates.contains(candidate)) {
                this.explorationCandidates.add(candidate);
                this.explorationRanked = false;
            }
        }

        private void recordPrimaryMisses(FakeNpcEntity npc) {
            if (this.primaryMissesRecorded) return;
            this.primaryMissesRecorded = true;
            for (SurvivalPlanner.Resource resource : this.attemptedResources) {
                npc.getMemories().recordResourceMiss(npc, resource);
            }
        }

    }

    /** Iterates loaded chunks in a horizontal ring without loading or generating missing chunks. */
    private static final class LoadedChunkRingIterator implements Iterator<BlockPos> {
        private final ServerLevel level;
        private final BlockPos origin;
        private final int innerRadius;
        private final int outerRadius;
        private final int minY;
        private final int maxY;
        private final List<ChunkPos> chunks;
        private int chunkIndex = -1;
        private int localX;
        private int localZ;
        private int y;
        private @Nullable ChunkPos chunk;
        private @Nullable BlockPos next;

        private LoadedChunkRingIterator(ServerLevel level, BlockPos origin, int innerRadius,
                                        int outerRadius, int verticalRadius) {
            this.level = level;
            this.origin = origin;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.minY = Math.max(level.getMinY(), origin.getY() - verticalRadius);
            this.maxY = Math.min(level.getMaxY() - 1, origin.getY() + verticalRadius);
            ChunkPos center = ChunkPos.containing(origin);
            int chunkRadius = (outerRadius + 15) / 16;
            this.chunks = new ArrayList<>();
            for (int chunkX = center.x() - chunkRadius; chunkX <= center.x() + chunkRadius; chunkX++) {
                for (int chunkZ = center.z() - chunkRadius; chunkZ <= center.z() + chunkRadius; chunkZ++) {
                    ChunkPos candidate = new ChunkPos(chunkX, chunkZ);
                    if (level.isLoaded(candidate.getMiddleBlockPosition(origin.getY()))) {
                        this.chunks.add(candidate);
                    }
                }
            }
            this.chunks.sort(Comparator.comparingInt(center::distanceSquared));
            advance();
        }

        @Override
        public boolean hasNext() {
            return this.next != null;
        }

        @Override
        public BlockPos next() {
            if (this.next == null) throw new NoSuchElementException();
            BlockPos result = this.next;
            advance();
            return result;
        }

        private void advance() {
            this.next = null;
            while (true) {
                if (this.chunk == null) {
                    if (++this.chunkIndex >= this.chunks.size()) return;
                    this.chunk = this.chunks.get(this.chunkIndex);
                    this.localX = 0;
                    this.localZ = 0;
                    this.y = this.minY;
                }
                while (this.localX < 16) {
                    int x = this.chunk.getBlockX(this.localX);
                    int z = this.chunk.getBlockZ(this.localZ);
                    int dx = Math.abs(x - this.origin.getX());
                    int dz = Math.abs(z - this.origin.getZ());
                    int distance = Math.max(dx, dz);
                    if ((this.innerRadius == 0 || distance > this.innerRadius)
                            && distance <= this.outerRadius) {
                        if (this.y <= this.maxY) {
                            this.next = new BlockPos(x, this.y++, z);
                            return;
                        }
                    }
                    this.y = this.minY;
                    if (++this.localZ >= 16) {
                        this.localZ = 0;
                        this.localX++;
                    }
                }
                this.chunk = null;
            }
        }
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Candidate(BlockPos blockPos, Kind kind, double value) {
    }
}
