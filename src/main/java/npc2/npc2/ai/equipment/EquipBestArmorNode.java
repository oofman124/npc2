package npc2.npc2.ai.equipment;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcContext;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EquipBestArmorNode extends ExecutableNode {
    public final SignalPort outPort;

    public EquipBestArmorNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (Boolean.TRUE.equals(context.get(NpcContext.EQUIPMENT_UPDATE)) &&
            context.get("Npc") instanceof FakeNpcEntity npc &&
            context.get("Controller") instanceof NpcController controller) {
            controller.equipBestArmor(npc);
        }
        this.outPort.fire(context);
    }
}
