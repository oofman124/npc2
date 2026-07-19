package npc2.npc2.ai.interaction;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

/** Detects a closed wooden door on or immediately beside the active route. */
@NullMarked
public class ClosedDoorAheadNode extends ConditionNode {
    private final NpcBrain brain;

    public ClosedDoorAheadNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        this.brain.memories.doorTarget = null;
        if (this.brain.npc.isSleeping()) {
            return false;
        }

        Path path = this.brain.npc.getNpcNavigation().getPath();
        if (path != null && !path.isDone()) {
            int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3);
            for (int index = path.getNextNodeIndex(); index < end; index++) {
                if (rememberIfClosedWoodenDoor(path.getNodePos(index))) {
                    return true;
                }
            }
        }

        BlockPos origin = this.brain.npc.blockPosition();
        if (rememberIfClosedWoodenDoor(origin) || rememberIfClosedWoodenDoor(origin.above())) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = origin.relative(direction);
            if (rememberIfClosedWoodenDoor(beside) || rememberIfClosedWoodenDoor(beside.above())) {
                return true;
            }
        }
        return false;
    }

    private boolean rememberIfClosedWoodenDoor(BlockPos pos) {
        BlockState state = this.brain.npc.level().getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door
                && DoorBlock.isWoodenDoor(state)
                && !door.isOpen(state)
                && this.brain.npc.distanceToSqr(Vec3.atCenterOf(pos)) <= 9.0D) {
            this.brain.memories.doorTarget = pos.immutable();
            return true;
        }
        return false;
    }
}
