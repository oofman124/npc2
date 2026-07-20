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
import npc2.npc2.ai.NpcTickSchedule;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class GatherResourcesNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 60;
    private static final int POST_HARVEST_SEARCH_DELAY = 30;
    private static final int POST_HARVEST_SEARCH_JITTER = 31;
    private static final int FAILED_TARGET_SEARCH_DELAY = 10;
    private static final int WORK_DELAY = 20;
    private static final int SEARCH_BLOCK_BUDGET = 8192;
    private static final int EXPLORATION_SEARCH_DELAY = 100;
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
            if (!brain.memories.resourceSearchInitialized) {
                // A newly-selected gather plan must search immediately. Stagger only
                // subsequent retries; otherwise the NPC looks idle for up to three seconds.
                brain.memories.resourceSearchCooldown = 0;
                brain.memories.resourceSearchInitialized = true;
            }
            if (brain.memories.resourceTarget != null
                    && !BlockResourceGathering.isUsable(brain.npc, brain.controller, brain.memories.resourceTarget)) {
                // A completed/reweighted need is not evidence that the remembered
                // block vanished. Only remove the memory when the world no longer
                // contains the expected resource.
                if (!BlockResourceGathering.targetStillExists(brain.npc, brain.memories.resourceTarget)) {
                    brain.memories.forgetResource(brain.memories.resourceTarget.kind().resource(),
                            brain.npc.level().dimension(), brain.memories.resourceTarget.blockPos());
                }
                clear(brain);
            }
            if (brain.memories.resourceTarget == null && brain.memories.resourceSearch != null) {
                if (NpcTickSchedule.due(brain.npc, 2, 0)) {
                    BlockResourceGathering.Search search = brain.memories.resourceSearch;
                    BlockResourceGathering.SearchProgress progress = BlockResourceGathering.continueSearch(
                            brain.npc, search, SEARCH_BLOCK_BUDGET);
                    if (progress.target() != null && BlockResourceGathering.claim(brain.npc, progress.target())) {
                        brain.memories.resourceTarget = progress.target();
                        brain.memories.resourceSearch = null;
                        brain.memories.gatheringResource = true;
                        brain.memories.exploringForResources = false;
                    } else if (progress.explorationTarget() != null) {
                        refreshPlanAndExploreIfNeeded(brain, progress.explorationTarget());
                    } else if (progress.complete()) {
                        refreshPlanAndExploreIfNeeded(brain, null);
                    }
                }
            } else if (brain.memories.resourceTarget == null
                    && brain.memories.resourceSearchCooldown-- <= 0) {
                brain.memories.resourceSearchCooldown = SEARCH_INTERVAL;
                brain.memories.resourceSearch = BlockResourceGathering.beginSearch(
                        brain.npc, brain.controller, this.radius);
                // The bounded primary scan is its own stationary search phase. It
                // becomes exploration only if this search completes without a
                // usable local target.
                brain.memories.gatheringResource = false;
                brain.memories.exploringForResources = false;
                brain.memories.wanderTarget = null;
                brain.controller.stopMoving(brain.npc);
            }
            if (brain.memories.resourceTarget != null) {
                if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                    abandonTarget(brain);
                    this.outPort.fire(context);
                    return;
                }
                if (brain.npc.distanceToSqr(brain.memories.resourceTarget.approachPosition()) > 2.25D) {
                    brain.memories.resourceWorkTicks = 0;
                    if (!brain.controller.moveTo(brain.npc, brain.memories.resourceTarget.approachPosition(), 0.24D)) {
                        // Path calculation can fail for one frame while crossing a
                        // cave mouth, step, or chunk boundary. Keep ownership of the
                        // resource and retry until navigation's accumulated failure
                        // threshold says the target is genuinely unreachable.
                        if (brain.npc.getNpcNavigation().shouldAbandonTarget()) {
                            abandonTarget(brain);
                        }
                        this.outPort.fire(context);
                        return;
                    }
                } else {
                    BlockState state = brain.npc.level().getBlockState(brain.memories.resourceTarget.blockPos());
                    brain.controller.stopMoving(brain.npc);
                    brain.controller.lookAt(brain.npc, Vec3.atCenterOf(brain.memories.resourceTarget.blockPos()));
                    brain.controller.equipBestToolForBlock(brain.npc, state);
                    if (++brain.memories.resourceWorkTicks >= WORK_DELAY) {
                        BlockResourceGathering.Kind completedKind = brain.memories.resourceTarget.kind();
                        if (harvest(brain)) {
                            brain.memories.recordResourceSuccess(brain.npc, completedKind.resource());
                        }
                        clear(brain);
                        brain.memories.resourceSearchCooldown = POST_HARVEST_SEARCH_DELAY
                                + brain.npc.getRandom().nextInt(POST_HARVEST_SEARCH_JITTER);
                        // Stay in RESOURCE_SEARCH during this short pause. Do not
                        // wander away from adjacent resources between harvests.
                        brain.memories.exploringForResources = false;
                        brain.memories.wanderTarget = null;
                        brain.memories.resourceWorkTicks = 0;
                    } else if (brain.memories.resourceWorkTicks % 5 == 0) brain.controller.swingHand(brain.npc);
                }
            }
        }
        this.outPort.fire(context);
    }

    private static void abandonTarget(NpcBrain brain) {
        if (brain.memories.resourceTarget == null) return;
        BlockResourceGathering.Kind failedKind = brain.memories.resourceTarget.kind();
        BlockResourceGathering.avoid(brain.npc, brain.memories.resourceTarget, 200);
        brain.memories.recordResourceMiss(brain.npc, failedKind.resource());
        clear(brain);
        brain.npc.getNpcNavigation().markTargetAbandoned();
        // A failed block is not proof that the whole local area is exhausted.
        // Give the primary scanner a chance to choose another remembered/candidate
        // resource before allowing exploration movement.
        brain.memories.resourceSearchCooldown = FAILED_TARGET_SEARCH_DELAY;
        brain.memories.wanderTarget = null;
    }

    private static boolean harvest(NpcBrain brain) {
        BlockPos.MutableBlockPos pos = brain.memories.resourceTarget.blockPos().mutable();
        int limit = brain.memories.resourceTarget.kind() == BlockResourceGathering.Kind.WOOD ? 12 : 1;
        boolean harvested = false;
        for (int count = 0; count < limit; count++) {
            BlockState state = brain.npc.level().getBlockState(pos);
            if (count > 0 && !state.is(BlockTags.LOGS)) break;
            boolean destroyed = brain.npc.level().destroyBlock(pos, true, brain.npc, 512);
            harvested |= destroyed;
            if (destroyed) {
                brain.memories.forgetResource(brain.memories.resourceTarget.kind().resource(),
                        brain.npc.level().dimension(), pos);
            }
            pos.move(0, 1, 0);
        }
        return harvested;
    }

    private static void clear(NpcBrain brain) {
        BlockResourceGathering.release(brain.npc);
        brain.memories.resourceTarget = null;
        brain.memories.resourceSearch = null;
        brain.memories.gatheringResource = false;
        brain.memories.exploringForResources = false;
    }

    private static void beginExploration(NpcBrain brain, @Nullable Vec3 target) {
        BlockResourceGathering.release(brain.npc);
        brain.memories.resourceTarget = null;
        brain.memories.resourceSearch = null;
        brain.memories.gatheringResource = false;
        brain.memories.exploringForResources = true;
        brain.memories.resourceSearchCooldown = EXPLORATION_SEARCH_DELAY;
        brain.memories.wanderTarget = target;
        brain.memories.wanderCooldown = 0;
    }

    private static void refreshPlanAndExploreIfNeeded(NpcBrain brain, @Nullable Vec3 target) {
        brain.memories.plan = npc2.npc2.ai.survival.SurvivalPlanner.create(
                brain.npc, brain.controller);
        if (brain.memories.plan.shouldGather()) {
            beginExploration(brain, target);
        } else {
            clear(brain);
            brain.memories.wanderTarget = null;
        }
    }
}
