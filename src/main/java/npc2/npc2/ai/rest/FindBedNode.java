package npc2.npc2.ai.rest;

import io.github.oofman124.asterisk.Context;
import io.github.oofman124.asterisk.nodes.ExecutableNode;
import io.github.oofman124.asterisk.ports.SignalPort;
import io.github.oofman124.asterisk.ports.SignalPortMode;
import net.minecraft.core.BlockPos;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class FindBedNode extends ExecutableNode {
    private static final int SEARCH_INTERVAL = 40;
    private final int searchRadius;
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
            if (!brain.memories.bedSearchInitialized) {
                brain.memories.bedSearchCooldown = brain.memories.returningHome
                        ? Math.floorMod(brain.npc.getId() + 1, 3)
                        : Math.floorMod(brain.npc.getId() + 19, SEARCH_INTERVAL);
                brain.memories.bedSearchInitialized = true;
            }
            if (brain.memories.bedTarget != null && !BedReservations.isStillUsable(brain.npc, brain.memories.bedTarget)) {
                BedReservations.release(brain.npc);
                brain.memories.bedTarget = null;
                brain.memories.seekingBed = false;
            }
            if (brain.memories.bedTarget == null && brain.memories.bedSearchCooldown-- <= 0) {
                brain.memories.bedSearchCooldown = SEARCH_INTERVAL;
                BlockPos excludedHome = brain.memories.homeUnavailableUntil > brain.npc.level().getGameTime()
                        ? brain.memories.homeBedPosition : null;
                BedReservations.Target candidate = BedReservations.findTargetExcluding(
                        brain.npc, Math.min(this.searchRadius, NpcHome.NEARBY_BED_RADIUS), excludedHome);
                if (candidate != null) {
                    // A usable local bed avoids an unnecessary trip across the area.
                    brain.memories.returningHome = false;
                } else if (brain.memories.returningHome && brain.memories.homeBedPosition != null) {
                    candidate = BedReservations.findTargetAt(brain.npc, brain.memories.homeBedPosition);
                    if (candidate == null) {
                        NpcHome.defer(brain.npc);
                        excludedHome = brain.memories.homeBedPosition;
                    }
                }
                if (candidate == null) {
                    candidate = BedReservations.findTargetExcluding(brain.npc, this.searchRadius, excludedHome);
                    if (candidate != null && !NpcHome.isHome(brain.npc, candidate.bedPos())) {
                        brain.memories.returningHome = false;
                    }
                }
                if (candidate != null && BedReservations.claim(brain.npc, candidate)) {
                    brain.memories.bedTarget = candidate;
                    brain.memories.seekingBed = true;
                }
            }
        }
        this.outPort.fire(context);
    }
}
