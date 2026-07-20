package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.NpcTickSchedule;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TerrainAssistanceNeededNode extends ConditionNode {
    private static final int CHECK_INTERVAL = 10;
    private final NpcBrain brain;

    public TerrainAssistanceNeededNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        return NpcTickSchedule.due(this.brain.npc, CHECK_INTERVAL, 1)
                && this.brain.npc.getNpcNavigation().needsRecovery()
                && !this.brain.memories.blockingMob
                && !this.brain.memories.retreating
                && !this.brain.memories.floating
                && !this.brain.npc.isSleeping();
    }
}
