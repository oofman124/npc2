package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DepositItemsNode extends ExecutableNode {
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;
    private final double radius;
    public final SignalPort outPort;

    public DepositItemsNode(String id, double radius) {
        super(id);
        this.radius = radius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain) {
            if (brain.chestDepositTarget != null
                    && !ChestLooting.isStillDepositable(brain.npc, brain.controller, brain.chestDepositTarget)) {
                clear(brain);
            }
            if (brain.chestDepositTarget == null) {
                ChestLooting.Target candidate = ChestLooting.findDepositTarget(brain.npc, brain.controller, this.radius);
                if (candidate != null && ChestLooting.claim(brain.npc, candidate)) {
                    brain.chestDepositTarget = candidate;
                    brain.depositing = true;
                }
            }
            if (brain.chestDepositTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.chestDepositTarget.approachPosition()) > ARRIVAL_DISTANCE_SQR) {
                    brain.controller.moveTo(brain.npc, brain.chestDepositTarget.approachPosition(), 0.25D);
                } else {
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.swingHand(brain.npc);
                    ChestLooting.deposit(brain.npc, brain.controller, brain.chestDepositTarget);
                    clear(brain);
                }
            }
        }
        this.outPort.fire(context);
    }

    private static void clear(NpcBrain brain) {
        ChestLooting.release(brain.npc);
        brain.chestDepositTarget = null;
        brain.depositing = false;
    }
}
