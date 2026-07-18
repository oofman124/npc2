package npc2.npc2.ai.equipment;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EquipBestWeaponNode extends ExecutableNode {
    public final SignalPort outPort;

    public EquipBestWeaponNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (context.get("Npc") instanceof FakeNpcEntity npc &&
            context.get("Controller") instanceof NpcController controller) {
            NpcBrain brain = context.get("Brain") instanceof NpcBrain value ? value : null;
            if (brain != null && brain.target != null) {
                controller.equipBestWeapon(npc);
            } else if (brain != null && brain.resourceTarget != null) {
                controller.equipBestToolForBlock(npc, npc.level().getBlockState(brain.resourceTarget.blockPos()));
            } else {
                controller.equipBestWeapon(npc);
            }
        }
        this.outPort.fire(context);
    }
}
