package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.BlockResourceGathering;
import npc2.npc2.ai.survival.SurvivalNeeds;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanGatherResourcesNode extends ConditionNode {
    private final NpcBrain brain;

    public CanGatherResourcesNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean allowed = this.brain.target == null && !this.brain.blockingMob && !this.brain.retreating
                && !this.brain.floating && !this.brain.seekingLoot && !this.brain.seekingChest
                && !this.brain.depositing && !this.brain.seekingBed && !this.brain.seekingCraftingTable
                && !this.brain.npc.isSleeping() && BlockResourceGathering.needsResources(this.brain.npc, this.brain.controller)
                && SurvivalNeeds.materials(this.brain.npc, this.brain.controller)
                >= SurvivalNeeds.tools(this.brain.npc, this.brain.controller);
        if (!allowed) {
            BlockResourceGathering.release(this.brain.npc);
            this.brain.resourceTarget = null;
            this.brain.gatheringResource = false;
        }
        return allowed;
    }
}
