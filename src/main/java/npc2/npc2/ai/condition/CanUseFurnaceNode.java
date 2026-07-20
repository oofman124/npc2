package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.interaction.CarriedStationPlacement;
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
        boolean safe = this.brain.memories.target == null && !this.brain.memories.blockingMob && !this.brain.memories.retreating
                && !this.brain.memories.returningHome
                && !this.brain.memories.floating && !this.brain.memories.seekingLoot && !this.brain.memories.seekingChest
                && !this.brain.memories.seekingBed && !this.brain.memories.depositing && !this.brain.memories.gatheringResource
                && !this.brain.memories.seekingCraftingTable && !this.brain.npc.isSleeping();
        boolean requested = this.brain.memories.processingFurnace
                || (!this.brain.memories.plan.shouldGather()
                && this.brain.memories.plan.action() == SurvivalPlanner.Action.FURNACE);
        if (!safe || !requested) {
            if (this.brain.memories.processingFurnace || this.brain.memories.furnaceTarget != null) {
                BlockInteractionStations.release(this.brain.npc, BlockInteractionStations.Kind.FURNACE);
            }
            this.brain.memories.furnaceTarget = null;
            this.brain.memories.processingFurnace = false;
            CarriedStationPlacement.clear(this.brain, BlockInteractionStations.Kind.FURNACE);
            return false;
        }
        // Keep the production branch active while it discovers or places the furnace.
        this.brain.memories.processingFurnace = true;
        return true;
    }
}
