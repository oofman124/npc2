package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanCraftNode extends ConditionNode {
    private final NpcBrain brain;

    public CanCraftNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        return this.brain.memories.target == null
                && !this.brain.memories.blockingMob
                && !this.brain.memories.retreating
                && !this.brain.memories.returningHome
                && !this.brain.memories.floating
                && !this.brain.memories.depositing
                && !this.brain.memories.gatheringResource
                && !this.brain.memories.seekingCraftingTable
                && !this.brain.memories.processingFurnace
                && !this.brain.npc.isSleeping()
                && !this.brain.memories.plan.shouldGather()
                && this.brain.memories.plan.action() == npc2.npc2.ai.survival.SurvivalPlanner.Action.HAND_CRAFT;
    }
}
