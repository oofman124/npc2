package npc2.npc2.ai.util;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import npc2.npc2.ai.NpcBrain;
import org.jspecify.annotations.NonNull;

public class DebounceNode extends ConditionNode {
    public final int durationTicks;
    private final NpcBrain brain;

    public DebounceNode(@NonNull String id, NpcBrain brain, int duration) {
        super(id);
        this.brain = brain;
        this.durationTicks = duration;
    }
    @Override
    protected boolean evaluateCondition()
    {
        this.brain.memories.attackDebounceTicks++;
        if (this.brain.memories.attackDebounceTicks >= durationTicks)
        {
            this.brain.memories.attackDebounceTicks = 0;
            return true;
        }
        return false;
    }
}
