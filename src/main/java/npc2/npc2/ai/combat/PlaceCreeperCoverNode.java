package npc2.npc2.ai.combat;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

/** Places immediate blast cover when an ignited creeper threatens an unshielded NPC. */
@NullMarked
public final class PlaceCreeperCoverNode extends ExecutableNode {
    private static final int PLACEMENT_COOLDOWN = 40;
    public final SignalPort outPort;

    public PlaceCreeperCoverNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Brain") instanceof NpcBrain brain
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller) {
            if (brain.memories.creeperCoverCooldown > 0) {
                brain.memories.creeperCoverCooldown--;
            } else if (!controller.hasShieldEquipped(npc)) {
                Creeper creeper = brain.memories.target instanceof Creeper target
                        && controller.shouldBlockCreeper(npc, target)
                        ? target
                        : controller.findThreateningCreeper(npc, 8.0D);
                if (creeper != null && placeCover(npc, controller, creeper)) {
                    brain.memories.creeperCoverCooldown = PLACEMENT_COOLDOWN;
                }
            }
        }
        this.outPort.fire(context);
    }

    private static boolean placeCover(FakeNpcEntity npc, NpcController controller, Creeper creeper) {
        Direction direction = directionToward(npc, creeper);
        BlockPos front = npc.blockPosition().relative(direction);
        if (!npc.level().getBlockState(front).isAir()
                || npc.level().getBlockState(front.below()).isAir()) return false;

        SimpleContainer bag = npc.getInventory();
        int slot = findBlock(bag);
        if (slot < 0) return false;

        ItemStack previousHand = npc.getMainHandItem();
        npc.setItemInHand(InteractionHand.MAIN_HAND, bag.getItem(slot));
        controller.lookAt(npc, creeper.getEyePosition());
        boolean placed = controller.placeBlockOnTop(npc, front.below());
        ItemStack remaining = npc.getMainHandItem();
        npc.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
        bag.setItem(slot, remaining);
        bag.setChanged();
        return placed;
    }

    private static int findBlock(SimpleContainer bag) {
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return slot;
        }
        return -1;
    }

    private static Direction directionToward(FakeNpcEntity npc, Creeper creeper) {
        double x = creeper.getX() - npc.getX();
        double z = creeper.getZ() - npc.getZ();
        if (Math.abs(x) > Math.abs(z)) return x >= 0.0D ? Direction.EAST : Direction.WEST;
        return z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }
}
