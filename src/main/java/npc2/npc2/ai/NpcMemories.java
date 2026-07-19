package npc2.npc2.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.crafting.CraftingStations;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.movement.BlockResourceGathering;
import npc2.npc2.ai.movement.ChestLooting;
import npc2.npc2.ai.rest.BedReservations;
import npc2.npc2.ai.survival.SurvivalPlanner;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All NPC AI state whose lifetime is longer than one generated behavior event.
 * Node objects keep configuration only; their counters and targets live here.
 */
public final class NpcMemories {
    // Current weighted plan, also copied into each tick's generated event context.
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

    // General movement.
    public @Nullable Vec3 wanderTarget;
    public boolean floating;
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
    public int depositCheckTicks;
    public @Nullable ResourceKey<Level> openChestDimension;
    public @Nullable BlockPos openChestPosition;
    public @Nullable BlockPos openChestPartnerPosition;
    public int chestVisualTicks;

    // Gathering and production.
    public boolean gatheringResource;
    public BlockResourceGathering.@Nullable Target resourceTarget;
    public int resourceSearchCooldown;
    public int resourceWorkTicks;
    public boolean resourceSearchInitialized;
    public boolean seekingCraftingTable;
    public CraftingStations.@Nullable Target craftingTableTarget;
    public int craftingSearchCooldown;
    public int craftingCooldown;
    public boolean craftingSearchInitialized;
    public boolean processingFurnace;
    public BlockInteractionStations.@Nullable Target furnaceTarget;
    public int furnaceSearchCooldown;

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
    public int campPreparationCooldown;
    public @Nullable BlockPos doorTarget;

    // Periodic maintenance.
    public int eatCooldown;
    public int inventoryMaintenanceTicks;
    public int terrainAssistanceTicks;

    // Per-NPC memories previously held in shared static maps.
    public final Map<UUID, Long> avoidedLootUntil = new HashMap<>();
    public final Map<RememberedBlock, Long> avoidedResourceBlocksUntil = new HashMap<>();
    public final EnumMap<BlockInteractionStations.Kind, RememberedStation> knownStations =
            new EnumMap<>(BlockInteractionStations.Kind.class);
    private final EnumMap<SurvivalPlanner.Resource, ResourceMiss> resourceMisses =
            new EnumMap<>(SurvivalPlanner.Resource.class);

    public ResourceAdjustment adjustResourceScore(FakeNpcEntity npc, SurvivalPlanner.Resource resource,
                                                   double score) {
        ResourceMiss miss = this.resourceMisses.get(resource);
        if (miss == null || !miss.dimension.equals(npc.level().dimension())) {
            return new ResourceAdjustment(score, 1.0D);
        }
        long now = npc.level().getGameTime();
        if (miss.until <= now) {
            this.resourceMisses.remove(resource);
            return new ResourceAdjustment(score, 1.0D);
        }
        double confidence = miss.count >= 2 ? 0.0D : 0.25D;
        return new ResourceAdjustment(score * confidence, confidence);
    }

    public boolean recordResourceMiss(FakeNpcEntity npc, SurvivalPlanner.Plan plan) {
        SurvivalPlanner.Need need = plan.highestGatheringNeed();
        if (need == null) return false;
        long now = npc.level().getGameTime();
        ResourceMiss previous = this.resourceMisses.get(need.resource());
        int count = previous != null
                && previous.dimension.equals(npc.level().dimension())
                && previous.until > now ? previous.count + 1 : 1;
        long until = now + (count >= 2 ? unavailableTicks(need.resource()) : 200L);
        this.resourceMisses.put(need.resource(), new ResourceMiss(
                npc.level().dimension(), Math.min(count, 2), until));
        return true;
    }

    public void recordResourceFound(SurvivalPlanner.Resource resource) {
        this.resourceMisses.remove(resource);
    }

    public @Nullable UnavailableResource unavailableResource(FakeNpcEntity npc) {
        long now = npc.level().getGameTime();
        UnavailableResource longest = null;
        for (Map.Entry<SurvivalPlanner.Resource, ResourceMiss> entry : this.resourceMisses.entrySet()) {
            ResourceMiss miss = entry.getValue();
            if (!miss.dimension.equals(npc.level().dimension()) || miss.count < 2 || miss.until <= now) continue;
            long ticks = miss.until - now;
            if (longest == null || ticks > longest.ticksRemaining) {
                longest = new UnavailableResource(entry.getKey(), ticks);
            }
        }
        return longest;
    }

    private static int unavailableTicks(SurvivalPlanner.Resource resource) {
        return switch (resource) {
            case DIAMOND -> 6000;
            case IRON_ORE -> 2400;
            case FUEL -> 1200;
            case LOGS, COBBLESTONE, SOIL -> 600;
            default -> 600;
        };
    }

    public record RememberedBlock(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record RememberedStation(ResourceKey<Level> dimension, BlockInteractionStations.Target target) {
    }

    public record ResourceAdjustment(double score, double confidence) {
    }

    public record UnavailableResource(SurvivalPlanner.Resource resource, long ticksRemaining) {
    }

    private record ResourceMiss(ResourceKey<Level> dimension, int count, long until) {
    }
}
