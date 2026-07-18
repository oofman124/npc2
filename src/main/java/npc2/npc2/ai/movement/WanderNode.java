package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class WanderNode extends ExecutableNode {
    private static final int WANDER_INTERVAL = 60;
    private static final int RETRY_INTERVAL = 20;
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;

    public Vec3 bounds = new Vec3(5, 5, 5);
    public final SignalPort outPort;
    private int cooldown;

    public WanderNode(String id, @Nullable Vec3 bounds) {
        super(id);
        if (bounds != null) {
            this.bounds = bounds;
        }
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (context.get("Npc") instanceof FakeNpcEntity npc &&
            context.get("Controller") instanceof NpcController controller) {
            NpcBrain brain = (context.get("Brain") instanceof NpcBrain storedBrain) ? storedBrain : null;
            boolean productionPlanned = brain != null && brain.hasProductionPlan();
            if (brain != null && (brain.target != null || brain.blockingMob || brain.floating || brain.seekingLoot
                    || brain.seekingChest || brain.seekingBed || brain.depositing || brain.gatheringResource
                    || brain.seekingCraftingTable || brain.processingFurnace || productionPlanned || npc.isSleeping())) {
                brain.wanderTarget = null;
                this.outPort.fire(context);
                return;
            }

            if (brain != null && brain.wanderTarget != null) {
                boolean arrived = npc.distanceToSqr(brain.wanderTarget) <= ARRIVAL_DISTANCE_SQR;
                boolean routeFailed = npc.getNpcNavigation().shouldAbandonTarget()
                        || (npc.getNpcNavigation().isDone() && !arrived);
                if (routeFailed) {
                    brain.wanderTarget = null;
                    npc.getNpcNavigation().markTargetAbandoned();
                    this.cooldown = RETRY_INTERVAL;
                } else if (arrived) {
                    brain.wanderTarget = null;
                    controller.stopMoving(npc);
                    this.cooldown = WANDER_INTERVAL;
                } else {
                    boolean moving = controller.moveTo(npc, brain.wanderTarget, 0.22D);
                    if (!moving || !npc.getNpcNavigation().pathActuallyReachesTarget()) {
                        brain.wanderTarget = null;
                        npc.getNpcNavigation().markTargetAbandoned();
                        this.cooldown = RETRY_INTERVAL;
                    }
                    this.outPort.fire(context);
                    return;
                }
            }

            if (this.cooldown > 0) {
                this.cooldown--;
                this.outPort.fire(context);
                return;
            }

            Vec3 target = findReachableTarget(npc);
            this.cooldown = target == null ? RETRY_INTERVAL : WANDER_INTERVAL;
            if (brain != null && target != null) {
                brain.wanderTarget = target;
                if (!controller.moveTo(npc, target, 0.22D)
                        || !npc.getNpcNavigation().pathActuallyReachesTarget()) {
                    brain.wanderTarget = null;
                    npc.getNpcNavigation().markTargetAbandoned();
                    this.cooldown = RETRY_INTERVAL;
                }
            }
        }
        this.outPort.fire(context);
    }

    private Vec3 findReachableTarget(FakeNpcEntity npc) {
        int horizontalRange = Math.max(4, (int)Math.ceil(Math.max(this.bounds.x, this.bounds.z)));
        int verticalRange = Math.max(2, Math.min(8, (int)Math.ceil(this.bounds.y)));
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 candidate = LandRandomPos.getPos(npc, horizontalRange, verticalRange);
            if (candidate != null
                    && npc.distanceToSqr(candidate) > 4.0D
                    && npc.getNpcNavigation().canReach(net.minecraft.core.BlockPos.containing(candidate))) {
                return candidate;
            }
        }
        return null;
    }
}
