package npc2.npc2.ai.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.Nullable;

/** Places a carried workstation into a safe adjacent block and returns an interaction target. */
public final class CarriedStationPlacement {
    private CarriedStationPlacement() {
    }

    public static BlockInteractionStations.@Nullable Target place(
            NpcBrain brain, Item item, BlockInteractionStations.Kind kind) {
        SimpleContainer bag = brain.npc.getInventory();
        int stationSlot = -1;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (bag.getItem(slot).is(item)) {
                stationSlot = slot;
                break;
            }
        }
        if (stationSlot < 0) return null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos placeAt = brain.npc.blockPosition().relative(direction);
            if (!brain.npc.level().getBlockState(placeAt).isAir()
                    || brain.npc.level().getBlockState(placeAt.below()).isAir()) continue;
            ItemStack previousHand = brain.npc.getMainHandItem();
            brain.npc.setItemInHand(InteractionHand.MAIN_HAND, bag.getItem(stationSlot));
            boolean placed = brain.controller.placeBlockOnTop(brain.npc, placeAt.below());
            ItemStack remaining = brain.npc.getMainHandItem();
            brain.npc.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
            bag.setItem(stationSlot, remaining);
            bag.setChanged();
            if (placed && kind.matches(brain.npc.level().getBlockState(placeAt))) {
                return new BlockInteractionStations.Target(kind, placeAt.immutable(), brain.npc.position());
            }
        }
        return null;
    }
}
