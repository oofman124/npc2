package npc2.npc2.ai.crafting;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;

import java.util.List;

/** Curated 3x3 recipes used only beside a crafting table. */
public final class WorkstationCrafting {
    private static final List<ToolRecipe> TOOLS = List.of(
            new ToolRecipe(List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE), 3, 2),
            new ToolRecipe(List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE), 3, 2),
            new ToolRecipe(List.of(Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL), 1, 2),
            new ToolRecipe(List.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE), 2, 2),
            new ToolRecipe(List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD), 2, 1)
    );

    private WorkstationCrafting() {
    }

    public static boolean canCraft(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        if (!npc2.npc2.ai.survival.SurvivalNeeds.hasBedAvailable(npc)
                && BasicCrafting.countTag(bag, ItemTags.WOOL) >= 3
                && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= 3
                && BasicCrafting.canAdd(bag, Items.BED.white(), 1)) {
            return true;
        }
        if (!controller.hasShieldOwned(npc)
                && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= 6
                && bag.countItem(Items.IRON_INGOT) >= 1
                && BasicCrafting.canAdd(bag, Items.SHIELD, 1)) {
            return true;
        }
        if (bag.countItem(Items.FURNACE) == 0 && bag.countItem(Items.COBBLESTONE) >= 8
                && BasicCrafting.canAdd(bag, Items.FURNACE, 1)) {
            return true;
        }
        if (controller.countFood(npc) < 8 && bag.countItem(Items.WHEAT) >= 3
                && BasicCrafting.canAdd(bag, Items.BREAD, 1)) {
            return true;
        }
        for (ToolRecipe recipe : TOOLS) {
            if (!ownsAny(npc, recipe.outputs) && chooseTier(bag, recipe.materialCount) >= 0
                    && bag.countItem(Items.STICK) >= recipe.sticks) {
                return true;
            }
        }
        return false;
    }

    public static boolean craftOne(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        if (!npc2.npc2.ai.survival.SurvivalNeeds.hasBedAvailable(npc)
                && BasicCrafting.countTag(bag, ItemTags.WOOL) >= 3
                && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= 3
                && BasicCrafting.canAdd(bag, Items.BED.white(), 1)) {
            BasicCrafting.consumeTag(bag, ItemTags.WOOL, 3);
            BasicCrafting.consumeTag(bag, ItemTags.PLANKS, 3);
            bag.addItem(new ItemStack(Items.BED.white()));
            return changed(bag);
        }
        if (!controller.hasShieldOwned(npc)
                && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= 6
                && bag.countItem(Items.IRON_INGOT) >= 1
                && BasicCrafting.canAdd(bag, Items.SHIELD, 1)) {
            BasicCrafting.consumeTag(bag, ItemTags.PLANKS, 6);
            BasicCrafting.consumeItem(bag, Items.IRON_INGOT, 1);
            bag.addItem(new ItemStack(Items.SHIELD));
            return changed(bag);
        }
        if (bag.countItem(Items.FURNACE) == 0 && bag.countItem(Items.COBBLESTONE) >= 8
                && BasicCrafting.canAdd(bag, Items.FURNACE, 1)) {
            BasicCrafting.consumeItem(bag, Items.COBBLESTONE, 8);
            bag.addItem(new ItemStack(Items.FURNACE));
            return changed(bag);
        }
        if (controller.countFood(npc) < 8 && bag.countItem(Items.WHEAT) >= 3
                && BasicCrafting.canAdd(bag, Items.BREAD, 1)) {
            BasicCrafting.consumeItem(bag, Items.WHEAT, 3);
            bag.addItem(new ItemStack(Items.BREAD));
            return changed(bag);
        }
        for (ToolRecipe recipe : TOOLS) {
            if (ownsAny(npc, recipe.outputs) || bag.countItem(Items.STICK) < recipe.sticks) {
                continue;
            }
            int tier = chooseTier(bag, recipe.materialCount);
            if (tier < 0 || !BasicCrafting.canAdd(bag, recipe.outputs.get(tier), 1)) {
                continue;
            }
            consumeTierMaterial(bag, tier, recipe.materialCount);
            BasicCrafting.consumeItem(bag, Items.STICK, recipe.sticks);
            bag.addItem(new ItemStack(recipe.outputs.get(tier)));
            return changed(bag);
        }
        return false;
    }

    private static boolean ownsAny(FakeNpcEntity npc, List<Item> items) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (items.contains(npc.getItemBySlot(slot).getItem())) {
                return true;
            }
        }
        for (ItemStack stack : npc.getInventory()) {
            if (items.contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    /** Prefer iron, then stone, then wood without inspecting the global recipe registry. */
    private static int chooseTier(SimpleContainer bag, int amount) {
        if (bag.countItem(Items.IRON_INGOT) >= amount) return 2;
        if (bag.countItem(Items.COBBLESTONE) >= amount) return 1;
        if (BasicCrafting.countTag(bag, ItemTags.PLANKS) >= amount) return 0;
        return -1;
    }

    private static void consumeTierMaterial(SimpleContainer bag, int tier, int amount) {
        if (tier == 2) BasicCrafting.consumeItem(bag, Items.IRON_INGOT, amount);
        else if (tier == 1) BasicCrafting.consumeItem(bag, Items.COBBLESTONE, amount);
        else BasicCrafting.consumeTag(bag, ItemTags.PLANKS, amount);
    }

    private static boolean changed(SimpleContainer bag) {
        bag.setChanged();
        return true;
    }

    private record ToolRecipe(List<Item> outputs, int materialCount, int sticks) {
    }
}
