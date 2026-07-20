package npc2.npc2.ai.survival;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RetreatNode extends ExecutableNode {
    public final SignalPort outPort;

    public RetreatNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Brain") instanceof NpcBrain brain
                && context.get("Controller") instanceof NpcController controller
                && !brain.memories.floating
                && brain.memories.target != null) {
            Vec3 away = brain.npc.position().subtract(brain.memories.target.position());
            if (away.horizontalDistanceSqr() > 0.001D) {
                Vec3 destination = brain.npc.position().add(away.multiply(1.0D, 0.0D, 1.0D).normalize().scale(10.0D));
                controller.moveTo(brain.npc, destination, 0.28D);
            }
        }
        this.outPort.fire(context);
    }
}
