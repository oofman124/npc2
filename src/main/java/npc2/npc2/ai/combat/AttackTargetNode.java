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
public class AttackTargetNode extends ExecutableNode {
    public final SignalPort outPort;

    public AttackTargetNode(String id) {
        super(id);
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
            brain.targetInRange &&
            !brain.blockingMob &&
            !brain.retreating &&
            !brain.seekingLoot &&
            !brain.seekingChest &&
            !brain.seekingBed &&
            !brain.depositing &&
            !brain.gatheringResource &&
            !brain.seekingCraftingTable &&
            !npc.isSleeping() &&
            target.isAlive()) {
            controller.attackEntity(npc, target);
        }
        this.outPort.fire(context);
    }
}
