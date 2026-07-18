package npc2.npc2.ai.rest;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SleepNode extends ExecutableNode {
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;
    public final SignalPort outPort;

    public SleepNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Brain") instanceof NpcBrain brain
                && context.get("Controller") instanceof NpcController controller
                && brain.bedTarget != null) {
            brain.seekingBed = true;
            if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                BedReservations.release(brain.npc);
                brain.bedTarget = null;
                brain.seekingBed = false;
                brain.npc.getNpcNavigation().markTargetAbandoned();
                this.outPort.fire(context);
                return;
            }
            if (brain.npc.distanceToSqr(brain.bedTarget.approachPosition()) <= ARRIVAL_DISTANCE_SQR) {
                controller.stopMoving(brain.npc);
                brain.npc.startSleeping(brain.bedTarget.bedPos());
                brain.seekingBed = false;
            } else {
                controller.moveTo(brain.npc, brain.bedTarget.approachPosition(), 0.22D);
            }
        }
        this.outPort.fire(context);
    }
}
