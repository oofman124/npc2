package npc2.npc2.ai.crafting;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;

/** A deliberately small hand-crafting ruleset for fundamental supplies. */
public final class BasicCrafting {
    private BasicCrafting() {
    }

    public static boolean canCraft(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        int planks = countTag(bag, ItemTags.PLANKS);
        int sticks = bag.countItem(Items.STICK);
        return (planks < 4 && countTag(bag, ItemTags.LOGS) >= 1 && canAdd(bag, Items.OAK_PLANKS, 4))
                || (sticks < 2 && planks >= 2 && canAdd(bag, Items.STICK, 4))
                || (bag.countItem(Items.CRAFTING_TABLE) == 0 && planks >= 4 && canAdd(bag, Items.CRAFTING_TABLE, 1))
                || (bag.countItem(Items.TORCH) < 16 && sticks >= 1 && countCoal(bag) >= 1 && canAdd(bag, Items.TORCH, 4));
    }

    public static boolean craftOne(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        int planks = countTag(bag, ItemTags.PLANKS);
        int sticks = bag.countItem(Items.STICK);

        if (planks < 4 && countTag(bag, ItemTags.LOGS) >= 1 && canAdd(bag, Items.OAK_PLANKS, 4)) {
            consumeTag(bag, ItemTags.LOGS, 1);
            bag.addItem(new ItemStack(Items.OAK_PLANKS, 4));
            return true;
        }
        if (sticks < 2 && planks >= 2 && canAdd(bag, Items.STICK, 4)) {
            consumeTag(bag, ItemTags.PLANKS, 2);
            bag.addItem(new ItemStack(Items.STICK, 4));
            return true;
        }
        if (bag.countItem(Items.CRAFTING_TABLE) == 0 && planks >= 4 && canAdd(bag, Items.CRAFTING_TABLE, 1)) {
            consumeTag(bag, ItemTags.PLANKS, 4);
            bag.addItem(new ItemStack(Items.CRAFTING_TABLE));
            return true;
        }
        if (bag.countItem(Items.TORCH) < 16 && sticks >= 1 && countCoal(bag) >= 1 && canAdd(bag, Items.TORCH, 4)) {
            consumeItem(bag, bag.countItem(Items.COAL) > 0 ? Items.COAL : Items.CHARCOAL, 1);
            consumeItem(bag, Items.STICK, 1);
            bag.addItem(new ItemStack(Items.TORCH, 4));
            return true;
        }
        return false;
    }

    private static int countCoal(SimpleContainer bag) {
        return bag.countItem(Items.COAL) + bag.countItem(Items.CHARCOAL);
    }

    static int countTag(SimpleContainer bag, TagKey<Item> tag) {
        int count = 0;
        for (ItemStack stack : bag) {
            if (stack.is(tag)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static void consumeTag(SimpleContainer bag, TagKey<Item> tag, int amount) {
        for (int slot = 0; slot < bag.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.is(tag)) {
                continue;
            }
            int taken = Math.min(amount, stack.getCount());
            stack.shrink(taken);
            amount -= taken;
            if (stack.isEmpty()) {
                bag.setItem(slot, ItemStack.EMPTY);
            }
        }
        bag.setChanged();
    }

    static void consumeItem(SimpleContainer bag, Item item, int amount) {
        for (int slot = 0; slot < bag.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(amount, stack.getCount());
            stack.shrink(taken);
            amount -= taken;
            if (stack.isEmpty()) {
                bag.setItem(slot, ItemStack.EMPTY);
            }
        }
        bag.setChanged();
    }

    static boolean canAdd(SimpleContainer bag, Item item, int count) {
        return bag.canAddItem(new ItemStack(item, count));
    }
}
