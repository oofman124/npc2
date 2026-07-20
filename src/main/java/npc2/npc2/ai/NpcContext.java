package npc2.npc2.ai;

import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.survival.SurvivalPlanner;

import java.util.Map;

/** Names and factories for values passed through an NPC behavior-graph context. */
public final class NpcContext {
    public static final String NPC = "Npc";
    public static final String CONTROLLER = "Controller";
    public static final String BRAIN = "Brain";
    public static final String MEMORIES = "Memories";

    /** Refreshed in the graph context immediately before each Tick event. */
    public static final String PLAN = "Plan";
    public static final String EQUIPMENT_UPDATE = "EquipmentUpdate";
    public static final String DEFENSE_SCAN = "DefenseScan";

    /** Values generated inside one event execution and cleared by its next reset. */
    public static final String TARGET = "Target";
    public static final String TARGET_IN_RANGE = "TargetInRange";
    public static final String DEFENSE_THREAT = "DefenseThreat";

    private NpcContext() {
    }

    public static Map<String, Object> stable(FakeNpcEntity npc, NpcController controller,
                                              NpcBrain brain, NpcMemories memories) {
        return Map.of(
                NPC, npc,
                CONTROLLER, controller,
                BRAIN, brain,
                MEMORIES, memories
        );
    }

    public static Map<String, Object> tick(SurvivalPlanner.Plan plan, boolean equipmentUpdate,
                                           boolean defenseScan) {
        return Map.of(
                PLAN, plan,
                EQUIPMENT_UPDATE, equipmentUpdate,
                DEFENSE_SCAN, defenseScan
        );
    }
}
