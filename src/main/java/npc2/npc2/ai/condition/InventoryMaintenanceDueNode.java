package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class InventoryMaintenanceDueNode extends ConditionNode {
    private final NpcBrain brain;
    private final int interval;
    private int ticks;

    public InventoryMaintenanceDueNode(String id, NpcBrain brain, int interval) {
        super(id);
        this.brain = brain;
        this.interval = interval;
    }

    @Override
    protected boolean evaluateCondition() {
        if (++this.ticks < this.interval) {
            return false;
        }
        this.ticks = 0;
        return !this.brain.npc.isSleeping() && !this.brain.blockingMob;
    }
}
