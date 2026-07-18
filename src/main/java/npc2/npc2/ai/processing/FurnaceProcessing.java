package npc2.npc2.ai.processing;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.interaction.BlockInteractionStations;

/** Inventory transfer rules for unattended vanilla furnace smelting. */
public final class FurnaceProcessing {
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    private FurnaceProcessing() {
    }

    public static boolean canStart(FakeNpcEntity npc) {
        SimpleContainer bag = npc.getInventory();
        return bag.countItem(Items.RAW_IRON) > 0
                && (bag.countItem(Items.COAL) > 0 || bag.countItem(Items.CHARCOAL) > 0)
                && hasFurnaceAvailable(npc);
    }

    public static boolean hasFurnaceAvailable(FakeNpcEntity npc) {
        return npc.getInventory().countItem(Items.FURNACE) > 0
                || BlockInteractionStations.hasKnownTarget(npc, BlockInteractionStations.Kind.FURNACE);
    }

    public static boolean canService(AbstractFurnaceBlockEntity furnace) {
        ItemStack input = furnace.getItem(INPUT_SLOT);
        ItemStack result = furnace.getItem(RESULT_SLOT);
        return (input.isEmpty() || input.is(Items.RAW_IRON))
                && (result.isEmpty() || result.is(Items.IRON_INGOT));
    }

    public static boolean service(FakeNpcEntity npc, AbstractFurnaceBlockEntity furnace) {
        if (!canService(furnace)) return false;
        SimpleContainer bag = npc.getInventory();
        boolean changed = collectResult(bag, furnace);

        // Never load more ore than the fuel currently available can finish. Otherwise a
        // one-coal NPC can put twelve ore into a furnace, smelt eight, and wait forever.
        int rawWaiting = bag.countItem(Items.RAW_IRON) + furnace.getItem(INPUT_SLOT).getCount();
        if (furnace.getItem(FUEL_SLOT).isEmpty()) {
            Item fuel = bag.countItem(Items.COAL) > 0 ? Items.COAL : Items.CHARCOAL;
            int fuelNeeded = Math.min(8, (rawWaiting + 7) / 8);
            changed |= moveFromBag(bag, furnace, fuel, FUEL_SLOT, fuelNeeded);
        }
        if (furnace.getItem(INPUT_SLOT).isEmpty()) {
            int supportedOre = furnace.getItem(FUEL_SLOT).getCount() * 8;
            changed |= moveFromBag(bag, furnace, Items.RAW_IRON, INPUT_SLOT, supportedOre);
        }
        if (changed) {
            bag.setChanged();
            furnace.setChanged();
        }
        return hasPendingWork(npc, furnace);
    }

    public static boolean hasPendingWork(FakeNpcEntity npc, AbstractFurnaceBlockEntity furnace) {
        return canStart(npc)
                || furnace.getItem(INPUT_SLOT).is(Items.RAW_IRON)
                || furnace.getItem(RESULT_SLOT).is(Items.IRON_INGOT);
    }

    private static boolean collectResult(SimpleContainer bag, AbstractFurnaceBlockEntity furnace) {
        ItemStack result = furnace.getItem(RESULT_SLOT);
        if (!result.is(Items.IRON_INGOT) || !bag.canAddItem(result)) return false;
        int original = result.getCount();
        ItemStack remainder = bag.addItem(result.copy());
        int moved = original - remainder.getCount();
        if (moved <= 0) return false;
        result.shrink(moved);
        furnace.setItem(RESULT_SLOT, result);
        return true;
    }

    private static boolean moveFromBag(SimpleContainer bag, AbstractFurnaceBlockEntity furnace,
                                       Item item, int furnaceSlot, int limit) {
        ItemStack stored = furnace.getItem(furnaceSlot);
        if (!stored.isEmpty() && !stored.is(item)) return false;
        int capacity = stored.isEmpty() ? new ItemStack(item).getMaxStackSize()
                : stored.getMaxStackSize() - stored.getCount();
        int remaining = Math.min(limit, capacity);
        if (remaining <= 0) return false;

        boolean movedAny = false;
        for (int slot = 0; slot < bag.getContainerSize() && remaining > 0; slot++) {
            ItemStack source = bag.getItem(slot);
            if (!source.is(item)) continue;
            int moved = Math.min(remaining, source.getCount());
            if (stored.isEmpty()) {
                stored = source.copyWithCount(moved);
                furnace.setItem(furnaceSlot, stored);
            } else {
                stored.grow(moved);
            }
            source.shrink(moved);
            if (source.isEmpty()) bag.setItem(slot, ItemStack.EMPTY);
            remaining -= moved;
            movedAny = true;
        }
        return movedAny;
    }
}
