package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Supplies the buoyancy that ground navigation's can-float flag does not provide. */
@NullMarked
public class FloatInWaterNode extends ExecutableNode {
    private static final double ASCENT_SPEED = 0.12D;
    private static final int ESCAPE_SEARCH_RADIUS = 12;
    private static final int ESCAPE_RETRY_TICKS = 20;
    private static final int MAX_PATH_TESTS = 8;
    private static final int[] ESCAPE_Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4};
    public final SignalPort outPort;

    public FloatInWaterNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Brain") instanceof NpcBrain brain
                && context.get("Controller") instanceof NpcController controller
                && npc.isInWater()) {
            Vec3 movement = npc.getDeltaMovement();
            npc.setDeltaMovement(movement.x, Math.max(movement.y, ASCENT_SPEED), movement.z);

            Vec3 escape = brain.memories.waterEscapeTarget;
            if (escape != null && !isDryStandable(npc, BlockPos.containing(escape))) {
                escape = null;
                brain.memories.waterEscapeTarget = null;
                brain.memories.waterEscapeCooldown = 0;
            }
            if (escape == null && brain.memories.waterEscapeCooldown-- <= 0) {
                brain.memories.waterEscapeCooldown = ESCAPE_RETRY_TICKS;
                escape = findDryLand(npc);
                brain.memories.waterEscapeTarget = escape;
            }
            if (escape != null && !controller.moveTo(npc, escape, 0.28D)) {
                controller.stopMoving(npc);
                npc.getNpcNavigation().markTargetAbandoned();
                brain.memories.waterEscapeTarget = null;
                brain.memories.waterEscapeCooldown = ESCAPE_RETRY_TICKS;
            }
        }
        this.outPort.fire(context);
    }

    private static @Nullable Vec3 findDryLand(FakeNpcEntity npc) {
        BlockPos origin = npc.blockPosition();
        int pathTests = 0;
        int dryCandidatesToSkip = npc.getRandom().nextInt(16);
        for (int radius = 1; radius <= ESCAPE_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : ESCAPE_Y_OFFSETS) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (!isDryStandable(npc, candidate)) continue;
                        if (dryCandidatesToSkip-- > 0) continue;
                        if (++pathTests > MAX_PATH_TESTS) return null;
                        if (npc.getNpcNavigation().canReach(candidate)) {
                            return Vec3.atBottomCenterOf(candidate);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isDryStandable(FakeNpcEntity npc, BlockPos pos) {
        return npc.level().getFluidState(pos).isEmpty()
                && npc.level().getFluidState(pos.above()).isEmpty()
                && npc.level().getBlockState(pos).getCollisionShape(npc.level(), pos).isEmpty()
                && npc.level().getBlockState(pos.above()).getCollisionShape(npc.level(), pos.above()).isEmpty()
                && !npc.level().getBlockState(pos.below()).getCollisionShape(npc.level(), pos.below()).isEmpty();
    }
}
