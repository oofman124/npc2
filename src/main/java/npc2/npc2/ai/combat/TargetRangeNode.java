package npc2.npc2.ai.combat;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.LivingEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ai.NpcContext;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TargetRangeNode extends ExecutableNode {
    public final double range;
    public final SignalPort outPort;

    public TargetRangeNode(String id, double range) {
        super(id);
        this.range = range;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) return;
        boolean inRange = context.get(NpcContext.NPC) instanceof FakeNpcEntity npc
                && context.get(NpcContext.TARGET) instanceof LivingEntity target
                && npc.distanceToSqr(target) <= this.range * this.range;
        context.set(NpcContext.TARGET_IN_RANGE, inRange);
        if (inRange) {
            this.outPort.fire(context);
        }
    }
}
