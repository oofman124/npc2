package npc2.npc2.ai.combat;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.LivingEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.NpcContext;
import npc2.npc2.ai.rest.NpcHome;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ChaseTargetNode extends ExecutableNode {
    public final double speed;
    public final SignalPort outPort;

    public ChaseTargetNode(String id, double speed) {
        super(id);
        this.speed = speed;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (context.get("Brain") instanceof NpcBrain brain &&
            context.get("Npc") instanceof FakeNpcEntity npc &&
            context.get("Controller") instanceof NpcController controller &&
            context.get(NpcContext.TARGET) instanceof LivingEntity target &&
            (!brain.memories.returningHome || NpcHome.isThreatAtHome(npc, target)) &&
            target.isAlive()) {
            double distanceSqr = npc.distanceToSqr(target);
            boolean targetInRange = distanceSqr <= 4.0D;
            context.set(NpcContext.TARGET_IN_RANGE, targetInRange);

            if (brain.memories.blockingMob || brain.memories.retreating || brain.memories.floating || brain.memories.seekingLoot || brain.memories.seekingChest || brain.memories.seekingBed
                    || brain.memories.depositing || brain.memories.gatheringResource || brain.memories.seekingCraftingTable
                    || brain.memories.processingFurnace || npc.isSleeping()) {
                this.outPort.fire(context);
                return;
            }

            if (!targetInRange) {
                controller.moveTo(npc, target, this.speed);
            } else {
                controller.stopMoving(npc);
            }
        } else {
            context.set(NpcContext.TARGET_IN_RANGE, false);
        }
        this.outPort.fire(context);
    }
}
