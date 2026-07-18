package npc2.npc2.ai.util;

import io.github.oofman124.asterisk.nodes.ConditionNode;
import org.jspecify.annotations.NonNull;

public class DebounceNode extends ConditionNode {
    public final int durationTicks;
    private int current = 0;

    public DebounceNode(@NonNull String id, int duration) {
        super(id);
        this.durationTicks = duration;
    }
    @Override
    protected boolean evaluateCondition()
    {
        current++;
        if (current >= durationTicks)
        {
            current = 0;
            return true;
        }
        return false;
    }
}
