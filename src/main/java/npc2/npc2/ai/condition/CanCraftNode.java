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
        return this.brain.target == null
                && !this.brain.blockingMob
                && !this.brain.retreating
                && !this.brain.floating
                && !this.brain.depositing
                && !this.brain.gatheringResource
                && !this.brain.seekingCraftingTable
                && !this.brain.processingFurnace
                && !this.brain.npc.isSleeping()
                && !this.brain.plan.shouldGather()
                && this.brain.plan.action() == npc2.npc2.ai.survival.SurvivalPlanner.Action.HAND_CRAFT;
    }
}
