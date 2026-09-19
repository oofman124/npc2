package npc2.npc2.ai.rest;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

/** Places a carried bed when shelter is missing and adds a nearby torch before sleeping. */
@NullMarked
public class PrepareCampNode extends ExecutableNode {
    private static final int ACTION_INTERVAL = 20;
    public final SignalPort outPort;

    public PrepareCampNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain
                && brain.memories.campPreparationCooldown-- <= 0) {
            brain.memories.campPreparationCooldown = ACTION_INTERVAL;
            BlockPos bed = brain.memories.bedTarget != null ? brain.memories.bedTarget.bedPos() : brain.memories.campBedPosition;
            if (bed == null || !(brain.npc.level().getBlockState(bed).getBlock() instanceof BedBlock)) {
                bed = placeBed(brain);
                brain.memories.campBedPosition = bed;
            }
            if (bed != null) placeBedsideTorch(brain, bed);
        }
        this.outPort.fire(context);
    }

    private static BlockPos placeBed(NpcBrain brain) {
        int slot = findSlot(brain.npc.getInventory(), true);
        if (slot < 0) return null;
        BlockPos origin = brain.npc.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos foot = origin.relative(direction, 2);
            BlockPos head = foot.relative(direction);
            if (!canPlaceOnFloor(brain, foot) || !canPlaceOnFloor(brain, head)) continue;
            if (placeFromBag(brain, slot, foot.below())) return foot.immutable();
        }
        return null;
    }

    private static void placeBedsideTorch(NpcBrain brain, BlockPos bed) {
        for (BlockPos pos : BlockPos.withinBoxByManhattanDistance(bed, 3, 2, 3)) {
            if (brain.npc.level().getBlockState(pos).is(Blocks.TORCH)
                    || brain.npc.level().getBlockState(pos).is(Blocks.WALL_TORCH)) return;
        }
        int slot = findSlot(brain.npc.getInventory(), false);
        if (slot < 0) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = bed.relative(direction, 2);
            if (canPlaceOnFloor(brain, candidate) && placeFromBag(brain, slot, candidate.below())) return;
        }
    }

    private static boolean canPlaceOnFloor(NpcBrain brain, BlockPos pos) {
        return brain.npc.level().getBlockState(pos).isAir()
                && !brain.npc.level().getBlockState(pos.below()).isAir()
                && !(brain.npc.level().getBlockState(pos.below()).getBlock() instanceof BedBlock);
    }

    private static int findSlot(SimpleContainer bag, boolean bed) {
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (bed ? stack.is(ItemTags.BEDS) : stack.is(Items.TORCH)) return slot;
        }
        return -1;
    }

    private static boolean placeFromBag(NpcBrain brain, int slot, BlockPos floor) {
        SimpleContainer bag = brain.npc.getInventory();
        ItemStack previous = brain.npc.getMainHandItem();
        brain.npc.setItemInHand(InteractionHand.MAIN_HAND, bag.getItem(slot));
        boolean placed = brain.controller.placeBlockOnTop(brain.npc, floor);
        ItemStack remaining = brain.npc.getMainHandItem();
        brain.npc.setItemInHand(InteractionHand.MAIN_HAND, previous);
        bag.setItem(slot, remaining);
        bag.setChanged();
        return placed;
    }
}
