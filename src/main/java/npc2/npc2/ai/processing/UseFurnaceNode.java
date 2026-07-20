package npc2.npc2.ai.processing;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.interaction.CarriedStationPlacement;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class UseFurnaceNode extends ExecutableNode {
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;
    private static final int SEARCH_INTERVAL = 40;
    private final int radius;
    public final SignalPort outPort;

    public UseFurnaceNode(String id, int radius) {
        super(id);
        this.radius = radius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain) {
            if (brain.memories.furnaceTarget != null
                    && !BlockInteractionStations.isUsable(brain.npc, brain.memories.furnaceTarget)) clear(brain);
            boolean placementActive = CarriedStationPlacement.isActive(
                    brain, BlockInteractionStations.Kind.FURNACE);
            if (brain.memories.furnaceTarget == null
                    && (placementActive || brain.memories.furnaceSearchCooldown-- <= 0)) {
                brain.memories.furnaceSearchCooldown = SEARCH_INTERVAL;
                BlockInteractionStations.Target candidate = placementActive ? null
                        : BlockInteractionStations.findTarget(
                                brain.npc, BlockInteractionStations.Kind.FURNACE, this.radius);
                if (candidate == null) {
                    CarriedStationPlacement.Result placement = CarriedStationPlacement.tick(
                            brain, Items.FURNACE, BlockInteractionStations.Kind.FURNACE);
                    candidate = placement.target();
                    if (candidate == null && placement.state() != CarriedStationPlacement.State.UNAVAILABLE) {
                        brain.memories.furnaceSearchCooldown = placement.state() == CarriedStationPlacement.State.SEARCHING
                                ? 10 : 0;
                        this.outPort.fire(context);
                        return;
                    }
                }
                if (candidate != null && BlockInteractionStations.claim(brain.npc, candidate)) {
                    CarriedStationPlacement.clear(brain, BlockInteractionStations.Kind.FURNACE);
                    brain.memories.furnaceTarget = candidate;
                    brain.memories.processingFurnace = true;
                } else if (candidate == null) {
                    clear(brain);
                    brain.memories.furnaceSearchCooldown = 0;
                }
            }

            if (brain.memories.furnaceTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    brain.memories.furnaceSearchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.memories.furnaceTarget.approachPosition()) > ARRIVAL_DISTANCE_SQR) {
                    if (!brain.controller.moveTo(brain.npc, brain.memories.furnaceTarget.approachPosition(), 0.24D)) {
                        BlockInteractionStations.forget(brain.npc, BlockInteractionStations.Kind.FURNACE);
                        brain.memories.furnaceTarget = null;
                        brain.memories.processingFurnace = false;
                        brain.npc.getNpcNavigation().markTargetAbandoned();
                        brain.memories.furnaceSearchCooldown = 0;
                        this.outPort.fire(context);
                        return;
                    }
                } else if (brain.npc.level().getBlockEntity(brain.memories.furnaceTarget.blockPos())
                        instanceof AbstractFurnaceBlockEntity furnace) {
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.memories.furnaceTarget.blockPos()));
                    if (!FurnaceProcessing.canService(furnace)
                            || !FurnaceProcessing.service(brain.npc, furnace)) {
                        clear(brain);
                        brain.memories.furnaceSearchCooldown = SEARCH_INTERVAL;
                    }
                } else {
                    clear(brain);
                }
            }
        }
        this.outPort.fire(context);
    }

    private static void clear(NpcBrain brain) {
        BlockInteractionStations.release(brain.npc, BlockInteractionStations.Kind.FURNACE);
        CarriedStationPlacement.clear(brain, BlockInteractionStations.Kind.FURNACE);
        brain.memories.furnaceTarget = null;
        brain.memories.processingFurnace = false;
    }
}
