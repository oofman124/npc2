package npc2.npc2.ai.rest;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class FindBedNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 40;
    private final int searchRadius;
    private int searchCooldown;
    public final SignalPort outPort;

    public FindBedNode(String id, int searchRadius) {
        super(id);
        this.searchRadius = searchRadius;
        this.outPort = new SignalPort("Out", SignalPortMode.SEND, context -> null);
        this.getSignalPorts().put("Out", this.outPort);
    }

    @Override
    protected void onExecute(Context context) {
        if (context != null && context.get("Brain") instanceof NpcBrain brain) {
            if (brain.bedTarget != null && !BedReservations.isStillUsable(brain.npc, brain.bedTarget)) {
                BedReservations.release(brain.npc);
                brain.bedTarget = null;
                brain.seekingBed = false;
            }
            if (brain.bedTarget == null && this.searchCooldown-- <= 0) {
                this.searchCooldown = SEARCH_INTERVAL;
                BedReservations.Target candidate = BedReservations.findTarget(brain.npc, this.searchRadius);
                if (candidate != null && BedReservations.claim(brain.npc, candidate)) {
                    brain.bedTarget = candidate;
                    brain.seekingBed = true;
                }
            }
        }
        this.outPort.fire(context);
    }
}
