package npc2.npc2.ai.sensing;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.entity.LivingEntity;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SenseEntitiesNode extends ExecutableNode {
    private static final int TARGET_SCAN_INTERVAL = 10;
    private static final double SWITCH_DISTANCE_RATIO_SQR = 0.64D;

    private final double radius;
    private int scanCooldown;
    private boolean scanPhaseInitialized;
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
            LivingEntity current = brain.target;
            if (!this.scanPhaseInitialized) {
                this.scanCooldown = Math.floorMod(npc.getId(), TARGET_SCAN_INTERVAL);
                this.scanPhaseInitialized = true;
            }
            double radiusSqr = this.radius * this.radius;
            boolean currentIsValid = current != null && (brain.hunting
                    ? controller.isValidHuntTarget(npc, current)
                    : controller.isValidCombatTarget(npc, current));
            if (current != null && (!currentIsValid
                    || npc.distanceToSqr(current) > radiusSqr)) {
                current = null;
                brain.hunting = false;
                this.scanCooldown = 0;
            }

            if (this.scanCooldown-- <= 0) {
                LivingEntity hostile = controller.findNearestTarget(npc, this.radius);
                LivingEntity candidate = hostile;
                boolean candidateIsHunt = false;
                if (candidate == null && controller.shouldHuntForResources(npc)) {
                    candidate = controller.findNearestHuntTarget(npc, Math.min(this.radius, 40.0D));
                    candidateIsHunt = candidate != null;
                }
                this.scanCooldown = TARGET_SCAN_INTERVAL;

                if (hostile != null && brain.hunting) {
                    // Survival hunting never takes priority over an actual threat.
                    current = hostile;
                    brain.hunting = false;
                } else if (current == null
                        || candidate == current
                        || (candidate != null
                        && npc.distanceToSqr(candidate) < npc.distanceToSqr(current) * SWITCH_DISTANCE_RATIO_SQR)) {
                    current = candidate;
                    brain.hunting = candidateIsHunt;
                }
            }

            brain.target = current;
            brain.targetInRange = false;
            if (current != null) {
                context.set("Target", current);
            }

        }
        this.outPort.fire(context);
    }
}
