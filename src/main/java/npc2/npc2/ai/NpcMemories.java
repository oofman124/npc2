package npc2.npc2.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.Npc2Config;
import npc2.npc2.ai.crafting.CraftingStations;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.interaction.CarriedStationPlacement;
import npc2.npc2.ai.movement.BlockResourceGathering;
import npc2.npc2.ai.movement.ChestLooting;
import npc2.npc2.ai.movement.ResourceSurveyor;
import npc2.npc2.ai.rest.BedReservations;
import npc2.npc2.ai.survival.SurvivalPlanner;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All NPC AI state whose lifetime is longer than one generated behavior event.
 * Node objects keep configuration only; their counters and targets live here.
 */
public final class NpcMemories {
    // Current weighted plan, refreshed on a staggered schedule and exposed by graph context.
    public SurvivalPlanner.Plan plan = new SurvivalPlanner.Plan(
            Map.of(), SurvivalPlanner.Action.NONE, 0.0D);

    // Combat and sensing.
    public @Nullable LivingEntity target;
    public boolean hunting;
    public boolean blockingMob;
    public @Nullable LivingEntity blockThreat;
    public boolean retreating;
    public int targetScanCooldown;
    public boolean targetScanInitialized;
    public int guardTicksRemaining;
    public int attackDebounceTicks;
    public int creeperCoverCooldown;
    public final Map<UUID, Long> provokedPlayersUntil = new HashMap<>();

    // General movement.
    public @Nullable Vec3 wanderTarget;
    public boolean floating;
    public @Nullable Vec3 waterEscapeTarget;
    public int waterEscapeCooldown;
    public int wanderCooldown;
    public int idleTicks;

    // Looting and storage.
    public boolean seekingLoot;
    public @Nullable ItemEntity lootTarget;
    public int lootSearchCooldown;
    public boolean lootSearchInitialized;
    public boolean seekingChest;
    public @Nullable BlockPos chestTarget;
    public ChestLooting.@Nullable Target chestLootTarget;
    public int chestSearchCooldown;
    public boolean chestSearchInitialized;
    public boolean depositing;
    public ChestLooting.@Nullable Target chestDepositTarget;
    public @Nullable ResourceKey<Level> openChestDimension;
    public @Nullable BlockPos openChestPosition;
    public @Nullable BlockPos openChestPartnerPosition;
    public int chestVisualTicks;

    // Gathering and production.
    public boolean gatheringResource;
    public BlockResourceGathering.@Nullable Target resourceTarget;
    public BlockResourceGathering.@Nullable Search resourceSearch;
    public int resourceSearchCooldown;
    public int resourceWorkTicks;
    public boolean resourceSearchInitialized;
    public boolean exploringForResources;
    public ResourceSurveyor.@Nullable Survey resourceSurvey;
    public boolean seekingCraftingTable;
    public CraftingStations.@Nullable Target craftingTableTarget;
    public int craftingSearchCooldown;
    public int craftingCooldown;
    public boolean craftingSearchInitialized;
    public boolean processingFurnace;
    public BlockInteractionStations.@Nullable Target furnaceTarget;
    public int furnaceSearchCooldown;
    public final EnumMap<BlockInteractionStations.Kind, CarriedStationPlacement.PlacementSite> stationPlacementSites =
            new EnumMap<>(BlockInteractionStations.Kind.class);
    public final EnumMap<BlockInteractionStations.Kind, Vec3> stationRelocationTargets =
            new EnumMap<>(BlockInteractionStations.Kind.class);

    // Rest and local interaction.
    public boolean seekingBed;
    public BedReservations.@Nullable Target bedTarget;
    public @Nullable BlockPos campBedPosition;
    public boolean floorSleeping;
    public @Nullable BlockPos floorSleepPosition;
    public @Nullable ResourceKey<Level> homeDimension;
    public @Nullable BlockPos homeBedPosition;
    public boolean returningHome;
    public long homeUnavailableUntil;
    public int bedSearchCooldown;
    public boolean bedSearchInitialized;
    public int campPreparationCooldown;
    public @Nullable BlockPos doorTarget;

    // Periodic maintenance.
    public int eatCooldown;

    // Per-NPC memories previously held in shared static maps.
    public final Map<UUID, Long> avoidedLootUntil = new HashMap<>();
    public final Map<RememberedBlock, Long> avoidedResourceBlocksUntil = new HashMap<>();
    public final EnumMap<BlockInteractionStations.Kind, RememberedStation> knownStations =
            new EnumMap<>(BlockInteractionStations.Kind.class);
    private final EnumMap<SurvivalPlanner.Resource, LinkedHashMap<RememberedBlock, Long>> knownResources =
            new EnumMap<>(SurvivalPlanner.Resource.class);
    private final EnumMap<SurvivalPlanner.Resource, ResourceAvailability> resourceAvailability =
            new EnumMap<>(SurvivalPlanner.Resource.class);

    public void provoke(FakeNpcEntity npc, Player player) {
        int duration = Npc2Config.get().playerRetaliationTicks;
        if (duration <= 0) return;
        this.provokedPlayersUntil.put(player.getUUID(), npc.level().getGameTime() + duration);
    }

    public boolean isProvokedBy(FakeNpcEntity npc, Player player) {
        if (Npc2Config.get().playerRetaliationTicks <= 0) {
            this.provokedPlayersUntil.clear();
            return false;
        }
        long now = npc.level().getGameTime();
        this.provokedPlayersUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        return this.provokedPlayersUntil.getOrDefault(player.getUUID(), Long.MIN_VALUE) > now;
    }

    /** Save only durable knowledge; active paths and tasks are safely reacquired after loading. */
    public void save(ValueOutput output) {
        if (this.homeDimension != null && this.homeBedPosition != null) {
            ValueOutput home = output.child("home");
            writeDimension(home, this.homeDimension);
            writePos(home, this.homeBedPosition);
        }

        ValueOutput.ValueOutputList resources = output.childrenList("known_resources");
        this.knownResources.forEach((resource, entries) -> entries.forEach((block, seenTick) -> {
            ValueOutput entry = resources.addChild();
            entry.putString("resource", resource.name());
            writeDimension(entry, block.dimension());
            writePos(entry, block.pos());
            entry.putLong("seen_tick", seenTick);
        }));

        ValueOutput.ValueOutputList availability = output.childrenList("resource_availability");
        this.resourceAvailability.forEach((resource, value) -> {
            ValueOutput entry = availability.addChild();
            entry.putString("resource", resource.name());
            writeDimension(entry, value.dimension());
            entry.putInt("failures", value.failures());
            entry.putLong("last_failure_tick", value.lastFailureTick());
            entry.putLong("last_evidence_tick", value.lastEvidenceTick());
        });

        ValueOutput.ValueOutputList stations = output.childrenList("known_stations");
        this.knownStations.forEach((kind, remembered) -> {
            ValueOutput entry = stations.addChild();
            entry.putString("kind", kind.name());
            writeDimension(entry, remembered.dimension());
            writePos(entry, remembered.target().blockPos());
        });

        ValueOutput.ValueOutputList provoked = output.childrenList("provoked_players");
        this.provokedPlayersUntil.forEach((uuid, until) -> {
            ValueOutput entry = provoked.addChild();
            entry.putString("uuid", uuid.toString());
            entry.putLong("until", until);
        });
    }

    public void load(ValueInput input) {
        this.homeDimension = null;
        this.homeBedPosition = null;
        input.child("home").ifPresent(home -> {
            this.homeDimension = readDimension(home);
            this.homeBedPosition = readPos(home);
        });

        this.knownResources.clear();
        for (ValueInput entry : input.childrenListOrEmpty("known_resources")) {
            SurvivalPlanner.Resource resource = enumValue(
                    SurvivalPlanner.Resource.class, entry.getString("resource").orElse(null));
            ResourceKey<Level> dimension = readDimension(entry);
            BlockPos pos = readPos(entry);
            if (resource == null || dimension == null || pos == null) continue;
            LinkedHashMap<RememberedBlock, Long> remembered =
                    this.knownResources.computeIfAbsent(resource, ignored -> new LinkedHashMap<>());
            if (remembered.size() < 128) {
                remembered.put(new RememberedBlock(dimension, pos), entry.getLongOr("seen_tick", 0L));
            }
        }

        this.resourceAvailability.clear();
        for (ValueInput entry : input.childrenListOrEmpty("resource_availability")) {
            SurvivalPlanner.Resource resource = enumValue(
                    SurvivalPlanner.Resource.class, entry.getString("resource").orElse(null));
            ResourceKey<Level> dimension = readDimension(entry);
            if (resource == null || dimension == null) continue;
            this.resourceAvailability.put(resource, new ResourceAvailability(
                    dimension,
                    Math.clamp(entry.getIntOr("failures", 0), 0, 5),
                    entry.getLongOr("last_failure_tick", Long.MIN_VALUE / 2),
                    entry.getLongOr("last_evidence_tick", Long.MIN_VALUE / 2)));
        }

        this.knownStations.clear();
        for (ValueInput entry : input.childrenListOrEmpty("known_stations")) {
            BlockInteractionStations.Kind kind = enumValue(
                    BlockInteractionStations.Kind.class, entry.getString("kind").orElse(null));
            ResourceKey<Level> dimension = readDimension(entry);
            BlockPos pos = readPos(entry);
            if (kind == null || dimension == null || pos == null) continue;
            this.knownStations.put(kind, new RememberedStation(dimension,
                    new BlockInteractionStations.Target(kind, pos, Vec3.atBottomCenterOf(pos))));
        }

        this.provokedPlayersUntil.clear();
        for (ValueInput entry : input.childrenListOrEmpty("provoked_players")) {
            if (this.provokedPlayersUntil.size() >= 64) break;
            try {
                entry.getString("uuid").map(UUID::fromString).ifPresent(uuid ->
                        this.provokedPlayersUntil.put(uuid, entry.getLongOr("until", 0L)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void writeDimension(ValueOutput output, ResourceKey<Level> dimension) {
        output.putString("dimension", dimension.identifier().toString());
    }

    private static @Nullable ResourceKey<Level> readDimension(ValueInput input) {
        Identifier identifier = input.getString("dimension").map(Identifier::tryParse).orElse(null);
        return identifier == null ? null : ResourceKey.create(Registries.DIMENSION, identifier);
    }

    private static void writePos(ValueOutput output, BlockPos pos) {
        output.putInt("x", pos.getX());
        output.putInt("y", pos.getY());
        output.putInt("z", pos.getZ());
    }

    private static @Nullable BlockPos readPos(ValueInput input) {
        if (input.getInt("x").isEmpty() || input.getInt("y").isEmpty() || input.getInt("z").isEmpty()) return null;
        return new BlockPos(input.getIntOr("x", 0), input.getIntOr("y", 0), input.getIntOr("z", 0));
    }

    private static <E extends Enum<E>> @Nullable E enumValue(Class<E> type, @Nullable String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Apply learned local availability to a raw need without deleting the need itself. */
    public ResourceAdjustment adjustResourceScore(FakeNpcEntity npc, SurvivalPlanner.Resource resource,
                                                   double rawScore) {
        AvailabilitySnapshot availability = resourceAvailability(npc, resource);
        return new ResourceAdjustment(rawScore * availability.confidence(), availability.confidence(),
                availability.failures(), availability.ticksUntilRecovery());
    }

    /**
     * Record that a complete bounded search found no usable example of a resource.
     * Failures are rate limited so fast node ticks cannot collapse a score instantly.
     */
    public boolean recordResourceMiss(FakeNpcEntity npc, SurvivalPlanner.Resource resource) {
        long now = npc.level().getGameTime();
        ResourceAvailability availability = normalizedAvailability(npc, resource, now);
        if (availability != null && now - availability.lastFailureTick < 100L) return false;
        int failures = Math.min(5, availability == null ? 1 : availability.failures + 1);
        this.resourceAvailability.put(resource, new ResourceAvailability(
                npc.level().dimension(), failures, now,
                availability == null ? Long.MIN_VALUE / 2 : availability.lastEvidenceTick));
        return true;
    }

    /** A reachable primary-search target cautiously restores one confidence step. */
    public void recordResourceEvidence(FakeNpcEntity npc, SurvivalPlanner.Resource resource) {
        long now = npc.level().getGameTime();
        ResourceAvailability availability = normalizedAvailability(npc, resource, now);
        if (availability == null || now - availability.lastEvidenceTick < 200L) return;
        int failures = availability.failures - 1;
        if (failures <= 0) {
            this.resourceAvailability.remove(resource);
        } else {
            this.resourceAvailability.put(resource, new ResourceAvailability(
                    availability.dimension, failures, availability.lastFailureTick, now));
        }
    }

    /** Reaching and harvesting the block is strong evidence and clears all failures. */
    public void recordResourceSuccess(FakeNpcEntity npc, SurvivalPlanner.Resource resource) {
        ResourceAvailability availability = this.resourceAvailability.get(resource);
        if (availability != null && availability.dimension.equals(npc.level().dimension())) {
            this.resourceAvailability.remove(resource);
        }
    }

    public AvailabilitySnapshot resourceAvailability(FakeNpcEntity npc, SurvivalPlanner.Resource resource) {
        long now = npc.level().getGameTime();
        ResourceAvailability availability = normalizedAvailability(npc, resource, now);
        if (availability == null) return new AvailabilitySnapshot(1.0D, 0, 0L);
        double confidence = switch (availability.failures) {
            case 1 -> 0.72D;
            case 2 -> 0.45D;
            case 3 -> 0.24D;
            case 4 -> 0.10D;
            default -> 0.0D;
        };
        long recoveryInterval = recoveryInterval(resource);
        long ticksUntilRecovery = Math.max(1L,
                recoveryInterval - (now - availability.lastFailureTick));
        return new AvailabilitySnapshot(confidence, availability.failures, ticksUntilRecovery);
    }

    private @Nullable ResourceAvailability normalizedAvailability(FakeNpcEntity npc,
                                                                  SurvivalPlanner.Resource resource, long now) {
        ResourceAvailability availability = this.resourceAvailability.get(resource);
        if (availability == null) return null;
        if (!availability.dimension.equals(npc.level().dimension())) {
            this.resourceAvailability.remove(resource);
            return null;
        }
        long recoveryInterval = recoveryInterval(resource);
        int recovered = (int)((now - availability.lastFailureTick) / recoveryInterval);
        if (recovered <= 0) return availability;
        int failures = Math.max(0, availability.failures - recovered);
        if (failures == 0) {
            this.resourceAvailability.remove(resource);
            return null;
        }
        ResourceAvailability normalized = new ResourceAvailability(
                availability.dimension, failures,
                availability.lastFailureTick + recovered * recoveryInterval,
                availability.lastEvidenceTick);
        this.resourceAvailability.put(resource, normalized);
        return normalized;
    }

    private static long recoveryInterval(SurvivalPlanner.Resource resource) {
        return switch (resource) {
            case LOGS, SOIL -> 600L;
            case COBBLESTONE -> 900L;
            case FUEL -> 1_200L;
            case IRON_ORE -> 1_800L;
            default -> 600L;
        };
    }

    public void rememberResource(FakeNpcEntity npc, SurvivalPlanner.Resource resource, BlockPos pos) {
        final int maximumEntriesPerResource = 128;
        LinkedHashMap<RememberedBlock, Long> entries = this.knownResources.computeIfAbsent(
                resource, ignored -> new LinkedHashMap<>());
        RememberedBlock block = new RememberedBlock(npc.level().dimension(), pos.immutable());
        if (!entries.containsKey(block) && entries.size() >= maximumEntriesPerResource) {
            entries.keySet().removeIf(remembered -> !remembered.dimension().equals(npc.level().dimension()));
        }
        if (entries.containsKey(block) || entries.size() < maximumEntriesPerResource) {
            entries.put(block, npc.level().getGameTime());
        }
    }

    public List<RememberedBlock> knownResources(SurvivalPlanner.Resource resource) {
        LinkedHashMap<RememberedBlock, Long> entries = this.knownResources.get(resource);
        return entries == null ? List.of() : List.copyOf(entries.keySet());
    }

    public void forgetResource(SurvivalPlanner.Resource resource, ResourceKey<Level> dimension, BlockPos pos) {
        LinkedHashMap<RememberedBlock, Long> entries = this.knownResources.get(resource);
        if (entries != null) entries.remove(new RememberedBlock(dimension, pos.immutable()));
    }

    public void pruneKnownResources(FakeNpcEntity npc) {
        final long staleAfterTicks = 12_000L;
        long cutoff = npc.level().getGameTime() - staleAfterTicks;
        this.knownResources.values().forEach(entries ->
                entries.entrySet().removeIf(entry -> entry.getValue() < cutoff));
    }

    public int knownResourceCount() {
        return this.knownResources.values().stream().mapToInt(Map::size).sum();
    }

    public record RememberedBlock(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record RememberedStation(ResourceKey<Level> dimension, BlockInteractionStations.Target target) {
    }

    public record ResourceAdjustment(double score, double confidence, int failures, long ticksUntilRecovery) {
    }

    public record AvailabilitySnapshot(double confidence, int failures, long ticksUntilRecovery) {
    }

    private record ResourceAvailability(ResourceKey<Level> dimension, int failures,
                                        long lastFailureTick, long lastEvidenceTick) {
    }

}
