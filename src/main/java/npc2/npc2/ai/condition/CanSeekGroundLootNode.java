package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.LootReservations;
import org.jspecify.annotations.NullMarked;

/** Policy gate for opportunistic ground-loot collection. */
@NullMarked
public class CanSeekGroundLootNode extends ConditionNode {
    private final NpcBrain brain;

    public CanSeekGroundLootNode(String id, NpcBrain brain) {
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
                && !this.brain.memories.seekingChest
                && !this.brain.memories.seekingBed
                && !this.brain.memories.depositing
                && !this.brain.memories.gatheringResource
                && !this.brain.memories.seekingCraftingTable
                && !this.brain.memories.processingFurnace
                && !this.brain.npc.isSleeping();
        if (!allowed && (this.brain.memories.seekingLoot
                || this.brain.memories.lootTarget != null)) {
            LootReservations.release(this.brain.npc);
            this.brain.memories.seekingLoot = false;
            this.brain.memories.lootTarget = null;
        }
        return allowed;
    }
}
