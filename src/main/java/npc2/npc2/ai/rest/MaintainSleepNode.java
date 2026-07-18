package npc2.npc2.ai.rest;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.ai.NpcBrain;
import npc2.npc2.ai.survival.SurvivalNeeds;
import net.minecraft.world.level.block.BedBlock;
import org.jspecify.annotations.NullMarked;

/** Wakes sleeping NPCs at daylight or when a combat target appears. */
@NullMarked
public class MaintainSleepNode extends ExecutableNode {
    public final SignalPort outPort;

    public MaintainSleepNode(String id) {
        super(id);
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain && brain.npc.isSleeping()) {
            boolean bedMissing = brain.npc.getSleepingPos()
                    .map(pos -> !(brain.npc.level().getBlockState(pos).getBlock() instanceof BedBlock))
                    .orElse(true);
            if (!SurvivalNeeds.isNight(brain.npc) || bedMissing || brain.target != null || brain.blockingMob) {
                brain.npc.stopSleeping();
                BedReservations.release(brain.npc);
                brain.bedTarget = null;
            }
        }
        this.outPort.fire(context);
    }
}
