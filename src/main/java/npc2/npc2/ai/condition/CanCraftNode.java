package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.crafting.BasicCrafting;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanCraftNode extends ConditionNode {
    private static final int CHECK_INTERVAL = 100;
    private final NpcBrain brain;
    private int ticks;

    public CanCraftNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        if (++this.ticks < CHECK_INTERVAL) {
            return false;
        }
        this.ticks = 0;
        return this.brain.target == null
                && !this.brain.blockingMob
                && !this.brain.retreating
                && !this.brain.floating
                && !this.brain.depositing
                && !this.brain.gatheringResource
                && !this.brain.seekingCraftingTable
                && !this.brain.npc.isSleeping()
                && BasicCrafting.canCraft(this.brain.npc, this.brain.controller);
    }
}
