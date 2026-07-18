package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.item.ItemEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SeekLootNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 20;
    private static final int UNREACHABLE_COOLDOWN = 160;
    private final double searchRadius;
    private final double safeCombatDistance;
    private int searchCooldown;
    private boolean searchPhaseInitialized;
    public final SignalPort outPort;

    public SeekLootNode(String id, double searchRadius, double safeCombatDistance) {
        super(id);
        this.searchRadius = searchRadius;
        this.safeCombatDistance = safeCombatDistance;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (context.get("Brain") instanceof NpcBrain brain &&
            context.get("Npc") instanceof FakeNpcEntity npc &&
            context.get("Controller") instanceof NpcController controller) {

            if (!this.searchPhaseInitialized) {
                this.searchCooldown = Math.floorMod(npc.getId(), SEARCH_INTERVAL);
                this.searchPhaseInitialized = true;
            }

            ItemEntity loot = brain.lootTarget;
            if (loot != null && (!loot.isAlive() || loot.getItem().isEmpty()
                    || controller.getDesirableLootScore(npc, loot.getItem()) <= 0.0D
                    || !LootReservations.isAvailable(npc, loot))) {
                clear(npc, brain);
                loot = null;
            }
            if (loot == null && this.searchCooldown-- <= 0) {
                this.searchCooldown = SEARCH_INTERVAL;
                loot = controller.findDesirableGroundLoot(
                        npc, this.searchRadius, brain.target, this.safeCombatDistance);
                if (loot != null && LootReservations.claim(npc, loot)) {
                    brain.lootTarget = loot;
                } else {
                    loot = null;
                }
            }

            if (loot != null) {
                if (npc.getNpcNavigation().shouldAbandonTarget()) {
                    LootReservations.avoid(npc, loot, UNREACHABLE_COOLDOWN);
                    npc.getNpcNavigation().markTargetAbandoned();
                    brain.seekingLoot = false;
                    brain.lootTarget = null;
                    this.searchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                brain.seekingLoot = true;
                brain.lootTarget = loot;
                controller.moveTo(npc, LootReservations.getApproachPosition(npc, loot), 0.25D);
            } else {
                clear(npc, brain);
            }
        }
        this.outPort.fire(context);
    }

    private static void clear(FakeNpcEntity npc, NpcBrain brain) {
        LootReservations.release(npc);
        brain.seekingLoot = false;
        brain.lootTarget = null;
    }
}
