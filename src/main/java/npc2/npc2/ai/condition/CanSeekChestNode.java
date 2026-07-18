package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.movement.ChestLooting;
import org.jspecify.annotations.NullMarked;

/** Policy gate for optional chest looting; also clears interrupted chest state. */
@NullMarked
public class CanSeekChestNode extends ConditionNode {
    private final NpcBrain brain;
    private final double safeCombatDistance;

    public CanSeekChestNode(String id, NpcBrain brain, double safeCombatDistance) {
        super(id);
        this.brain = brain;
        this.safeCombatDistance = safeCombatDistance;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean combatTooClose = this.brain.target != null
                && this.brain.target.isAlive()
                && this.brain.npc.distanceTo(this.brain.target) < this.safeCombatDistance;
        boolean allowed = !this.brain.blockingMob
                && !this.brain.retreating
                && !this.brain.seekingLoot
                && !this.brain.seekingBed
                && !this.brain.depositing
                && !this.brain.gatheringResource
                && !this.brain.seekingCraftingTable
                && !this.brain.processingFurnace
                && !this.brain.npc.isSleeping()
                && !combatTooClose;
        if (!allowed) {
            if (!this.brain.depositing) ChestLooting.release(this.brain.npc);
            this.brain.chestLootTarget = null;
            this.brain.chestTarget = null;
            this.brain.seekingChest = false;
        }
        return allowed;
    }
}
