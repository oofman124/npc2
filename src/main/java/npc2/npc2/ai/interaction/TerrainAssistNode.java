package npc2.npc2.ai.interaction;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import org.jspecify.annotations.NullMarked;

/** Conservative stuck recovery: place a simple floor block or break one soft obstruction. */
@NullMarked
public class TerrainAssistNode extends ExecutableNode {
    public final SignalPort outPort;

    public TerrainAssistNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller) {
            if (!npc.level().noCollision(npc, npc.getBoundingBox())) {
                npc.setPos(npc.getX(), npc.getY() + 0.25D, npc.getZ());
                npc.setDeltaMovement(npc.getDeltaMovement().add(0.0D, 0.12D, 0.0D));
                npc.getNpcNavigation().markRecovered();
                this.outPort.fire(context);
                return;
            }
            Path path = npc.getNpcNavigation().getPath();
            if ((path == null || path.isDone()) && npc.getNpcNavigation().hasFailedPath()) {
                if (npc.onGround()) npc.jumpFromGround();
                npc.getNpcNavigation().markRecovered();
                this.outPort.fire(context);
                return;
            }
            BlockPos next = path != null && !path.isDone()
                    ? path.getNextNodePos()
                    : npc.blockPosition().relative(npc.getDirection());
            if (!tryPlaceFloor(npc, controller, next)) {
                tryBreakObstacle(npc, controller, next);
            }
            if (npc.onGround()) {
                npc.jumpFromGround();
            }
            npc.getNpcNavigation().markRecovered();
        }
        this.outPort.fire(context);
    }

    private static boolean tryPlaceFloor(FakeNpcEntity npc, NpcController controller, BlockPos next) {
        BlockPos floor = next.below();
        if (!npc.level().getBlockState(next).isAir()
                || !npc.level().getBlockState(floor).isAir()
                || npc.level().getBlockState(floor.below()).isAir()) {
            return false;
        }

        SimpleContainer bag = npc.getInventory();
        int slot = findBuildingBlock(bag);
        if (slot < 0) {
            return false;
        }
        ItemStack previousHand = npc.getMainHandItem();
        npc.setItemInHand(InteractionHand.MAIN_HAND, bag.getItem(slot));
        boolean placed = controller.placeBlockOnTop(npc, floor.below());
        ItemStack remaining = npc.getMainHandItem();
        npc.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
        bag.setItem(slot, remaining);
        return placed;
    }

    private static void tryBreakObstacle(FakeNpcEntity npc, NpcController controller, BlockPos next) {
        BlockState state = npc.level().getBlockState(next);
        if (state.isAir()) {
            next = npc.blockPosition().relative(npc.getDirection());
            state = npc.level().getBlockState(next);
        }
        float hardness = state.getDestroySpeed(npc.level(), next);
        if (state.isAir() || hardness < 0.0F || hardness > 3.0F
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BedBlock
                || npc.level().getBlockEntity(next) != null) {
            return;
        }
        controller.equipBestToolForBlock(npc, state);
        controller.lookAt(npc, net.minecraft.world.phys.Vec3.atCenterOf(next));
        controller.swingHand(npc);
        npc.level().destroyBlock(next, true, npc, 512);
    }

    private static int findBuildingBlock(SimpleContainer bag) {
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (stack.is(Items.DIRT) || stack.is(Items.COBBLESTONE) || stack.is(Items.NETHERRACK)) {
                return slot;
            }
        }
        return -1;
    }
}
