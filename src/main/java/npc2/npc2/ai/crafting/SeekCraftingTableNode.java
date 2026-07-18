package npc2.npc2.ai.crafting;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.interaction.CarriedStationPlacement;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SeekCraftingTableNode extends ExecutableNode {
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;
    private static final int CRAFT_INTERVAL = 20;
    private static final int SEARCH_INTERVAL = 60;
    private final int radius;
    private int craftCooldown;
    private int searchCooldown;
    private boolean searchPhaseInitialized;
    public final SignalPort outPort;

    public SeekCraftingTableNode(String id, int radius) {
        super(id);
        this.radius = radius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain) {
            if (!this.searchPhaseInitialized) {
                // Do not leave a fresh crafting plan waiting behind a randomized initial delay.
                this.searchCooldown = 0;
                this.searchPhaseInitialized = true;
            }
            if (brain.craftingTableTarget != null && !CraftingStations.isUsable(brain.npc, brain.craftingTableTarget)) {
                clear(brain);
            }
            if (brain.craftingTableTarget == null && this.searchCooldown-- <= 0) {
                this.searchCooldown = SEARCH_INTERVAL;
                CraftingStations.Target candidate = CraftingStations.findTarget(brain.npc, this.radius);
                if (candidate == null) {
                    BlockInteractionStations.Target placed = CarriedStationPlacement.place(
                            brain, Items.CRAFTING_TABLE, BlockInteractionStations.Kind.CRAFTING_TABLE);
                    if (placed != null) candidate = CraftingStations.fromShared(placed);
                }
                if (candidate != null && CraftingStations.claim(brain.npc, candidate)) {
                    brain.craftingTableTarget = candidate;
                    brain.seekingCraftingTable = true;
                }
            }
            if (brain.craftingTableTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    this.searchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.craftingTableTarget.approachPosition()) > ARRIVAL_DISTANCE_SQR) {
                    brain.controller.moveTo(brain.npc, brain.craftingTableTarget.approachPosition(), 0.25D);
                } else {
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.craftingTableTarget.tablePos()));
                    if (this.craftCooldown-- <= 0) {
                        this.craftCooldown = CRAFT_INTERVAL;
                        brain.controller.swingHand(brain.npc);
                        WorkstationCrafting.craftOne(brain.npc, brain.controller);
                    }
                    if (!WorkstationCrafting.canCraft(brain.npc, brain.controller)) {
                        clear(brain);
                        this.searchCooldown = SEARCH_INTERVAL;
                    }
                }
            }
        }
        this.outPort.fire(context);
    }

    private static void clear(NpcBrain brain) {
        CraftingStations.release(brain.npc);
        brain.craftingTableTarget = null;
        brain.seekingCraftingTable = false;
    }
}
