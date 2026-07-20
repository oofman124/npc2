package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.ChestLooting;
import org.jspecify.annotations.NullMarked;

/** Policy gate for optional chest looting; also clears interrupted chest state. */
@NullMarked
public class CanSeekChestNode extends ConditionNode {
    private final NpcBrain brain;

    public CanSeekChestNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean allowed = this.brain.memories.target == null
                && !this.brain.memories.blockingMob
                && !this.brain.memories.retreating
                && !this.brain.memories.floating
                && !this.brain.memories.returningHome
                && !this.brain.memories.seekingLoot
                && !this.brain.memories.seekingBed
                && !this.brain.memories.depositing
                && !this.brain.memories.gatheringResource
                && !this.brain.memories.seekingCraftingTable
                && !this.brain.memories.processingFurnace
                && !this.brain.npc.isSleeping();
        if (!allowed && (this.brain.memories.seekingChest
                || this.brain.memories.chestLootTarget != null
                || this.brain.memories.chestTarget != null)) {
            if (!this.brain.memories.depositing) ChestLooting.release(this.brain.npc);
            this.brain.memories.chestLootTarget = null;
            this.brain.memories.chestTarget = null;
            this.brain.memories.seekingChest = false;
        }
        return allowed;
    }
}
