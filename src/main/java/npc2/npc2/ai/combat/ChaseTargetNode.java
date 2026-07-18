package npc2.npc2.ai.combat;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.LivingEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
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
            brain.target instanceof LivingEntity target &&
            target.isAlive()) {
            double distanceSqr = npc.distanceToSqr(target);
            brain.targetInRange = distanceSqr <= 4.0D;

            if (brain.blockingMob || brain.retreating || brain.seekingLoot || brain.seekingChest || brain.seekingBed
                    || brain.depositing || brain.gatheringResource || brain.seekingCraftingTable
                    || brain.processingFurnace || npc.isSleeping()) {
                this.outPort.fire(context);
                return;
            }

            if (!brain.targetInRange) {
                controller.moveTo(npc, target, this.speed);
            } else {
                controller.stopMoving(npc);
            }
        } else if (context.get("Brain") instanceof NpcBrain brain) {
            brain.targetInRange = false;
        }
        this.outPort.fire(context);
    }
}
