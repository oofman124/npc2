package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.BlockResourceGathering;
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
        boolean allowed = this.brain.memories.target == null && !this.brain.memories.blockingMob && !this.brain.memories.retreating
                && !this.brain.memories.returningHome
                && !this.brain.memories.floating && !this.brain.memories.seekingLoot && !this.brain.memories.seekingChest
                && !this.brain.memories.depositing && !this.brain.memories.seekingBed && !this.brain.memories.seekingCraftingTable
                && !this.brain.memories.processingFurnace && !this.brain.npc.isSleeping()
                && this.brain.memories.plan.shouldGather();
        if (!allowed) {
            BlockResourceGathering.release(this.brain.npc);
            this.brain.memories.resourceTarget = null;
            this.brain.memories.gatheringResource = false;
        }
        return allowed;
    }
}
