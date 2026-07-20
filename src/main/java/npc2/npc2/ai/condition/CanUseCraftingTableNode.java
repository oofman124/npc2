package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.crafting.CraftingStations;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.interaction.CarriedStationPlacement;
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
        boolean safe = this.brain.memories.target == null && !this.brain.memories.blockingMob && !this.brain.memories.retreating
                && !this.brain.memories.returningHome
                && !this.brain.memories.floating && !this.brain.memories.seekingLoot && !this.brain.memories.seekingChest
                && !this.brain.memories.seekingBed && !this.brain.memories.depositing && !this.brain.memories.gatheringResource
                && !this.brain.memories.processingFurnace
                && !this.brain.npc.isSleeping();
        if (!safe || this.brain.memories.plan.shouldGather()
                || this.brain.memories.plan.action() != SurvivalPlanner.Action.CRAFTING_TABLE) {
            if (this.brain.memories.seekingCraftingTable
                    || this.brain.memories.craftingTableTarget != null) {
                CraftingStations.release(this.brain.npc);
            }
            this.brain.memories.craftingTableTarget = null;
            this.brain.memories.seekingCraftingTable = false;
            CarriedStationPlacement.clear(this.brain, BlockInteractionStations.Kind.CRAFTING_TABLE);
            return false;
        }
        // Claim the work intent before station discovery so idle/wander cannot take over
        // during a search cooldown or while a carried table is being placed.
        this.brain.memories.seekingCraftingTable = true;
        return true;
    }
}
