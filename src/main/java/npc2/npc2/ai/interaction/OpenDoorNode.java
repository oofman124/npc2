package npc2.npc2.ai.interaction;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

/** Opens the wooden door selected by {@link ClosedDoorAheadNode}. */
@NullMarked
public class OpenDoorNode extends ExecutableNode {
    public final SignalPort outPort;

    public OpenDoorNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain && brain.doorTarget != null) {
            BlockState state = brain.npc.level().getBlockState(brain.doorTarget);
            if (state.getBlock() instanceof DoorBlock door && DoorBlock.isWoodenDoor(state) && !door.isOpen(state)) {
                door.setOpen(brain.npc, brain.npc.level(), state, brain.doorTarget, true);
            }
            brain.doorTarget = null;
        }
        this.outPort.fire(context);
    }
}
