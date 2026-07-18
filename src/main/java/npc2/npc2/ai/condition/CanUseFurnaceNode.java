package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.survival.SurvivalPlanner;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanUseFurnaceNode extends ConditionNode {
    private final NpcBrain brain;

    public CanUseFurnaceNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean safe = this.brain.target == null && !this.brain.blockingMob && !this.brain.retreating
                && !this.brain.floating && !this.brain.seekingLoot && !this.brain.seekingChest
                && !this.brain.seekingBed && !this.brain.depositing && !this.brain.gatheringResource
                && !this.brain.seekingCraftingTable && !this.brain.npc.isSleeping();
        boolean requested = this.brain.processingFurnace
                || (!this.brain.plan.shouldGather()
                && this.brain.plan.action() == SurvivalPlanner.Action.FURNACE);
        if (!safe || !requested) {
            BlockInteractionStations.release(this.brain.npc, BlockInteractionStations.Kind.FURNACE);
            this.brain.furnaceTarget = null;
            this.brain.processingFurnace = false;
            return false;
        }
        // Keep the production branch active while it discovers or places the furnace.
        this.brain.processingFurnace = true;
        return true;
    }
}
