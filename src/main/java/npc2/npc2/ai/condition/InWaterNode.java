package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

/** Updates the water state every tick and emits while buoyancy is needed. */
@NullMarked
public class InWaterNode extends ConditionNode {
    private final NpcBrain brain;

    public InWaterNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        this.brain.memories.floating = this.brain.npc.isInWater();
        return this.brain.memories.floating;
    }
}
