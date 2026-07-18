package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class WanderNode extends ExecutableNode {
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
            if (brain != null && (brain.target != null || brain.blockingMob || brain.floating || brain.seekingLoot
                    || brain.seekingChest || brain.seekingBed || brain.depositing || brain.gatheringResource
                    || brain.seekingCraftingTable || npc.isSleeping())) {
                this.outPort.fire(context);
                return;
            }

            if (brain != null && brain.wanderTarget != null) {
                if (npc.getNpcNavigation().shouldAbandonTarget()) {
                    brain.wanderTarget = null;
                    npc.getNpcNavigation().markTargetAbandoned();
                } else if (npc.distanceToSqr(brain.wanderTarget) <= 1.0D) {
                    brain.wanderTarget = null;
                } else {
                    controller.moveTo(npc, brain.wanderTarget, 0.22D);
                    this.outPort.fire(context);
                    return;
                }
            }

            RandomSource random = npc.level().getRandom();
            Vec3 position = npc.position();

            double offsetX = (random.nextDouble() * 2.0 - 1.0) * bounds.x;
            double offsetZ = (random.nextDouble() * 2.0 - 1.0) * bounds.z;

            Vec3 target = position.add(offsetX, 0.0, offsetZ);
            if (brain != null) {
                brain.wanderTarget = target;
            }
            controller.moveTo(npc, target, 0.22D);
        }
        this.outPort.fire(context);
    }
}
