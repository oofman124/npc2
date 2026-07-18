package npc2.npc2.ai.crafting;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CraftBasicSuppliesNode extends ExecutableNode {
    public final SignalPort outPort;

    public CraftBasicSuppliesNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain) {
            BasicCrafting.craftOne(brain.npc, brain.controller);
        }
        this.outPort.fire(context);
    }
}
