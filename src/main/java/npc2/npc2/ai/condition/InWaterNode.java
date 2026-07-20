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
        boolean wasFloating = this.brain.memories.floating;
        boolean floating = this.brain.npc.isInWater();
        if (floating != wasFloating) {
            // Water recovery owns navigation until dry land is reached. Cancel the
            // previous work path so it cannot keep recalculating under the float node.
            this.brain.controller.stopMoving(this.brain.npc);
            this.brain.npc.getNpcNavigation().markTargetAbandoned();
            this.brain.memories.waterEscapeTarget = null;
            this.brain.memories.waterEscapeCooldown = 0;
        }
        this.brain.memories.floating = floating;
        return this.brain.memories.floating;
    }
}
