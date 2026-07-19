package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.survival.SurvivalNeeds;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HasBedTargetNode extends ConditionNode {
    private final NpcBrain brain;

    public HasBedTargetNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        return !this.brain.npc.isSleeping()
                && (this.brain.memories.bedTarget != null
                || SurvivalNeeds.shouldSleepOnFloor(this.brain.npc));
    }
}
