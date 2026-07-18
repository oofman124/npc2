package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GatherResourcesNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 60;
    private static final int POST_HARVEST_SEARCH_DELAY = 30;
    private static final int POST_HARVEST_SEARCH_JITTER = 31;
    private static final int WORK_DELAY = 20;
    private final int radius;
    private int searchCooldown;
    private int workTicks;
    private boolean searchPhaseInitialized;
    public final SignalPort outPort;

    public GatherResourcesNode(String id, int radius) {
        super(id);
        this.radius = radius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain) {
            if (!this.searchPhaseInitialized) {
                // A newly-selected gather plan must search immediately. Stagger only
                // subsequent retries; otherwise the NPC looks idle for up to three seconds.
                this.searchCooldown = 0;
                this.searchPhaseInitialized = true;
            }
            if (brain.resourceTarget != null
                    && !BlockResourceGathering.isUsable(brain.npc, brain.controller, brain.resourceTarget)) clear(brain);
            if (brain.resourceTarget == null && this.searchCooldown-- <= 0) {
                this.searchCooldown = SEARCH_INTERVAL;
                BlockResourceGathering.Target candidate = BlockResourceGathering.findTarget(brain.npc, brain.controller, this.radius);
                if (candidate != null && BlockResourceGathering.claim(brain.npc, candidate)) {
                    brain.resourceTarget = candidate;
                    brain.gatheringResource = true;
                }
            }
            if (brain.resourceTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    BlockResourceGathering.avoid(brain.npc, brain.resourceTarget, 200);
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    this.searchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.resourceTarget.approachPosition()) > 2.25D) {
                    this.workTicks = 0;
                    brain.controller.moveTo(brain.npc, brain.resourceTarget.approachPosition(), 0.24D);
                } else {
                    BlockState state = brain.npc.level().getBlockState(brain.resourceTarget.blockPos());
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.resourceTarget.blockPos()));
                    brain.controller.equipBestToolForBlock(brain.npc, state);
                    if (++this.workTicks >= WORK_DELAY) {
                        harvest(brain);
                        clear(brain);
                        this.searchCooldown = POST_HARVEST_SEARCH_DELAY
                                + brain.npc.getRandom().nextInt(POST_HARVEST_SEARCH_JITTER);
                        this.workTicks = 0;
                    } else if (this.workTicks % 5 == 0) brain.controller.swingHand(brain.npc);
                }
            }
        }
        this.outPort.fire(context);
    }

    private static void harvest(NpcBrain brain) {
        BlockPos.MutableBlockPos pos = brain.resourceTarget.blockPos().mutable();
        int limit = brain.resourceTarget.kind() == BlockResourceGathering.Kind.WOOD ? 12 : 1;
        for (int count = 0; count < limit; count++) {
            BlockState state = brain.npc.level().getBlockState(pos);
            if (count > 0 && !state.is(BlockTags.LOGS)) break;
            brain.npc.level().destroyBlock(pos, true, brain.npc, 512);
            pos.move(0, 1, 0);
        }
    }

    private static void clear(NpcBrain brain) {
        BlockResourceGathering.release(brain.npc);
        brain.resourceTarget = null;
        brain.gatheringResource = false;
    }
}
