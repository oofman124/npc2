package npc2.npc2.ai.survival;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcMemories;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EatFoodNode extends ExecutableNode {
    private static final int EAT_COOLDOWN = 40;
    public final SignalPort outPort;

    public EatFoodNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Memories") instanceof NpcMemories memories
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller) {
            if (memories.eatCooldown > 0) {
                memories.eatCooldown--;
            } else if (controller.eatBestFood(npc)) {
                memories.eatCooldown = EAT_COOLDOWN;
            }
        }
        this.outPort.fire(context);
    }
}
