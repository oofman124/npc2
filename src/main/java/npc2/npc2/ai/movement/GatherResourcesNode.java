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
import npc2.npc2.ai.NpcContext;
import npc2.npc2.ai.survival.SurvivalPlanner;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GatherResourcesNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 60;
    private static final int POST_HARVEST_SEARCH_DELAY = 30;
    private static final int POST_HARVEST_SEARCH_JITTER = 31;
    private static final int WORK_DELAY = 20;
    private final int radius;
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
            SurvivalPlanner.Plan plan = context.get(NpcContext.PLAN) instanceof SurvivalPlanner.Plan tickPlan
                    ? tickPlan : brain.memories.plan;
            if (!brain.memories.resourceSearchInitialized) {
                // A newly-selected gather plan must search immediately. Stagger only
                // subsequent retries; otherwise the NPC looks idle for up to three seconds.
                brain.memories.resourceSearchCooldown = 0;
                brain.memories.resourceSearchInitialized = true;
            }
            if (brain.memories.resourceTarget != null
                    && !BlockResourceGathering.isUsable(brain.npc, brain.controller, brain.memories.resourceTarget)) clear(brain);
            if (brain.memories.resourceTarget == null && brain.memories.resourceSearchCooldown-- <= 0) {
                brain.memories.resourceSearchCooldown = SEARCH_INTERVAL;
                BlockResourceGathering.Target candidate = BlockResourceGathering.findTarget(brain.npc, brain.controller, this.radius);
                if (candidate != null && BlockResourceGathering.claim(brain.npc, candidate)) {
                    brain.memories.resourceTarget = candidate;
                    brain.memories.gatheringResource = true;
                } else if (candidate == null
                        && brain.memories.recordResourceMiss(brain.npc, plan)) {
                    // Replan on the next tick and immediately test the next weighted need.
                    // Ordinary wandering is suppressed while that gather plan is active.
                    brain.memories.resourceSearchCooldown = 0;
                }
            }
            if (brain.memories.resourceTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    BlockResourceGathering.avoid(brain.npc, brain.memories.resourceTarget, 200);
                    clear(brain);
                    brain.npc.getNpcNavigation().markTargetAbandoned();
                    brain.memories.resourceSearchCooldown = SEARCH_INTERVAL;
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.memories.resourceTarget.approachPosition()) > 2.25D) {
                    brain.memories.resourceWorkTicks = 0;
                    if (!brain.controller.moveTo(brain.npc, brain.memories.resourceTarget.approachPosition(), 0.24D)) {
                        BlockResourceGathering.avoid(brain.npc, brain.memories.resourceTarget, 200);
                        clear(brain);
                        brain.npc.getNpcNavigation().markTargetAbandoned();
                        brain.memories.resourceSearchCooldown = SEARCH_INTERVAL;
                        this.outPort.fire(context);
                        return;
                    }
                } else {
                    BlockState state = brain.npc.level().getBlockState(brain.memories.resourceTarget.blockPos());
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.memories.resourceTarget.blockPos()));
                    brain.controller.equipBestToolForBlock(brain.npc, state);
                    if (++brain.memories.resourceWorkTicks >= WORK_DELAY) {
                        if (harvest(brain)) {
                            brain.memories.recordResourceFound(brain.memories.resourceTarget.kind().resource());
                        }
                        clear(brain);
                        brain.memories.resourceSearchCooldown = POST_HARVEST_SEARCH_DELAY
                                + brain.npc.getRandom().nextInt(POST_HARVEST_SEARCH_JITTER);
                        brain.memories.resourceWorkTicks = 0;
                    } else if (brain.memories.resourceWorkTicks % 5 == 0) brain.controller.swingHand(brain.npc);
                }
            }
        }
        this.outPort.fire(context);
    }

    private static boolean harvest(NpcBrain brain) {
        BlockPos.MutableBlockPos pos = brain.memories.resourceTarget.blockPos().mutable();
        int limit = brain.memories.resourceTarget.kind() == BlockResourceGathering.Kind.WOOD ? 12 : 1;
        boolean harvested = false;
        for (int count = 0; count < limit; count++) {
            BlockState state = brain.npc.level().getBlockState(pos);
            if (count > 0 && !state.is(BlockTags.LOGS)) break;
            harvested |= brain.npc.level().destroyBlock(pos, true, brain.npc, 512);
            pos.move(0, 1, 0);
        }
        return harvested;
    }

    private static void clear(NpcBrain brain) {
        BlockResourceGathering.release(brain.npc);
        brain.memories.resourceTarget = null;
        brain.memories.gatheringResource = false;
    }
}
