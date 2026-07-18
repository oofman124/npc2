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
    private int guardTicksRemaining;

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
                brain.blockingMob = true;
                brain.blockThreat = threat;
                this.guardTicksRemaining = calculateGuardTicks(npc, threat);
                controller.stopMoving(npc);
                controller.lookAt(npc, threat.getEyePosition());
                controller.raiseShield(npc);
            } else if (brain.blockingMob
                    && controller.hasShieldEquipped(npc)
                    && this.guardTicksRemaining > 0) {
                this.guardTicksRemaining--;
                controller.stopMoving(npc);
                if (brain.blockThreat != null && brain.blockThreat.isAlive()) {
                    controller.lookAt(npc, brain.blockThreat.getEyePosition());
                }
                controller.raiseShield(npc);
            } else {
                if (brain.blockingMob) {
                    controller.lowerShield(npc);
                }
                this.guardTicksRemaining = 0;
                brain.blockingMob = false;
                brain.blockThreat = null;
            }
        }
        this.outPort.fire(context);
    }

    private static LivingEntity findCreeperThreat(NpcBrain brain, FakeNpcEntity npc, NpcController controller) {
        if (brain.target instanceof Creeper creeper && controller.shouldBlockCreeper(npc, creeper)) {
            return creeper;
        }
        return controller.findThreateningCreeper(npc, 8.0D);
    }

    private static LivingEntity findRangedThreat(NpcBrain brain, FakeNpcEntity npc, NpcController controller) {
        if (brain.target != null && controller.shouldBlockRangedAttack(npc, brain.target)) {
            return brain.target;
        }
        return controller.findThreateningRangedAttacker(npc, RANGED_THREAT_RADIUS);
    }

    private static int calculateGuardTicks(FakeNpcEntity npc, LivingEntity threat) {
        // Arrows and crossbow bolts travel roughly a few blocks per tick. Include
        // a small margin for acceleration, server timing, and shield orientation.
        int flightTicks = (int)Math.ceil(npc.distanceTo(threat) / 2.5D) + 4;
        return Math.max(MIN_GUARD_TICKS_AFTER_SHOT, Math.min(MAX_GUARD_TICKS_AFTER_SHOT, flightTicks));
    }
}
