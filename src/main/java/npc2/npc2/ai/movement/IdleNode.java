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

@NullMarked
public class IdleNode extends ExecutableNode {
    private final double radius;
    public final SignalPort outPort;

    public IdleNode(String id, double radius) {
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
            if (brain.memories.target != null || brain.memories.wanderTarget != null || brain.memories.blockingMob || brain.memories.floating || brain.memories.seekingLoot
                    || brain.memories.seekingChest || brain.memories.seekingBed || brain.memories.depositing || brain.memories.gatheringResource
                    || brain.memories.seekingCraftingTable || brain.memories.processingFurnace || npc.isSleeping()
                    || brain.hasPlannedWork()) {
                this.outPort.fire(context);
                return;
            }

            brain.memories.idleTicks++;
            if ((brain.memories.idleTicks % 40) != 0) {
                this.outPort.fire(context);
                return;
            }

            RandomSource random = npc.level().getRandom();
            Vec3 lookPoint = npc.position().add(
                (random.nextDouble() * 2.0 - 1.0) * this.radius,
                0.5D + random.nextDouble() * 1.5D,
                (random.nextDouble() * 2.0 - 1.0) * this.radius
            );
            controller.lookAt(npc, lookPoint);
        }
        this.outPort.fire(context);
    }
}
