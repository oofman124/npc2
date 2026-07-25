package npc2.npc2.ai.rest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.survival.SurvivalNeeds;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** Validated home-bed memory and return-home arbitration. */
public final class NpcHome {
    public static final int NEARBY_BED_RADIUS = 12;
    private static final double HOME_THREAT_RADIUS = 8.0D;
    private static final int UNREACHABLE_RETRY_TICKS = 600;

    private NpcHome() {
    }

    public static void remember(FakeNpcEntity npc, BlockPos bedPos) {
        if (!(npc.level().getBlockState(bedPos).getBlock() instanceof BedBlock)) return;
        npc.getMemories().homeDimension = npc.level().dimension();
        npc.getMemories().homeBedPosition = bedPos.immutable();
        npc.getMemories().homeUnavailableUntil = 0L;
        npc.getMemories().returningHome = false;
    }

    public static boolean validate(FakeNpcEntity npc) {
        BlockPos home = npc.getMemories().homeBedPosition;
        if (home == null || npc.getMemories().homeDimension == null) return false;
        if (!npc.getMemories().homeDimension.equals(npc.level().dimension())) return false;
        if (!npc.level().hasChunk(home.getX() >> 4, home.getZ() >> 4)) return false;
        if (!(npc.level().getBlockState(home).getBlock() instanceof BedBlock)) {
            clear(npc);
            return false;
        }
        return true;
    }

    public static boolean isHome(FakeNpcEntity npc, BlockPos pos) {
        return validate(npc) && pos.equals(npc.getMemories().homeBedPosition);
    }

    public static boolean shouldPrioritize(NpcBrain brain) {
        FakeNpcEntity npc = brain.npc;
        if (!validate(npc) || npc.isSleeping() || !SurvivalNeeds.isNight(npc)
                || npc.level().getGameTime() < brain.memories.homeUnavailableUntil) return false;
        return brain.memories.bedTarget == null
                || npc.distanceToSqr(brain.memories.bedTarget.approachPosition())
                > NEARBY_BED_RADIUS * NEARBY_BED_RADIUS;
    }

    public static void updateReturnIntent(NpcBrain brain) {
        boolean wasReturningHome = brain.memories.returningHome;
        brain.memories.returningHome = shouldPrioritize(brain);
        if (!brain.memories.returningHome) return;
        if (!wasReturningHome) {
            brain.memories.bedSearchCooldown = Math.floorMod(brain.npc.getId() + 1, 3);
        }
        boolean cancelMovement = !wasReturningHome || hasConflictingWork(brain);
        if (brain.memories.bedTarget != null
                && !isHome(brain.npc, brain.memories.bedTarget.bedPos())) {
            BedReservations.release(brain.npc);
            brain.memories.bedTarget = null;
            brain.memories.seekingBed = false;
            cancelMovement = true;
        }
        if (!cancelMovement) return;

        brain.cancelOrdinaryWork();
    }

    private static boolean hasConflictingWork(NpcBrain brain) {
        return brain.memories.seekingLoot || brain.memories.lootTarget != null
                || brain.memories.seekingChest || brain.memories.chestLootTarget != null
                || brain.memories.depositing || brain.memories.chestDepositTarget != null
                || brain.memories.gatheringResource || brain.memories.resourceTarget != null
                || brain.memories.resourceSearch != null
                || brain.memories.seekingCraftingTable || brain.memories.craftingTableTarget != null
                || brain.memories.processingFurnace || brain.memories.furnaceTarget != null
                || !brain.memories.stationPlacementSites.isEmpty()
                || !brain.memories.stationRelocationTargets.isEmpty()
                || brain.memories.wanderTarget != null;
    }

    public static void defer(FakeNpcEntity npc) {
        npc.getMemories().returningHome = false;
        npc.getMemories().homeUnavailableUntil = npc.level().getGameTime() + UNREACHABLE_RETRY_TICKS;
    }

    public static @Nullable LivingEntity findThreat(FakeNpcEntity npc) {
        BlockPos home = npc.getMemories().homeBedPosition;
        if (!validate(npc) || home == null) return null;
        Vec3 center = Vec3.atCenterOf(home);
        AABB area = AABB.ofSize(center,
                HOME_THREAT_RADIUS * 2.0D,
                HOME_THREAT_RADIUS * 2.0D,
                HOME_THREAT_RADIUS * 2.0D);
        return npc.level().getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        candidate -> npc.getController().isValidCombatTarget(npc, candidate))
                .stream()
                .filter(candidate -> candidate.distanceToSqr(center)
                        <= HOME_THREAT_RADIUS * HOME_THREAT_RADIUS)
                .filter(candidate -> npc.getNpcNavigation().canReach(candidate))
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(center)))
                .orElse(null);
    }

    public static boolean isThreatAtHome(FakeNpcEntity npc, @Nullable LivingEntity target) {
        BlockPos home = npc.getMemories().homeBedPosition;
        return target != null && target.isAlive() && validate(npc) && home != null
                && target.distanceToSqr(Vec3.atCenterOf(home)) <= HOME_THREAT_RADIUS * HOME_THREAT_RADIUS;
    }

    public static void clear(FakeNpcEntity npc) {
        npc.getMemories().homeDimension = null;
        npc.getMemories().homeBedPosition = null;
        npc.getMemories().returningHome = false;
        npc.getMemories().homeUnavailableUntil = 0L;
    }
}
