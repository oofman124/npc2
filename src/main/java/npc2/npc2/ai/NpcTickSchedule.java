package npc2.npc2.ai;

import npc2.npc2.FakeNpcEntity;

/** Deterministically spreads recurring AI work across NPCs and server ticks. */
public final class NpcTickSchedule {
    private NpcTickSchedule() {
    }

    public static boolean due(FakeNpcEntity npc, int interval, int lane) {
        if (interval <= 1) return true;
        long shiftedTick = npc.level().getGameTime() + Math.floorMod(npc.getId(), interval);
        return Math.floorMod(shiftedTick, interval) == Math.floorMod(lane, interval);
    }
}
