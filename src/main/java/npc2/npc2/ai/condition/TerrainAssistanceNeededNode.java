package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TerrainAssistanceNeededNode extends ConditionNode {
    private static final int CHECK_INTERVAL = 10;
    private final NpcBrain brain;
    private int ticks;

    public TerrainAssistanceNeededNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        if (++this.ticks < CHECK_INTERVAL) {
            return false;
        }
        this.ticks = 0;
        return this.brain.npc.getNpcNavigation().needsRecovery()
                && !this.brain.blockingMob
                && !this.brain.retreating
                && !this.brain.npc.isSleeping();
    }
}
