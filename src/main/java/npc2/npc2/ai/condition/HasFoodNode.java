package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HasFoodNode extends ConditionNode {
    private final NpcBrain brain;

    public HasFoodNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        return this.brain.controller.hasFood(this.brain.npc);
    }
}
