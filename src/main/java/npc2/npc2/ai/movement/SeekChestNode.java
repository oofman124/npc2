package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SeekChestNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 20;
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;

    private final double searchRadius;
    private int searchCooldown;
    private boolean searchPhaseInitialized;
    public final SignalPort outPort;

    public SeekChestNode(String id, double searchRadius) {
        super(id);
        this.searchRadius = searchRadius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }
        if (context.get("Brain") instanceof NpcBrain brain
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller) {
            if (!this.searchPhaseInitialized) {
                this.searchCooldown = Math.floorMod(npc.getId(), SEARCH_INTERVAL);
                this.searchPhaseInitialized = true;
            }
            if (brain.chestLootTarget != null && !ChestLooting.isStillDesirable(npc, controller, brain.chestLootTarget)) {
                clearTarget(npc, brain);
            }
            if (brain.chestLootTarget == null && this.searchCooldown-- <= 0) {
                this.searchCooldown = SEARCH_INTERVAL;
                ChestLooting.Target candidate = ChestLooting.findTarget(npc, controller, this.searchRadius);
                if (candidate != null && ChestLooting.claim(npc, candidate)) {
                    brain.chestLootTarget = candidate;
                }
            }

            if (brain.chestLootTarget != null) {
                brain.seekingChest = true;
                brain.chestTarget = brain.chestLootTarget.chestPos();
                if (npc.getNpcNavigation().shouldAbandonTarget()) {
                    clearTarget(npc, brain);
                    npc.getNpcNavigation().markTargetAbandoned();
                    this.searchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (npc.distanceToSqr(brain.chestLootTarget.approachPosition()) <= ARRIVAL_DISTANCE_SQR) {
                    controller.stopMoving(npc);
                    ChestLooting.loot(npc, controller, brain.chestLootTarget);
                    clearTarget(npc, brain);
                    this.searchCooldown = SEARCH_INTERVAL;
                } else {
                    controller.moveTo(npc, brain.chestLootTarget.approachPosition(), 0.25D);
                }
            } else {
                brain.seekingChest = false;
                brain.chestTarget = null;
            }
        }
        this.outPort.fire(context);
    }

    private void clearTarget(FakeNpcEntity npc, NpcBrain brain) {
        ChestLooting.release(npc);
        brain.chestLootTarget = null;
        brain.seekingChest = false;
        brain.chestTarget = null;
    }
}
