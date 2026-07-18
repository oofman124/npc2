package npc2.npc2.ai.survival;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.crafting.WorkstationCrafting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;

/** Shared, normalized need scores used by gathering, hunting, crafting, and storage policies. */
public final class SurvivalNeeds {
    public static final int FOOD_TARGET = 8;
    public static final int LOG_TARGET = 16;
    public static final int COBBLESTONE_TARGET = 16;
    public static final int SOIL_TARGET = 12;
    public static final int FUEL_TARGET = 8;
    public static final int TORCH_TARGET = 16;

    private SurvivalNeeds() {
    }

    public static double food(FakeNpcEntity npc, NpcController controller) {
        double deficit = deficit(controller.countFood(npc), FOOD_TARGET);
        double injury = 1.0D - npc.getHealth() / npc.getMaxHealth();
        return deficit * 55.0D + injury * 90.0D;
    }

    public static double materials(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        return Math.max(
                deficit(controller.countInventoryTag(bag, ItemTags.LOGS), LOG_TARGET) * 45.0D,
                Math.max(deficit(bag.countItem(Items.COBBLESTONE), COBBLESTONE_TARGET) * 40.0D,
                        Math.max(deficit(bag.countItem(Items.DIRT), SOIL_TARGET) * 24.0D,
                                deficit(countFuel(bag), FUEL_TARGET) * 32.0D)));
    }

    public static double tools(FakeNpcEntity npc, NpcController controller) {
        return WorkstationCrafting.canCraft(npc, controller) ? 36.0D : 0.0D;
    }

    public static double light(FakeNpcEntity npc) {
        double deficit = deficit(npc.getInventory().countItem(Items.TORCH), TORCH_TARGET);
        return deficit * (npc.level().isBrightOutside() ? 18.0D : 42.0D);
    }

    public static boolean foodOutranksGathering(FakeNpcEntity npc, NpcController controller) {
        return food(npc, controller) >= Math.max(materials(npc, controller), tools(npc, controller));
    }

    public static int countFuel(SimpleContainer bag) {
        return bag.countItem(Items.COAL) + bag.countItem(Items.CHARCOAL);
    }

    public static boolean ownsBed(FakeNpcEntity npc) {
        for (ItemStack stack : npc.getInventory()) if (stack.is(ItemTags.BEDS)) return true;
        return false;
    }

    public static boolean hasBedAvailable(FakeNpcEntity npc) {
        if (ownsBed(npc)) return true;
        for (BlockPos pos : BlockPos.withinManhattan(npc.blockPosition(), 12, 4, 12)) {
            if (npc.level().getBlockState(pos).getBlock() instanceof BedBlock) return true;
        }
        return false;
    }

    public static boolean isNight(FakeNpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return time >= 12542L && time < 23460L;
    }

    private static double deficit(int current, int target) {
        return Math.max(0.0D, target - current) / target;
    }
}
