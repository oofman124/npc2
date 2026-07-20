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
            if (!brain.memories.craftingSearchInitialized) {
                // Do not leave a fresh crafting plan waiting behind a randomized initial delay.
                brain.memories.craftingSearchCooldown = 0;
                brain.memories.craftingSearchInitialized = true;
            }
            if (brain.memories.craftingTableTarget != null && !CraftingStations.isUsable(brain.npc, brain.memories.craftingTableTarget)) {
                clear(brain);
            }
            boolean placementActive = CarriedStationPlacement.isActive(
                    brain, BlockInteractionStations.Kind.CRAFTING_TABLE);
            if (brain.memories.craftingTableTarget == null
                    && (placementActive || brain.memories.craftingSearchCooldown-- <= 0)) {
                brain.memories.craftingSearchCooldown = SEARCH_INTERVAL;
                CraftingStations.Target candidate = placementActive
                        ? null : CraftingStations.findTarget(brain.npc, this.radius);
                if (candidate == null) {
                    CarriedStationPlacement.Result placement = CarriedStationPlacement.tick(
                            brain, Items.CRAFTING_TABLE, BlockInteractionStations.Kind.CRAFTING_TABLE);
                    if (placement.target() != null) candidate = CraftingStations.fromShared(placement.target());
                    if (candidate == null && placement.state() != CarriedStationPlacement.State.UNAVAILABLE) {
                        brain.memories.craftingSearchCooldown = placement.state() == CarriedStationPlacement.State.SEARCHING
                                ? 10 : 0;
                        this.outPort.fire(context);
                        return;
                    }
                }
                if (candidate != null && CraftingStations.claim(brain.npc, candidate)) {
                    CarriedStationPlacement.clear(brain, BlockInteractionStations.Kind.CRAFTING_TABLE);
                    brain.memories.craftingTableTarget = candidate;
                    brain.memories.seekingCraftingTable = true;
                } else if (candidate == null) {
                    // Station discovery may have invalidated a stale remembered table.
                    // Release the intent now so hand-crafting a replacement can run next tick.
                    clear(brain);
                    brain.memories.craftingSearchCooldown = 0;
                }
            }
            if (brain.memories.craftingTableTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    brain.memories.craftingSearchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.memories.craftingTableTarget.approachPosition()) > ARRIVAL_DISTANCE_SQR) {
                    if (!brain.controller.moveTo(brain.npc, brain.memories.craftingTableTarget.approachPosition(), 0.25D)) {
                        CraftingStations.forget(brain.npc);
                        brain.memories.craftingTableTarget = null;
                        brain.memories.seekingCraftingTable = false;
                        brain.npc.getNpcNavigation().markTargetAbandoned();
                        brain.memories.craftingSearchCooldown = 0;
                        this.outPort.fire(context);
                        return;
                    }
                } else {
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.memories.craftingTableTarget.tablePos()));
                    if (brain.memories.craftingCooldown-- <= 0) {
                        brain.memories.craftingCooldown = CRAFT_INTERVAL;
                        brain.controller.swingHand(brain.npc);
                        WorkstationCrafting.craftOne(brain.npc, brain.controller);
                        if (!WorkstationCrafting.canCraft(brain.npc, brain.controller)) {
                            clear(brain);
                            brain.memories.craftingSearchCooldown = SEARCH_INTERVAL;
                        }
                    }
                }
            }
        }
        this.outPort.fire(context);
    }

    private static void clear(NpcBrain brain) {
        CraftingStations.release(brain.npc);
        CarriedStationPlacement.clear(brain, BlockInteractionStations.Kind.CRAFTING_TABLE);
        brain.memories.craftingTableTarget = null;
        brain.memories.seekingCraftingTable = false;
    }
}
