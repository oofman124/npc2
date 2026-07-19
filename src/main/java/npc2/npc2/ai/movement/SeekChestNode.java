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
            if (!brain.memories.chestSearchInitialized) {
                brain.memories.chestSearchCooldown = Math.floorMod(npc.getId(), SEARCH_INTERVAL);
                brain.memories.chestSearchInitialized = true;
            }
            if (brain.memories.chestLootTarget != null && !ChestLooting.isStillDesirable(npc, controller, brain.memories.chestLootTarget)) {
                clearTarget(npc, brain);
            }
            if (brain.memories.chestLootTarget == null && brain.memories.chestSearchCooldown-- <= 0) {
                brain.memories.chestSearchCooldown = SEARCH_INTERVAL;
                ChestLooting.Target candidate = ChestLooting.findTarget(npc, controller, this.searchRadius);
                if (candidate != null && ChestLooting.claim(npc, candidate)) {
                    brain.memories.chestLootTarget = candidate;
                }
            }

            if (brain.memories.chestLootTarget != null) {
                brain.memories.seekingChest = true;
                brain.memories.chestTarget = brain.memories.chestLootTarget.chestPos();
                if (npc.getNpcNavigation().shouldAbandonTarget()) {
                    clearTarget(npc, brain);
                    npc.getNpcNavigation().markTargetAbandoned();
                    brain.memories.chestSearchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (npc.distanceToSqr(brain.memories.chestLootTarget.approachPosition()) <= ARRIVAL_DISTANCE_SQR) {
                    controller.stopMoving(npc);
                    ChestLooting.loot(npc, controller, brain.memories.chestLootTarget);
                    clearTarget(npc, brain);
                    brain.memories.chestSearchCooldown = SEARCH_INTERVAL;
                } else {
                    if (!controller.moveTo(npc, brain.memories.chestLootTarget.approachPosition(), 0.25D)) {
                        clearTarget(npc, brain);
                        npc.getNpcNavigation().markTargetAbandoned();
                        brain.memories.chestSearchCooldown = SEARCH_INTERVAL;
                        this.outPort.fire(context);
                        return;
                    }
                }
            } else {
                brain.memories.seekingChest = false;
                brain.memories.chestTarget = null;
            }
        }
        this.outPort.fire(context);
    }

    private void clearTarget(FakeNpcEntity npc, NpcBrain brain) {
        ChestLooting.release(npc);
        brain.memories.chestLootTarget = null;
        brain.memories.seekingChest = false;
        brain.memories.chestTarget = null;
    }
}
