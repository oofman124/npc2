package npc2.npc2.ai.movement;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import org.jspecify.annotations.NullMarked;

/** Supplies the buoyancy that ground navigation's can-float flag does not provide. */
@NullMarked
public class FloatInWaterNode extends ExecutableNode {
    private static final double ASCENT_SPEED = 0.12D;
    public final SignalPort outPort;

    public FloatInWaterNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Npc") instanceof FakeNpcEntity npc && npc.isInWater()) {
            Vec3 movement = npc.getDeltaMovement();
            npc.setDeltaMovement(movement.x, Math.max(movement.y, ASCENT_SPEED), movement.z);
        }
        this.outPort.fire(context);
    }
}
