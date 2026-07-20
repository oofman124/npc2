package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.NpcTickSchedule;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class InventoryMaintenanceDueNode extends ConditionNode {
    private final NpcBrain brain;
    private final int interval;

    public InventoryMaintenanceDueNode(String id, NpcBrain brain, int interval) {
        super(id);
        this.brain = brain;
        this.interval = interval;
    }

    @Override
    protected boolean evaluateCondition() {
        return NpcTickSchedule.due(this.brain.npc, this.interval, 73)
                && !this.brain.npc.isSleeping() && !this.brain.memories.blockingMob;
    }
}
