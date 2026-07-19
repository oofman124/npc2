package npc2.npc2.ai.combat;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class BlockMobNode extends ExecutableNode {
    private static final double RANGED_THREAT_RADIUS = 48.0D;
    private static final int MIN_GUARD_TICKS_AFTER_SHOT = 8;
    private static final int MAX_GUARD_TICKS_AFTER_SHOT = 30;

    public final SignalPort outPort;

    public BlockMobNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (context.get("Brain") instanceof NpcBrain brain
                && context.get("Npc") instanceof FakeNpcEntity npc
                && context.get("Controller") instanceof NpcController controller) {
            LivingEntity threat = findCreeperThreat(brain, npc, controller);
            if (threat == null) {
                threat = findRangedThreat(brain, npc, controller);
            }

            if (threat != null && controller.hasShieldEquipped(npc)) {
                brain.memories.blockingMob = true;
                brain.memories.blockThreat = threat;
                brain.memories.guardTicksRemaining = calculateGuardTicks(npc, threat);
                controller.stopMoving(npc);
                controller.lookAt(npc, threat.getEyePosition());
                controller.raiseShield(npc);
            } else if (brain.memories.blockingMob
                    && controller.hasShieldEquipped(npc)
                    && brain.memories.guardTicksRemaining > 0) {
                brain.memories.guardTicksRemaining--;
                controller.stopMoving(npc);
                if (brain.memories.blockThreat != null && brain.memories.blockThreat.isAlive()) {
                    controller.lookAt(npc, brain.memories.blockThreat.getEyePosition());
                }
                controller.raiseShield(npc);
            } else {
                if (brain.memories.blockingMob) {
                    controller.lowerShield(npc);
                }
                brain.memories.guardTicksRemaining = 0;
                brain.memories.blockingMob = false;
                brain.memories.blockThreat = null;
            }
        }
        this.outPort.fire(context);
    }

    private static LivingEntity findCreeperThreat(NpcBrain brain, FakeNpcEntity npc, NpcController controller) {
        if (brain.memories.target instanceof Creeper creeper && controller.shouldBlockCreeper(npc, creeper)) {
            return creeper;
        }
        return controller.findThreateningCreeper(npc, 8.0D);
    }

    private static LivingEntity findRangedThreat(NpcBrain brain, FakeNpcEntity npc, NpcController controller) {
        if (brain.memories.target != null && controller.shouldBlockRangedAttack(npc, brain.memories.target)) {
            return brain.memories.target;
        }
        double radius = npc.isSleeping() ? RANGED_THREAT_RADIUS * 0.25D : RANGED_THREAT_RADIUS;
        return controller.findThreateningRangedAttacker(npc, radius);
    }

    private static int calculateGuardTicks(FakeNpcEntity npc, LivingEntity threat) {
        // Arrows and crossbow bolts travel roughly a few blocks per tick. Include
        // a small margin for acceleration, server timing, and shield orientation.
        int flightTicks = (int)Math.ceil(npc.distanceTo(threat) / 2.5D) + 4;
        return Math.max(MIN_GUARD_TICKS_AFTER_SHOT, Math.min(MAX_GUARD_TICKS_AFTER_SHOT, flightTicks));
    }
}
