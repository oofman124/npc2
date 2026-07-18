package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.ChestLooting;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanDepositItemsNode extends ConditionNode {
    private static final int CHECK_INTERVAL = 100;
    private final NpcBrain brain;
    private int ticks;

    public CanDepositItemsNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean safe = this.brain.target == null && !this.brain.blockingMob && !this.brain.retreating
                && !this.brain.floating && !this.brain.seekingLoot && !this.brain.seekingChest
                && !this.brain.seekingBed && !this.brain.seekingCraftingTable && !this.brain.gatheringResource
                && !this.brain.npc.isSleeping();
        if (!safe || !ChestLooting.hasItemsToDeposit(this.brain.npc, this.brain.controller)) {
            if (!this.brain.seekingChest) ChestLooting.release(this.brain.npc);
            this.brain.chestDepositTarget = null;
            this.brain.depositing = false;
            return false;
        }
        if (this.brain.depositing) return true;
        if (++this.ticks < CHECK_INTERVAL) return false;
        this.ticks = 0;
        return true;
    }
}
