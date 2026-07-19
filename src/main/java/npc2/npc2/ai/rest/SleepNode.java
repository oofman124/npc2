package npc2.npc2.ai.rest;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.survival.SurvivalNeeds;
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
                && context.get("Controller") instanceof NpcController controller) {
            if (brain.memories.bedTarget == null) {
                if (SurvivalNeeds.shouldSleepOnFloor(brain.npc)) {
                    controller.stopMoving(brain.npc);
                    brain.memories.floorSleepPosition = brain.npc.blockPosition().immutable();
                    brain.memories.floorSleeping = true;
                    brain.memories.seekingBed = false;
                    brain.npc.startSleeping(brain.memories.floorSleepPosition);
                }
                this.outPort.fire(context);
                return;
            }
            brain.memories.seekingBed = true;
            if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                if (NpcHome.isHome(brain.npc, brain.memories.bedTarget.bedPos())) {
                    NpcHome.defer(brain.npc);
                }
                BedReservations.release(brain.npc);
                brain.memories.bedTarget = null;
                brain.memories.seekingBed = false;
                brain.npc.getNpcNavigation().markTargetAbandoned();
                this.outPort.fire(context);
                return;
            }
            if (brain.npc.distanceToSqr(brain.memories.bedTarget.approachPosition()) <= ARRIVAL_DISTANCE_SQR) {
                controller.stopMoving(brain.npc);
                brain.memories.floorSleeping = false;
                brain.memories.floorSleepPosition = null;
                brain.npc.startSleeping(brain.memories.bedTarget.bedPos());
                if (brain.npc.isSleeping()) {
                    NpcHome.remember(brain.npc, brain.memories.bedTarget.bedPos());
                }
                brain.memories.seekingBed = false;
            } else {
                if (!controller.moveTo(brain.npc, brain.memories.bedTarget.approachPosition(), 0.22D)) {
                    if (NpcHome.isHome(brain.npc, brain.memories.bedTarget.bedPos())) {
                        NpcHome.defer(brain.npc);
                    }
                    BedReservations.release(brain.npc);
                    brain.memories.bedTarget = null;
                    brain.memories.seekingBed = false;
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    this.outPort.fire(context);
                    return;
                }
            }
        }
        this.outPort.fire(context);
    }
}
