package npc2.npc2.ai.survival;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EatFoodNode extends ExecutableNode {
    private static final int EAT_COOLDOWN = 40;
    private int cooldown;
    public final SignalPort outPort;

    public EatFoodNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (this.cooldown > 0) {
            this.cooldown--;
        } else if (context != null
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller
                && controller.eatBestFood(npc)) {
            this.cooldown = EAT_COOLDOWN;
        }
        this.outPort.fire(context);
    }
}
