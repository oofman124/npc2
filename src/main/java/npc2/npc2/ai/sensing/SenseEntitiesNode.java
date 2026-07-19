package npc2.npc2.ai.sensing;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.LivingEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.NpcContext;
import npc2.npc2.ai.rest.NpcHome;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SenseEntitiesNode extends ExecutableNode {
    private static final int TARGET_SCAN_INTERVAL = 10;
    private static final double SWITCH_DISTANCE_RATIO_SQR = 0.64D;

    private final double radius;
    public final SignalPort outPort;

    public SenseEntitiesNode(String id, double radius) {
        super(id);
        this.radius = radius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context == null) {
            return;
        }

        if (context.get("Brain") instanceof NpcBrain brain &&
            context.get("Npc") instanceof FakeNpcEntity npc &&
            context.get("Controller") instanceof NpcController controller) {
            LivingEntity current = brain.memories.target;
            if (!brain.memories.targetScanInitialized) {
                brain.memories.targetScanCooldown = Math.floorMod(npc.getId(), TARGET_SCAN_INTERVAL);
                brain.memories.targetScanInitialized = true;
            }
            double effectiveRadius = npc.isSleeping() ? this.radius * 0.25D : this.radius;
            double radiusSqr = effectiveRadius * effectiveRadius;
            boolean currentIsValid = current != null && (!brain.memories.returningHome
                    || NpcHome.isThreatAtHome(npc, current)) && (brain.memories.hunting
                    ? controller.isValidHuntTarget(npc, current)
                    : controller.isValidCombatTarget(npc, current));
            if (current != null && (!currentIsValid
                    || npc.distanceToSqr(current) > radiusSqr)) {
                current = null;
                brain.memories.hunting = false;
                brain.memories.targetScanCooldown = 0;
            }

            if (brain.memories.targetScanCooldown-- <= 0) {
                LivingEntity hostile = brain.memories.returningHome
                        ? NpcHome.findThreat(npc)
                        : controller.findNearestTarget(npc, effectiveRadius);
                LivingEntity candidate = hostile;
                boolean candidateIsHunt = false;
                if (!brain.memories.returningHome && !npc.isSleeping()
                        && candidate == null && controller.shouldHuntForResources(npc)) {
                    candidate = controller.findNearestHuntTarget(npc, Math.min(effectiveRadius, 40.0D));
                    candidateIsHunt = candidate != null;
                }
                brain.memories.targetScanCooldown = TARGET_SCAN_INTERVAL;

                if (hostile != null && brain.memories.hunting) {
                    // Survival hunting never takes priority over an actual threat.
                    current = hostile;
                    brain.memories.hunting = false;
                } else if (current == null
                        || candidate == current
                        || (candidate != null
                        && npc.distanceToSqr(candidate) < npc.distanceToSqr(current) * SWITCH_DISTANCE_RATIO_SQR)) {
                    current = candidate;
                    brain.memories.hunting = candidateIsHunt;
                }
            }

            brain.memories.target = current;
            context.set(NpcContext.TARGET_IN_RANGE, false);
            if (current != null) {
                context.set(NpcContext.TARGET, current);
            }

        }
        this.outPort.fire(context);
    }
}
