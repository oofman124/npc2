package npc2.npc2.ai.crafting;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.processing.FurnaceProcessing;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/** Curated 3x3 recipes used only beside a crafting table. */
public final class WorkstationCrafting {
    private static final List<ToolRecipe> TOOLS = List.of(
            new ToolRecipe(List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE), 3, 2),
            new ToolRecipe(List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE), 3, 2),
            new ToolRecipe(List.of(Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL), 1, 2),
            new ToolRecipe(List.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE), 2, 2),
            new ToolRecipe(List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD), 2, 1)
    );

    private WorkstationCrafting() {
    }

    public static boolean canCraft(FakeNpcEntity npc, NpcController controller) {
        return craftingPriority(npc, controller) > 0.0D;
    }

    public static double craftingPriority(FakeNpcEntity npc, NpcController controller) {
        Opportunity opportunity = findOpportunity(npc, controller);
        return opportunity == null ? 0.0D : opportunity.score;
    }

    public static boolean craftOne(FakeNpcEntity npc, NpcController controller) {
        Opportunity opportunity = findOpportunity(npc, controller);
        if (opportunity == null) return false;
        SimpleContainer bag = npc.getInventory();
        switch (opportunity.kind) {
            case BED -> {
                BasicCrafting.consumeTag(bag, ItemTags.WOOL, 3);
                BasicCrafting.consumeTag(bag, ItemTags.PLANKS, 3);
                bag.addItem(new ItemStack(Items.BED.white()));
            }
            case TOOL -> {
                consumeTierMaterial(bag, opportunity.tier, opportunity.tool.materialCount);
                BasicCrafting.consumeItem(bag, Items.STICK, opportunity.tool.sticks);
                bag.addItem(new ItemStack(opportunity.tool.outputs.get(opportunity.tier)));
            }
            case SHIELD -> {
                BasicCrafting.consumeTag(bag, ItemTags.PLANKS, 6);
                BasicCrafting.consumeItem(bag, Items.IRON_INGOT, 1);
                bag.addItem(new ItemStack(Items.SHIELD));
            }
            case FURNACE -> {
                BasicCrafting.consumeItem(bag, Items.COBBLESTONE, 8);
                bag.addItem(new ItemStack(Items.FURNACE));
            }
            case BREAD -> {
                BasicCrafting.consumeItem(bag, Items.WHEAT, 3);
                bag.addItem(new ItemStack(Items.BREAD));
            }
        }
        return changed(bag);
    }

    private static Opportunity findOpportunity(FakeNpcEntity npc, NpcController controller) {
        if (!CraftingStations.hasAvailable(npc)) return null;
        SimpleContainer bag = npc.getInventory();
        List<Opportunity> choices = new ArrayList<>();

        if (!npc2.npc2.ai.survival.SurvivalNeeds.hasBedAvailable(npc)
                && BasicCrafting.countTag(bag, ItemTags.WOOL) >= 3
                && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= 3
                && BasicCrafting.canAdd(bag, Items.BED.white(), 1)) {
            choices.add(new Opportunity(RecipeKind.BED, null, -1, 92.0D));
        }

        double[] toolScores = {100.0D, 62.0D, 36.0D, 24.0D, 58.0D};
        for (int index = 0; index < TOOLS.size(); index++) {
            ToolRecipe recipe = TOOLS.get(index);
            int tier = chooseUpgradeTier(bag, recipe, ToolProgression.highestOwnedTier(npc, recipe.outputs));
            if (tier >= 0 && bag.countItem(Items.STICK) >= recipe.sticks
                    && BasicCrafting.canAdd(bag, recipe.outputs.get(tier), 1)) {
                choices.add(new Opportunity(RecipeKind.TOOL, recipe, tier, toolScores[index] + tier));
            }
        }

        if (!FurnaceProcessing.hasFurnaceAvailable(npc) && bag.countItem(Items.COBBLESTONE) >= 8
                && BasicCrafting.canAdd(bag, Items.FURNACE, 1)) {
            choices.add(new Opportunity(RecipeKind.FURNACE, null, -1, 94.0D));
        }
        if (controller.countFood(npc) < 8 && bag.countItem(Items.WHEAT) >= 3
                && BasicCrafting.canAdd(bag, Items.BREAD, 1)) {
            choices.add(new Opportunity(RecipeKind.BREAD, null, -1, 78.0D));
        }
        if (!controller.hasShieldOwned(npc)
                && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= 6
                && bag.countItem(Items.IRON_INGOT) >= 1
                && BasicCrafting.canAdd(bag, Items.SHIELD, 1)) {
            choices.add(new Opportunity(RecipeKind.SHIELD, null, -1, 68.0D));
        }

        return choices.stream().max(Comparator.comparingDouble(Opportunity::score)).orElse(null);
    }

    /** Prefer the best affordable tier that is a real upgrade over the owned tool. */
    private static int chooseUpgradeTier(SimpleContainer bag, ToolRecipe recipe, int ownedTier) {
        int amount = recipe.materialCount;
        if (ownedTier < ToolProgression.DIAMOND && bag.countItem(Items.DIAMOND) >= amount) return ToolProgression.DIAMOND;
        if (ownedTier < ToolProgression.IRON && bag.countItem(Items.IRON_INGOT) >= amount) return ToolProgression.IRON;
        if (ownedTier < ToolProgression.STONE && bag.countItem(Items.COBBLESTONE) >= amount) return ToolProgression.STONE;
        if (ownedTier < ToolProgression.WOOD && BasicCrafting.countTag(bag, ItemTags.PLANKS) >= amount) return ToolProgression.WOOD;
        return -1;
    }

    private static void consumeTierMaterial(SimpleContainer bag, int tier, int amount) {
        if (tier == ToolProgression.DIAMOND) BasicCrafting.consumeItem(bag, Items.DIAMOND, amount);
        else if (tier == ToolProgression.IRON) BasicCrafting.consumeItem(bag, Items.IRON_INGOT, amount);
        else if (tier == ToolProgression.STONE) BasicCrafting.consumeItem(bag, Items.COBBLESTONE, amount);
        else BasicCrafting.consumeTag(bag, ItemTags.PLANKS, amount);
    }

    private static boolean changed(SimpleContainer bag) {
        bag.setChanged();
        return true;
    }

    private record ToolRecipe(List<Item> outputs, int materialCount, int sticks) {
    }

    private enum RecipeKind {
        BED,
        TOOL,
        SHIELD,
        FURNACE,
        BREAD
    }

    private record Opportunity(RecipeKind kind, ToolRecipe tool, int tier, double score) {
    }
}
