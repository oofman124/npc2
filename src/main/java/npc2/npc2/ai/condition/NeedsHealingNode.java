package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class NeedsHealingNode extends ConditionNode {
    private final NpcBrain brain;
    private final float healthFraction;

    public NeedsHealingNode(String id, NpcBrain brain, float healthFraction) {
        super(id);
        this.brain = brain;
        this.healthFraction = healthFraction;
    }

    @Override
    protected boolean evaluateCondition() {
        return this.brain.npc.getHealth() < this.brain.npc.getMaxHealth() * this.healthFraction
                && !this.brain.blockingMob
                && (this.brain.target == null || this.brain.npc.distanceTo(this.brain.target) > 6.0D)
                && !this.brain.npc.isSleeping();
    }
}
