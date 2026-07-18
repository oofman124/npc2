package npc2.npc2.ai.crafting;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;

import java.util.List;

/** Shared tool-tier knowledge used by planning, crafting, and resource harvesting. */
public final class ToolProgression {
    public static final int NONE = -1;
    public static final int WOOD = 0;
    public static final int STONE = 1;
    public static final int IRON = 2;
    public static final int DIAMOND = 3;
    public static final int NETHERITE = 4;

    public static final List<Item> PICKAXES = List.of(
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
            Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE
    );

    private ToolProgression() {
    }

    public static int pickaxeTier(FakeNpcEntity npc) {
        int tier = highestOwnedTier(npc, PICKAXES);
        // Gold has wooden mining capability; count it so an already-equipped NPC does
        // not incorrectly restart at a wooden pickaxe.
        return owns(npc, Items.GOLDEN_PICKAXE) ? Math.max(WOOD, tier) : tier;
    }

    public static int highestOwnedTier(FakeNpcEntity npc, List<Item> orderedTiers) {
        int highest = NONE;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            highest = Math.max(highest, orderedTiers.indexOf(npc.getItemBySlot(slot).getItem()));
        }
        for (ItemStack stack : npc.getInventory()) {
            highest = Math.max(highest, orderedTiers.indexOf(stack.getItem()));
        }
        return highest;
    }

    private static boolean owns(FakeNpcEntity npc, Item item) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (npc.getItemBySlot(slot).is(item)) return true;
        }
        return npc.getInventory().countItem(item) > 0;
    }
}
