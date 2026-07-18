package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.crafting.CraftingStations;
import npc2.npc2.ai.crafting.WorkstationCrafting;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CanUseCraftingTableNode extends ConditionNode {
    private static final int CHECK_INTERVAL = 80;
    private final NpcBrain brain;
    private int ticks;

    public CanUseCraftingTableNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean safe = this.brain.target == null && !this.brain.blockingMob && !this.brain.retreating
                && !this.brain.floating && !this.brain.seekingLoot && !this.brain.seekingChest
                && !this.brain.seekingBed && !this.brain.depositing && !this.brain.gatheringResource
                && !this.brain.npc.isSleeping();
        if (!safe || !WorkstationCrafting.canCraft(this.brain.npc, this.brain.controller)) {
            CraftingStations.release(this.brain.npc);
            this.brain.craftingTableTarget = null;
            this.brain.seekingCraftingTable = false;
            return false;
        }
        if (this.brain.seekingCraftingTable) return true;
        if (++this.ticks < CHECK_INTERVAL) return false;
        this.ticks = 0;
        return true;
    }
}
