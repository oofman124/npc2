package npc2.npc2;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Thin pathfinding facade over the NPC's native {@link PathNavigation}.
 * Keeps the previous controller API while PathfinderMob handles movement.
 */
public final class NpcPathNavigation {
	/** Historical absolute step speed (~player) maps to this vanilla modifier. */
	private static final double REFERENCE_STEP_SPEED = 0.23D;

	private final FakeNpcEntity mob;
	private final PathNavigation navigation;

	// Path throttling
	private int pathRecalcDelay = 0;
	private Vec3 lastTargetPos = Vec3.ZERO;
	private int consecutiveFailures;
	private Vec3 lastProgressPos;
	private int noProgressTicks;
	private int partialPathTicks;
	private long lastProgressCheckTick = Long.MIN_VALUE;

	public NpcPathNavigation(FakeNpcEntity mob) {
		this.mob = mob;
		this.lastProgressPos = mob.position();
		this.navigation = mob.getNavigation();
		this.navigation.setCanFloat(true);
		this.navigation.setCanOpenDoors(true);
	}

	public boolean moveTo(double x, double y, double z, double speed) {
		Vec3 target = new Vec3(x, y, z);
		boolean targetChanged = target.distanceToSqr(this.lastTargetPos) >= 4.0D;
		if (targetChanged) {
			this.noProgressTicks = 0;
			this.partialPathTicks = 0;
			this.lastProgressPos = this.mob.position();
		}
		this.trackProgress();

		if (this.pathRecalcDelay > 0) {
			this.pathRecalcDelay--;
		}

		if (this.navigation.getPath() != null
			&& !this.navigation.isDone()
			&& target.distanceToSqr(this.lastTargetPos) < 4.0D
			&& this.pathRecalcDelay > 0) {
			this.navigation.setSpeedModifier(toSpeedModifier(speed));
			return true;
		}

		this.lastTargetPos = target;
		this.pathRecalcDelay = 15;
		boolean started = this.navigation.moveTo(x, y, z, toSpeedModifier(speed));
		Path path = this.navigation.getPath();
		if (!started) {
			this.consecutiveFailures = Math.min(20, this.consecutiveFailures + 1);
		} else if (pathActuallyReaches(path, target)) {
			this.consecutiveFailures = 0;
		} else {
			this.consecutiveFailures = Math.min(20, this.consecutiveFailures + 1);
		}
		return started;
	}

	public boolean moveTo(Vec3 target, double speed) {
		return moveTo(target.x, target.y, target.z, speed);
	}

	public void stop() {
		this.pathRecalcDelay = 0;
		this.navigation.stop();
		this.mob.setJumping(false);
	}

	public boolean isDone() {
		return this.navigation.isDone();
	}

	public boolean isInProgress() {
		return this.navigation.isInProgress();
	}

	public boolean isStuck() {
		return this.navigation.isStuck();
	}

	public boolean needsRecovery() {
		return this.navigation.isStuck() || this.consecutiveFailures >= 3
			|| (this.partialPathTicks >= 25 && this.noProgressTicks >= 25)
			|| this.noProgressTicks >= 45;
	}

	public boolean hasFailedPath() {
		return this.consecutiveFailures >= 3;
	}

	public boolean shouldAbandonTarget() {
		return this.consecutiveFailures >= 6
			|| (this.partialPathTicks >= 50 && this.noProgressTicks >= 50)
			|| this.noProgressTicks >= 80;
	}

	public int getNoProgressTicks() {
		return this.noProgressTicks;
	}

	public int getPartialPathTicks() {
		return this.partialPathTicks;
	}

	public boolean pathActuallyReachesTarget() {
		return pathActuallyReaches(this.navigation.getPath(), this.lastTargetPos);
	}

	public void markRecovered() {
		this.consecutiveFailures = 0;
		this.pathRecalcDelay = 0;
	}

	public void markTargetAbandoned() {
		this.consecutiveFailures = 0;
		this.noProgressTicks = 0;
		this.partialPathTicks = 0;
		this.pathRecalcDelay = 0;
		this.lastProgressPos = this.mob.position();
	}

	public @Nullable Path getPath() {
		return this.navigation.getPath();
	}

	/** Calculate reachability without replacing or stopping the active path. */
	public boolean canReach(Entity target) {
		Path candidate = this.navigation.createPath(target, 0);
		return pathActuallyReaches(candidate, target.position());
	}

	/** Calculate block reachability without replacing or stopping the active path. */
	public boolean canReach(BlockPos target) {
		if (Vec3.atCenterOf(target).distanceToSqr(this.mob.position()) <= 2.25D) {
			return true;
		}
		Path candidate = this.navigation.createPath(target, 0);
		return pathActuallyReaches(candidate, Vec3.atBottomCenterOf(target));
	}

	/**
	 * No-op: {@link net.minecraft.world.entity.Mob} ticks navigation each server AI step.
	 * Kept so callers that still invoke tick remain valid.
	 */
	public void tick() {
	}

	private void trackProgress() {
		long gameTick = this.mob.level().getGameTime();
		if (gameTick == this.lastProgressCheckTick) return;
		this.lastProgressCheckTick = gameTick;
		Vec3 position = this.mob.position();
		double dx = position.x - this.lastProgressPos.x;
		double dz = position.z - this.lastProgressPos.z;
		if (dx * dx + dz * dz >= 0.04D) {
			this.lastProgressPos = position;
			this.noProgressTicks = 0;
		} else {
			this.noProgressTicks = Math.min(200, this.noProgressTicks + 1);
		}
		Path path = this.navigation.getPath();
		if (path != null && !pathActuallyReaches(path, this.lastTargetPos)) {
			this.partialPathTicks = Math.min(200, this.partialPathTicks + 1);
		} else {
			this.partialPathTicks = 0;
		}
	}

	private static boolean pathActuallyReaches(@Nullable Path path, Vec3 target) {
		if (path == null || !path.canReach()) return false;
		Node end = path.getEndNode();
		if (end == null) return false;
		double dx = end.x + 0.5D - target.x;
		double dz = end.z + 0.5D - target.z;
		return dx * dx + dz * dz <= 2.25D && Math.abs(end.y - target.y) <= 1.25D;
	}

	private static double toSpeedModifier(double absoluteStepSpeed) {
		return Math.max(0.5D, absoluteStepSpeed / REFERENCE_STEP_SPEED);
	}
}
