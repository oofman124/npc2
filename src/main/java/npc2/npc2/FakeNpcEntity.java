package npc2.npc2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import npc2.npc2.ai.CoolEntity;
import npc2.npc2.ai.NpcMemories;

/**
 * A server-side NPC built on {@link PathfinderMob} instead of a fake {@code ServerPlayer}.
 * Uses real mob pathfinding, equipment, and combat, with a small inventory bag for loot/gear.
 */
public class FakeNpcEntity extends PathfinderMob implements ContainerUser {

	private static final int INVENTORY_SIZE = 27;
	private static final EntityDataAccessor<Integer> DATA_ATTACK_SWING_SEQUENCE = SynchedEntityData.defineId(FakeNpcEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_SWING_HAND = SynchedEntityData.defineId(FakeNpcEntity.class, EntityDataSerializers.INT);

	private NpcController controller;
	private final NpcPathNavigation navigation;
	private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
	private final NpcMemories memories = new NpcMemories();
	private int lastClientSwingSequence;

	protected MinecraftServer npcServer;
	protected ServerLevel respawnLevel;
	protected String npcName;
	protected double respawnX;
	protected double respawnY;
	protected double respawnZ;

	public FakeNpcEntity(EntityType<? extends FakeNpcEntity> type, Level level) {
		super(type, level);
		this.navigation = new NpcPathNavigation(this);
		this.setPersistenceRequired();
		this.setCanPickUpLoot(true);
		this.getNavigation().setCanFloat(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_ATTACK_SWING_SEQUENCE, 0);
		entityData.define(DATA_ATTACK_SWING_HAND, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0D)
			.add(Attributes.MOVEMENT_SPEED, 0.32D)
			.add(Attributes.STEP_HEIGHT, 1.0D)
			.add(Attributes.ATTACK_DAMAGE, 1.0D)
			.add(Attributes.FOLLOW_RANGE, 48.0D)
			.add(Attributes.ARMOR, 0.0D)
			.add(Attributes.ATTACK_KNOCKBACK, 0.0D);
	}

	public NpcPathNavigation getNpcNavigation() {
		return this.navigation;
	}

	/** Reach loot from an assigned neighboring approach block without crowding its exact position. */
	@Override
	protected Vec3i getPickupReach() {
		return new Vec3i(2, 1, 2);
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return (this.controller != null
				&& this.controller.getDesirableLootScore(this, stack) > 0.0D
				&& this.inventory.canAddItem(stack))
			|| super.wantsToPickUp(level, stack);
	}

	@Override
	protected void pickUpItem(ServerLevel level, ItemEntity entity) {
		ItemStack stack = entity.getItem();
		if (this.controller != null
				&& this.controller.getDesirableLootScore(this, stack) > 0.0D
				&& this.inventory.canAddItem(stack)) {
			int originalCount = stack.getCount();
			ItemStack remainder = this.inventory.addItem(stack.copy());
			int moved = originalCount - remainder.getCount();
			if (moved > 0) {
				this.onItemPickup(entity);
				this.take(entity, moved);
				stack.shrink(moved);
				if (stack.isEmpty()) {
					entity.discard();
				}
				return;
			}
		}
		super.pickUpItem(level, entity);
	}

	@Override
	public void aiStep() {
		this.updateSwingTime();
		super.aiStep();
	}

	@Override
	public void swing(InteractionHand hand, boolean sendToSwingingEntity) {
		this.beginSwingAnimation(hand);
		if (this.level() instanceof ServerLevel serverLevel) {
			ClientboundAnimatePacket packet = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
			ServerChunkCache chunkSource = serverLevel.getChunkSource();
			if (sendToSwingingEntity) {
				chunkSource.sendToTrackingPlayersAndSelf(this, packet);
			} else {
				chunkSource.sendToTrackingPlayers(this, packet);
			}
		}
	}

	private void beginSwingAnimation(InteractionHand hand) {
		this.swinging = true;
		this.swingingArm = hand;
		this.swingTime = 0;
		this.attackAnim = 0.0F;
		this.oAttackAnim = 0.0F;
		if (!this.level().isClientSide()) {
			this.entityData.set(DATA_ATTACK_SWING_SEQUENCE, this.entityData.get(DATA_ATTACK_SWING_SEQUENCE) + 1);
			this.entityData.set(DATA_ATTACK_SWING_HAND, hand == InteractionHand.MAIN_HAND ? 0 : 1);
		}
	}

	/** Non-equipment bag (armor/weapons wait here until equipped). */
	public SimpleContainer getInventory() {
		return this.inventory;
	}

	public NpcMemories getMemories() {
		return this.memories;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		ContainerHelper.saveAllItems(output.child("npc2_bag"), this.inventory.getItems());
		this.memories.save(output.child("npc2_memories"));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.inventory.clearContent();
		ContainerHelper.loadAllItems(input.childOrEmpty("npc2_bag"), this.inventory.getItems());
		this.memories.load(input.childOrEmpty("npc2_memories"));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player player
				&& !player.isCreative() && !player.isSpectator()) {
			this.memories.provoke(this, player);
		}
		return hurt;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		for (ItemStack stack : this.inventory.removeAllItems()) {
			if (!stack.isEmpty()) this.drop(stack, true, false);
		}
	}

	/**
	 * Enters the vanilla sleeping pose without applying the height of a bed to the
	 * entity. {@link LivingEntity#startSleeping(BlockPos)} always places a sleeper
	 * {@code 0.6875} blocks above the supplied block because it assumes that block
	 * is a bed. For floor sleep, the NPC's current feet position is already the
	 * floor surface, so retain that surface and only add vanilla's 1/8-block model
	 * clearance.
	 */
	public void startSleepingOnFloor(BlockPos position) {
		double floorSurfaceY = this.getY();
		this.startSleeping(position);
		this.setPos(position.getX() + 0.5D, floorSurfaceY + 0.125D, position.getZ() + 0.5D);
	}

	@Override
	public boolean hasContainerOpen(ContainerOpenersCounter counter, BlockPos pos) {
		return this.isAlive()
			&& !this.isRemoved()
			&& this.memories.openChestDimension != null
			&& this.memories.openChestDimension.equals(this.level().dimension())
			&& (pos.equals(this.memories.openChestPosition)
			|| pos.equals(this.memories.openChestPartnerPosition));
	}

	@Override
	public double getContainerInteractionRange() {
		return 4.5D;
	}

	public static FakeNpcEntity spawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
		FakeNpcEntity npc = new CoolEntity(ModEntities.FAKE_NPC, level);
		npc.configureSpawn(server, level, name, x, y, z);
		level.addFreshEntity(npc);
		return npc;
	}

	protected void configureSpawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
		this.npcServer = server;
		this.respawnLevel = level;
		this.npcName = name;
		this.respawnX = x;
		this.respawnY = y;
		this.respawnZ = z;

		this.snapTo(x, y, z, 0.0F, 0.0F);
		this.setCustomName(Component.literal(name));
		this.setCustomNameVisible(true);
		this.setPersistenceRequired();
	}

	public void attachController(NpcController controller) {
		this.controller = controller;
		controller.onAttach(this);
	}

	public void detachController() {
		if (this.controller != null) {
			this.controller.onDetach(this);
		}
		this.controller = null;
	}

	public NpcController getController() {
		return this.controller;
	}

	/**
	 * Call once per server tick from the mod lifecycle when the entity is managed externally.
	 * PathfinderMob also runs the controller from {@link #customServerAiStep} while alive in-world.
	 */
	public void aiTick() {
		if (this.controller != null) {
			this.controller.tick(this);
		}
		this.navigation.tick();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.navigation.tick();
		if (this.controller != null) {
			this.controller.tick(this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (this.level().isClientSide() && (DATA_ATTACK_SWING_SEQUENCE.equals(accessor) || DATA_ATTACK_SWING_HAND.equals(accessor))) {
			int currentSequence = this.entityData.get(DATA_ATTACK_SWING_SEQUENCE);
			if (currentSequence != this.lastClientSwingSequence) {
				this.lastClientSwingSequence = currentSequence;
				InteractionHand hand = this.entityData.get(DATA_ATTACK_SWING_HAND) == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				this.beginSwingAnimation(hand);
			}
		}
	}

	/** Removes this dead NPC and creates a replacement at its original spawn point. */
	public FakeNpcEntity respawn() {
		NpcController previousController = this.controller;
		this.detachController();
		if (!this.isRemoved()) {
			this.discard();
		}

		FakeNpcEntity replacement = spawn(this.npcServer, this.respawnLevel, this.npcName, this.respawnX, this.respawnY, this.respawnZ);
		if (previousController != null) {
			replacement.attachController(previousController);
		}
		return replacement;
	}
}
