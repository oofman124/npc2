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
        boolean allowed = !this.brain.blockingMob
                && !this.brain.retreating
                && !this.brain.seekingBed
                && !this.brain.depositing
                && !this.brain.gatheringResource
                && !this.brain.seekingCraftingTable
                && !this.brain.npc.isSleeping();
        if (!allowed) {
            LootReservations.release(this.brain.npc);
            this.brain.seekingLoot = false;
            this.brain.lootTarget = null;
        }
        return allowed;
    }
}
