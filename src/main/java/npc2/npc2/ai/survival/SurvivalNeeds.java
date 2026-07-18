package npc2.npc2.ai.survival;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.CoolEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;

/** Shared, normalized need scores used by gathering, hunting, crafting, and storage policies. */
public final class SurvivalNeeds {
    public static final int FOOD_TARGET = 8;
    public static final int LOG_TARGET = 16;
    public static final int COBBLESTONE_TARGET = 24;
    public static final int SOIL_TARGET = 12;
    public static final int FUEL_TARGET = 8;
    public static final int TORCH_TARGET = 16;
    public static final int IRON_TARGET = 12;
    public static final int DIAMOND_TARGET = 6;

    private SurvivalNeeds() {
    }

    public static double food(FakeNpcEntity npc, NpcController controller) {
        return planFor(npc, controller).score(SurvivalPlanner.Resource.FOOD);
    }

    public static double materials(FakeNpcEntity npc, NpcController controller) {
        return planFor(npc, controller).highestGatheringScore();
    }

    public static double tools(FakeNpcEntity npc, NpcController controller) {
        return planFor(npc, controller).actionScore();
    }

    public static double light(FakeNpcEntity npc) {
        return planFor(npc, npc.getController()).score(SurvivalPlanner.Resource.TORCHES);
    }

    public static boolean foodOutranksGathering(FakeNpcEntity npc, NpcController controller) {
        SurvivalPlanner.Plan plan = planFor(npc, controller);
        return plan.score(SurvivalPlanner.Resource.FOOD)
                >= Math.max(plan.highestGatheringScore(), plan.actionScore());
    }

    public static int countFuel(SimpleContainer bag) {
        return bag.countItem(Items.COAL) + bag.countItem(Items.CHARCOAL);
    }

    public static SurvivalPlanner.Plan planFor(FakeNpcEntity npc, NpcController controller) {
        if (npc instanceof CoolEntity cool && cool.brain != null && cool.brain.plan != null) {
            return cool.brain.plan;
        }
        return SurvivalPlanner.create(npc, controller);
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

}
