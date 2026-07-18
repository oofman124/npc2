package npc2.npc2.ai.condition;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import net.minecraft.world.level.Level;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.rest.BedReservations;
import npc2.npc2.ai.survival.SurvivalNeeds;
import org.jspecify.annotations.NullMarked;

/** Gates sleeping to safe, dark overworld periods and clears interrupted searches. */
@NullMarked
public class CanSleepNode extends ConditionNode {
    private final NpcBrain brain;

    public CanSleepNode(String id, NpcBrain brain) {
        super(id);
        this.brain = brain;
    }

    @Override
    protected boolean evaluateCondition() {
        boolean allowed = this.brain.npc.level().dimension().equals(Level.OVERWORLD)
                && SurvivalNeeds.isNight(this.brain.npc)
                && !this.brain.npc.isSleeping()
                && this.brain.target == null
                && !this.brain.blockingMob
                && !this.brain.retreating
                && !this.brain.seekingLoot
                && !this.brain.seekingChest
                && !this.brain.depositing
                && !this.brain.gatheringResource
                && !this.brain.seekingCraftingTable;

        if (!allowed && !this.brain.npc.isSleeping()) {
            BedReservations.release(this.brain.npc);
            this.brain.bedTarget = null;
            this.brain.seekingBed = false;
        }
        return allowed;
    }
}
