package npc2.npc2.ai.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.util.ReachableApproach;
import org.jspecify.annotations.Nullable;

/**
 * Shared, persistent acquisition flow for workstations carried by an NPC.
 * It searches for a reachable placement POI, walks beside it, revalidates it,
 * and relocates when the local area has no usable floor.
 */
public final class CarriedStationPlacement {
    private static final int SITE_RADIUS = 8;
    private static final int SITE_VERTICAL_RANGE = 3;
    private static final int MAX_REACHABILITY_TESTS = 32;
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;

    private CarriedStationPlacement() {
    }

    public static Result tick(NpcBrain brain, Item item, BlockInteractionStations.Kind kind) {
        int stationSlot = findSlot(brain.npc.getInventory(), item);
        if (stationSlot < 0 || !(item instanceof BlockItem blockItem)) {
            clear(brain, kind);
            return new Result(State.UNAVAILABLE, null);
        }

        PlacementSite site = brain.memories.stationPlacementSites.get(kind);
        if (site != null && !isValidSite(brain, blockItem, site.blockPos())) {
            brain.memories.stationPlacementSites.remove(kind);
            site = null;
        }
        if (site == null) {
            site = findSite(brain, blockItem);
            if (site != null) {
                brain.memories.stationRelocationTargets.remove(kind);
                brain.memories.stationPlacementSites.put(kind, site);
            }
        }
        if (site != null) return approachAndPlace(brain, stationSlot, kind, site);

        // A production plan must still create movement when every nearby floor
        // position is blocked. Move to another reachable patch and search again.
        Vec3 relocation = brain.memories.stationRelocationTargets.get(kind);
        if (relocation != null) {
            boolean arrived = brain.npc.distanceToSqr(relocation) <= 4.0D;
            if (arrived) {
                brain.controller.stopMoving(brain.npc);
                brain.memories.stationRelocationTargets.remove(kind);
                brain.npc.getNpcNavigation().markRecovered();
                return new Result(State.SEARCHING, null);
            }
            if (brain.npc.getNpcNavigation().shouldAbandonTarget()
                    || !brain.controller.moveTo(brain.npc, relocation, 0.23D)) {
                brain.memories.stationRelocationTargets.remove(kind);
                brain.npc.getNpcNavigation().markTargetAbandoned();
                return new Result(State.SEARCHING, null);
            }
            return new Result(State.RELOCATING, null);
        }

        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 candidate = LandRandomPos.getPos(brain.npc, 14, 5);
            if (candidate != null && brain.npc.distanceToSqr(candidate) >= 16.0D
                    && brain.npc.getNpcNavigation().canReach(BlockPos.containing(candidate))) {
                brain.memories.stationRelocationTargets.put(kind, candidate);
                if (brain.controller.moveTo(brain.npc, candidate, 0.23D)) {
                    return new Result(State.RELOCATING, null);
                }
                brain.memories.stationRelocationTargets.remove(kind);
                brain.npc.getNpcNavigation().markTargetAbandoned();
            }
        }
        return new Result(State.SEARCHING, null);
    }

    public static boolean isActive(NpcBrain brain, BlockInteractionStations.Kind kind) {
        return brain.memories.stationPlacementSites.containsKey(kind)
                || brain.memories.stationRelocationTargets.containsKey(kind);
    }

    public static void clear(NpcBrain brain, BlockInteractionStations.Kind kind) {
        brain.memories.stationPlacementSites.remove(kind);
        brain.memories.stationRelocationTargets.remove(kind);
    }

    private static Result approachAndPlace(NpcBrain brain, int stationSlot,
                                           BlockInteractionStations.Kind kind, PlacementSite site) {
        if (brain.npc.distanceToSqr(site.approachPosition()) > ARRIVAL_DISTANCE_SQR) {
            if (brain.npc.getNpcNavigation().shouldAbandonTarget()
                    || !brain.controller.moveTo(brain.npc, site.approachPosition(), 0.24D)) {
                brain.memories.stationPlacementSites.remove(kind);
                brain.npc.getNpcNavigation().markTargetAbandoned();
                return new Result(State.SEARCHING, null);
            }
            return new Result(State.APPROACHING, null);
        }

        brain.controller.stopMoving(brain.npc);
        SimpleContainer bag = brain.npc.getInventory();
        ItemStack previousHand = brain.npc.getMainHandItem();
        brain.npc.setItemInHand(InteractionHand.MAIN_HAND, bag.getItem(stationSlot));
        boolean placed = brain.controller.placeBlockOnTop(brain.npc, site.blockPos().below());
        ItemStack remaining = brain.npc.getMainHandItem();
        brain.npc.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
        bag.setItem(stationSlot, remaining);
        bag.setChanged();
        brain.memories.stationPlacementSites.remove(kind);

        if (!placed || !kind.matches(brain.npc.level().getBlockState(site.blockPos()))) {
            // The POI may have become occupied after it was selected. Forget it
            // immediately so the next tick selects another site instead of waiting.
            brain.npc.getNpcNavigation().markTargetAbandoned();
            return new Result(State.SEARCHING, null);
        }
        brain.memories.stationRelocationTargets.remove(kind);
        return new Result(State.PLACED,
                new BlockInteractionStations.Target(kind, site.blockPos(), site.approachPosition()));
    }

    private static @Nullable PlacementSite findSite(NpcBrain brain, BlockItem item) {
        BlockPos origin = brain.npc.blockPosition();
        int reachabilityTests = 0;
        for (int ring = 1; ring <= SITE_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    for (int dy = -SITE_VERTICAL_RANGE; dy <= SITE_VERTICAL_RANGE; dy++) {
                        BlockPos placeAt = origin.offset(dx, dy, dz);
                        if (!isValidSite(brain, item, placeAt)) continue;
                        if (++reachabilityTests > MAX_REACHABILITY_TESTS) return null;
                        Vec3 approach = ReachableApproach.beside(brain.npc, placeAt);
                        if (approach != null) return new PlacementSite(placeAt.immutable(), approach);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isValidSite(NpcBrain brain, BlockItem item, BlockPos placeAt) {
        if (!brain.npc.level().hasChunk(placeAt.getX() >> 4, placeAt.getZ() >> 4)
                || !brain.npc.level().getWorldBorder().isWithinBounds(placeAt)
                || !brain.npc.level().getBlockState(placeAt).canBeReplaced()) return false;
        BlockPos floor = placeAt.below();
        if (!brain.npc.level().getBlockState(floor).isFaceSturdy(brain.npc.level(), floor, Direction.UP)) {
            return false;
        }
        BlockState state = item.getBlock().defaultBlockState();
        return state.canSurvive(brain.npc.level(), placeAt)
                && brain.npc.level().isUnobstructed(state, placeAt, CollisionContext.of(brain.npc));
    }

    private static int findSlot(SimpleContainer bag, Item item) {
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (bag.getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    public enum State {
        PLACED,
        APPROACHING,
        RELOCATING,
        SEARCHING,
        UNAVAILABLE
    }

    public record PlacementSite(BlockPos blockPos, Vec3 approachPosition) {
    }

    public record Result(State state, BlockInteractionStations.@Nullable Target target) {
    }
}
