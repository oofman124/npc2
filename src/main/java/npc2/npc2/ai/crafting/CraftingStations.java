package npc2.npc2.ai.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import org.jspecify.annotations.Nullable;

/** Crafting-table facade over the shared block-interaction station service. */
public final class CraftingStations {
    private CraftingStations() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, int radius) {
        BlockInteractionStations.Target target = BlockInteractionStations.findTarget(
                npc, BlockInteractionStations.Kind.CRAFTING_TABLE, radius);
        return target == null ? null : fromShared(target);
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        return BlockInteractionStations.claim(npc, target.shared());
    }

    public static boolean isUsable(FakeNpcEntity npc, Target target) {
        return BlockInteractionStations.isUsable(npc, target.shared());
    }

    public static void release(FakeNpcEntity npc) {
        BlockInteractionStations.release(npc, BlockInteractionStations.Kind.CRAFTING_TABLE);
    }

    public static void forget(FakeNpcEntity npc) {
        BlockInteractionStations.forget(npc, BlockInteractionStations.Kind.CRAFTING_TABLE);
    }

    public static boolean hasAvailable(FakeNpcEntity npc) {
        return npc.getInventory().countItem(Items.CRAFTING_TABLE) > 0
                || BlockInteractionStations.hasKnownTarget(npc, BlockInteractionStations.Kind.CRAFTING_TABLE);
    }

    public static Target fromShared(BlockInteractionStations.Target target) {
        return new Target(target.blockPos(), target.approachPosition());
    }

    public record Target(BlockPos tablePos, Vec3 approachPosition) {
        private BlockInteractionStations.Target shared() {
            return new BlockInteractionStations.Target(
                    BlockInteractionStations.Kind.CRAFTING_TABLE, this.tablePos, this.approachPosition);
        }
    }
}
