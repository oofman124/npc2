package npc2.npc2.ai.combat;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import org.jspecify.annotations.NullMarked;
import npc2.npc2.ai.NpcBrain;

@NullMarked
public class TargetRangeNode extends ConditionNode {
    private final NpcBrain brain;
    public final double range;

    public TargetRangeNode(String id, NpcBrain brain, double range) {
        super(id);
        this.brain = brain;
        this.range = range;
    }

    @Override
    protected boolean evaluateCondition() {
        return this.brain.targetInRange;
    }
}
