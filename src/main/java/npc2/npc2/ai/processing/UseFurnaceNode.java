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
    private int searchCooldown;
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
            if (brain.furnaceTarget != null
                    && !BlockInteractionStations.isUsable(brain.npc, brain.furnaceTarget)) clear(brain);
            if (brain.furnaceTarget == null && this.searchCooldown-- <= 0) {
                this.searchCooldown = SEARCH_INTERVAL;
                BlockInteractionStations.Target candidate = BlockInteractionStations.findTarget(
                        brain.npc, BlockInteractionStations.Kind.FURNACE, this.radius);
                if (candidate == null) {
                    candidate = CarriedStationPlacement.place(
                            brain, Items.FURNACE, BlockInteractionStations.Kind.FURNACE);
                }
                if (candidate != null && BlockInteractionStations.claim(brain.npc, candidate)) {
                    brain.furnaceTarget = candidate;
                    brain.processingFurnace = true;
                }
            }

            if (brain.furnaceTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    this.searchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.furnaceTarget.approachPosition()) > ARRIVAL_DISTANCE_SQR) {
                    brain.controller.moveTo(brain.npc, brain.furnaceTarget.approachPosition(), 0.24D);
                } else if (brain.npc.level().getBlockEntity(brain.furnaceTarget.blockPos())
                        instanceof AbstractFurnaceBlockEntity furnace) {
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.furnaceTarget.blockPos()));
                    if (!FurnaceProcessing.canService(furnace)
                            || !FurnaceProcessing.service(brain.npc, furnace)) {
                        clear(brain);
                        this.searchCooldown = SEARCH_INTERVAL;
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
        brain.furnaceTarget = null;
        brain.processingFurnace = false;
    }
}
