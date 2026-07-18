package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CriticalHealthNode extends ConditionNode {
    private final NpcBrain brain;
    private final float healthFraction;

    public CriticalHealthNode(String id, NpcBrain brain, float healthFraction) {
        super(id);
        this.brain = brain;
        this.healthFraction = healthFraction;
    }

    @Override
    protected boolean evaluateCondition() {
        this.brain.retreating = this.brain.target != null
                && this.brain.target.isAlive()
                && this.brain.npc.getHealth() < this.brain.npc.getMaxHealth() * this.healthFraction
                && !this.brain.hunting
                && !this.brain.blockingMob;
        return this.brain.retreating;
    }
}
