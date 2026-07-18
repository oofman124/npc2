package npc2.npc2.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared nearest-first navigation lookup for blocks NPCs interact with from beside them. */
public final class ReachableApproach {
    private ReachableApproach() {
    }

    public static @Nullable Vec3 beside(FakeNpcEntity npc, BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(target.relative(direction));
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(npc.blockPosition())));
        for (BlockPos candidate : candidates) {
            if (npc.getNpcNavigation().canReach(candidate)) return Vec3.atBottomCenterOf(candidate);
        }
        return null;
    }
}
