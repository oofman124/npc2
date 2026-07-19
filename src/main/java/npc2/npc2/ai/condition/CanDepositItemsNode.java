package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.ChestLooting;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanDepositItemsNode extends ConditionNode {
    private static final int CHECK_INTERVAL = 100;
    private final NpcBrain brain;

    public CanDepositItemsNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean safe = this.brain.memories.target == null && !this.brain.memories.blockingMob && !this.brain.memories.retreating
                && !this.brain.memories.returningHome
                && !this.brain.memories.floating && !this.brain.memories.seekingLoot && !this.brain.memories.seekingChest
                && !this.brain.memories.seekingBed && !this.brain.memories.seekingCraftingTable && !this.brain.memories.gatheringResource
                && !this.brain.memories.processingFurnace
                && !this.brain.npc.isSleeping();
        if (!safe || !ChestLooting.hasItemsToDeposit(this.brain.npc, this.brain.controller)) {
            if (!this.brain.memories.seekingChest) ChestLooting.release(this.brain.npc);
            this.brain.memories.chestDepositTarget = null;
            this.brain.memories.depositing = false;
            return false;
        }
        if (this.brain.memories.depositing) return true;
        if (++this.brain.memories.depositCheckTicks < CHECK_INTERVAL) return false;
        this.brain.memories.depositCheckTicks = 0;
        return true;
    }
}
