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
            if (brain != null && !brain.allowsWandering()) {
                brain.memories.wanderTarget = null;
                this.outPort.fire(context);
                return;
            }

            if (brain != null && brain.memories.wanderTarget != null) {
                boolean arrived = npc.distanceToSqr(brain.memories.wanderTarget) <= ARRIVAL_DISTANCE_SQR;
                boolean routeFailed = npc.getNpcNavigation().shouldAbandonTarget()
                        || (npc.getNpcNavigation().isDone() && !arrived);
                if (routeFailed) {
                    brain.memories.wanderTarget = null;
                    npc.getNpcNavigation().markTargetAbandoned();
                    brain.memories.wanderCooldown = RETRY_INTERVAL;
                } else if (arrived) {
                    brain.memories.wanderTarget = null;
                    controller.stopMoving(npc);
                    brain.memories.wanderCooldown = WANDER_INTERVAL;
                } else {
                    boolean moving = controller.moveTo(npc, brain.memories.wanderTarget, 0.22D);
                    if (!moving || !npc.getNpcNavigation().pathActuallyReachesTarget()) {
                        brain.memories.wanderTarget = null;
                        npc.getNpcNavigation().markTargetAbandoned();
                        brain.memories.wanderCooldown = RETRY_INTERVAL;
                    }
                    this.outPort.fire(context);
                    return;
                }
            }

            if (brain != null && brain.memories.wanderCooldown > 0) {
                brain.memories.wanderCooldown--;
                this.outPort.fire(context);
                return;
            }

            Vec3 target = findReachableTarget(npc);
            if (brain != null) {
                brain.memories.wanderCooldown = target == null ? RETRY_INTERVAL : WANDER_INTERVAL;
            }
            if (brain != null && target != null) {
                brain.memories.wanderTarget = target;
                if (!controller.moveTo(npc, target, 0.22D)
                        || !npc.getNpcNavigation().pathActuallyReachesTarget()) {
                    brain.memories.wanderTarget = null;
                    npc.getNpcNavigation().markTargetAbandoned();
                    brain.memories.wanderCooldown = RETRY_INTERVAL;
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
