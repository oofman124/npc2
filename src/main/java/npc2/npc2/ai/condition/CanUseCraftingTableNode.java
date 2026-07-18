package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.crafting.CraftingStations;
import npc2.npc2.ai.survival.SurvivalPlanner;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanUseCraftingTableNode extends ConditionNode {
    private final NpcBrain brain;

    public CanUseCraftingTableNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean safe = this.brain.target == null && !this.brain.blockingMob && !this.brain.retreating
                && !this.brain.floating && !this.brain.seekingLoot && !this.brain.seekingChest
                && !this.brain.seekingBed && !this.brain.depositing && !this.brain.gatheringResource
                && !this.brain.processingFurnace
                && !this.brain.npc.isSleeping();
        if (!safe || this.brain.plan.shouldGather()
                || this.brain.plan.action() != SurvivalPlanner.Action.CRAFTING_TABLE) {
            CraftingStations.release(this.brain.npc);
            this.brain.craftingTableTarget = null;
            this.brain.seekingCraftingTable = false;
            return false;
        }
        // Claim the work intent before station discovery so idle/wander cannot take over
        // during a search cooldown or while a carried table is being placed.
        this.brain.seekingCraftingTable = true;
        return true;
    }
}
