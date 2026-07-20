package npc2.npc2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import npc2.npc2.ai.movement.LootReservations;
import npc2.npc2.ai.movement.ChestLooting;
import npc2.npc2.ai.rest.BedReservations;
import npc2.npc2.ai.movement.BlockResourceGathering;
import npc2.npc2.ai.interaction.BlockInteractionStations;
import npc2.npc2.ai.survival.SurvivalNeeds;
import npc2.npc2.ai.survival.SurvivalPlanner;

import java.util.List;
import java.util.Comparator;
import java.util.PriorityQueue;

public interface NpcController {

    /** Called once when this controller is attached to an NPC. Set up sensors/state here. */
    default void onAttach(FakeNpcEntity npc) {}

    /** Called once when detached (despawn, controller swap, etc). Clean up here. */
    default void onDetach(FakeNpcEntity npc) {
        LootReservations.release(npc);
        ChestLooting.closeVisual(npc);
        ChestLooting.release(npc);
        BedReservations.release(npc);
        BlockResourceGathering.release(npc);
        BlockInteractionStations.releaseAll(npc);
        npc.getMemories().stationPlacementSites.clear();
        npc.getMemories().stationRelocationTargets.clear();
    }

    /** Called every server tick this NPC is active. This is your behavior tree's root tick. */
    void tick(FakeNpcEntity npc);

    /**
     * Path toward a target using vanilla ground pathfinding.
     * The path is followed at the end of the NPC tick via {@link NpcPathNavigation#tick()}.
     * The NPC faces the next waypoint instead of staring through obstacles at the
     * final destination. A failed path is stopped rather than replaced with direct,
     * collision-ignoring movement.
     */
    default boolean moveTo(FakeNpcEntity npc, Vec3 target, double speed) {
        if (!npc.getNpcNavigation().moveTo(target, speed)) {
            // Cancel the failed route without erasing collision pushes or knockback.
            npc.getNpcNavigation().stop();
            return false;
        }

        Path path = npc.getNpcNavigation().getPath();
        Vec3 nextPosition = path != null && !path.isDone()
                ? path.getNextEntityPos(npc)
                : target;
        Vec3 facingTarget = new Vec3(nextPosition.x, npc.getEyeY(), nextPosition.z);
        lookAt(npc, facingTarget);
        return true;
    }

    /** Path toward an entity using vanilla ground pathfinding. */
    default boolean moveTo(FakeNpcEntity npc, Entity target, double speed) {
        return moveTo(npc, target.position(), speed);
    }

    /**
     * Direct step toward a point without pathfinding (ignores obstacles).
     * Prefer {@link #moveTo} when the NPC needs to navigate terrain.
     */
    default void moveStraightTo(FakeNpcEntity npc, Vec3 target, double speed) {
        Vec3 offset = target.subtract(npc.position());
        double horizontalDistance = Math.hypot(offset.x, offset.z);
        double dy = offset.y;

        if (dy > 0.5D && npc.onGround() && horizontalDistance <= 1.75D) {
            npc.jumpFromGround();
            npc.setJumping(true);
        } else if (dy <= 0.5D) {
            npc.setJumping(false);
        }

        if (horizontalDistance < 0.1) {
            if (dy <= 0.5D) {
                stopMoving(npc);
            }
            return;
        }

        double xVelocity = offset.x / horizontalDistance * speed;
        double zVelocity = offset.z / horizontalDistance * speed;
        Vec3 step = new Vec3(xVelocity, 0.0, zVelocity);
        npc.move(MoverType.SELF, step);
        npc.setDeltaMovement(xVelocity, npc.getDeltaMovement().y, zVelocity);
        lookAt(npc, new Vec3(target.x, npc.getEyeY(), target.z));
    }

    /** Cancel active navigation while leaving ordinary entity physics intact. */
    default void stopMoving(FakeNpcEntity npc) {
        npc.getNpcNavigation().stop();
    }

    /** Find the nearest real player within range. */
    default Player findNearestPlayer(FakeNpcEntity npc, double radius) {
        double maxDistanceSqr = radius * radius;
        Player nearest = null;
        double nearestDistance = maxDistanceSqr;

        for (Player candidate : npc.level().players()) {
            if (!candidate.isAlive()) {
                continue;
            }

            double distance = candidate.distanceToSqr(npc);
            if (distance <= nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    /**
     * Find the nearest attackable living entity within range: players (except this NPC)
     * and mobs. Creative/spectator players and other fake NPCs are ignored.
     * Entities hidden strictly inside caves beneath the NPC are ignored via smart vertical raycasts.
     */
    default LivingEntity findNearestTarget(FakeNpcEntity npc, double radius) {
        AABB searchBox = npc.getBoundingBox().inflate(radius);
        double npcY = npc.getY();

        List<LivingEntity> candidates = npc.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                candidate -> {
                    if (!isValidCombatTarget(npc, candidate)) {
                        return false;
                    }

                    double yDelta = npcY - candidate.getY();
                    // If the target is lower down than a comfortable step height (e.g., 2.5 blocks)
                    if (yDelta > 2.5D) {
                        // Fire a raycast from just beneath the NPC's feet down to the target's eye/head level.
                        // We check for COLLIDER blocks (solid terrain).
                        BlockHitResult hitResult = npc.level().clip(new ClipContext(
                                npc.position().add(0, 0.1D, 0),
                                candidate.getEyePosition(),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                npc
                        ));

                        // If the ray hit solid blocks before hitting the target, they are cut off by a roof/floor.
                        if (hitResult.getType() == HitResult.Type.BLOCK) {
                            return false;
                        }
                    }

                    return true;
                }
        );

        if (candidates.isEmpty()) {
            return null;
        }

        // Sort candidates by squared distance so we evaluate the closest ones first
        candidates.sort((a, b) -> Double.compare(a.distanceToSqr(npc), b.distanceToSqr(npc)));

        double maxDistanceSqr = radius * radius;

        for (LivingEntity candidate : candidates) {
            double distance = candidate.distanceToSqr(npc);
            if (distance > maxDistanceSqr) {
                continue;
            }

            if (npc.getNpcNavigation().canReach(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /** Hunt a reachable adult animal when food is genuinely needed. */
    default LivingEntity findNearestHuntTarget(FakeNpcEntity npc, double radius) {
        AABB searchBox = npc.getBoundingBox().inflate(radius);
        double maxDistanceSqr = radius * radius;
        Animal nearest = null;
        double nearestDistance = maxDistanceSqr;

        for (Animal animal : npc.level().getEntitiesOfClass(
                Animal.class,
                searchBox,
                candidate -> isDesiredHuntAnimal(npc, candidate)
                        && candidate.isAlive()
                        && !candidate.isBaby()
                        && candidate.attackable())) {
            double distance = npc.distanceToSqr(animal);
            if (distance < nearestDistance && npc.getNpcNavigation().canReach(animal)) {
                nearest = animal;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** Hunting is a fallback survival task, not a constant aggression mode. */
    default boolean shouldHuntForFood(FakeNpcEntity npc) {
        return countFood(npc) < SurvivalNeeds.FOOD_TARGET
                && SurvivalNeeds.foodOutranksGathering(npc, this);
    }

    default boolean shouldHuntForResources(FakeNpcEntity npc) {
        return shouldHuntForFood(npc) || needsBedMaterials(npc);
    }

    default boolean needsBedMaterials(FakeNpcEntity npc) {
        return SurvivalNeeds.isNight(npc)
                && !SurvivalNeeds.hasBedAvailable(npc)
                && countInventoryTag(npc.getInventory(), ItemTags.WOOL) < 3;
    }

    default boolean isDesiredHuntAnimal(FakeNpcEntity npc, Animal animal) {
        return (shouldHuntForFood(npc) && isFoodAnimal(animal))
                || (needsBedMaterials(npc) && animal instanceof Sheep);
    }

    /** Passive land mobs whose ordinary drops provide food. */
    default boolean isFoodAnimal(Animal animal) {
        return animal instanceof Cow
                || animal instanceof Pig
                || animal instanceof Sheep
                || animal instanceof Chicken
                || animal instanceof Rabbit;
    }

    default boolean isValidHuntTarget(FakeNpcEntity npc, LivingEntity candidate) {
        return shouldHuntForResources(npc)
                && candidate instanceof Animal animal
                && isDesiredHuntAnimal(npc, animal)
                && animal.isAlive()
                && !animal.isBaby()
                && animal.attackable();
    }

    /** Whether this entity is something the NPC should chase and attack. */
    default boolean isValidCombatTarget(FakeNpcEntity npc, LivingEntity candidate) {
        if (candidate == null || candidate == npc || !candidate.isAlive() || !candidate.attackable()) {
            return false;
        }
        if (candidate instanceof FakeNpcEntity) {
            return false;
        }
        if (candidate instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        if (candidate instanceof Animal) {
            return false;
        }
        if (candidate instanceof Villager) {
            return false;
        }
        if (candidate instanceof IronGolem) {
            //return true;
        }
        if (candidate instanceof Bee) {
            return true;
        }
        return candidate instanceof Monster;
    }

    /** Return true if the entity is close enough for a melee interaction. */
    default boolean isWithinMeleeRange(FakeNpcEntity npc, LivingEntity target, double range) {
        return target != null && target.isAlive() && npc.distanceToSqr(target) <= range * range;
    }

    /** Turn head/body toward a point without moving. */
    default void lookAt(FakeNpcEntity npc, Vec3 target) {
        npc.getLookControl().setLookAt(target.x, target.y, target.z, 20.0F, 20.0F);
    }

    /** Swing the main hand — used for both "attack" and "use item" animations. */
    default void swingHand(FakeNpcEntity npc) {
        npc.swing(InteractionHand.MAIN_HAND);
    }

    /** Swing the offhand. */
    default void swingOffhand(FakeNpcEntity npc) {
        npc.swing(InteractionHand.OFF_HAND);
    }

    /**
     * Start using the offhand item (shields, food, bows in offhand, etc).
     * For shields this begins blocking after the block-delay ticks.
     */
    default void useOffhand(FakeNpcEntity npc) {
        ItemStack offhand = npc.getOffhandItem();
        if (offhand.isEmpty()) {
            return;
        }
        if (npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.OFF_HAND) {
            return;
        }
        npc.startUsingItem(InteractionHand.OFF_HAND);
    }

    /** Stop using whatever item is currently being used (lowers shield, cancels draws). */
    default void stopUsingItem(FakeNpcEntity npc) {
        if (npc.isUsingItem()) {
            npc.stopUsingItem();
        }
    }

    /** True if the offhand stack can block attacks (shield / BlocksAttacks component). */
    default boolean isShield(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.SHIELD) || stack.has(DataComponents.BLOCKS_ATTACKS));
    }

    /** True if the NPC currently holds a shield in the offhand. */
    default boolean hasShieldEquipped(FakeNpcEntity npc) {
        return isShield(npc.getOffhandItem());
    }

    default boolean hasShieldOwned(FakeNpcEntity npc) {
        if (hasShieldEquipped(npc)) {
            return true;
        }
        for (ItemStack stack : npc.getInventory()) {
            if (isShield(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Raise the offhand shield if one is equipped. */
    default void raiseShield(FakeNpcEntity npc) {
        if (hasShieldEquipped(npc)) {
            useOffhand(npc);
        }
    }

    /** Lower the shield / stop offhand use. */
    default void lowerShield(FakeNpcEntity npc) {
        if (npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.OFF_HAND) {
            npc.stopUsingItem();
        }
    }

    /**
     * Place the main-hand block against {@code hit}, playing the place/swing animation.
     * @return true if the interaction consumed the action (block placed or item used)
     */
    default boolean placeBlock(FakeNpcEntity npc, BlockHitResult hit) {
        ItemStack stack = npc.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;

        BlockPos clicked = hit.getBlockPos();
        BlockPos placeAt = npc.level().getBlockState(clicked).canBeReplaced()
                ? clicked : clicked.relative(hit.getDirection());
        if (!npc.level().getBlockState(placeAt).canBeReplaced()) return false;

        Block block = blockItem.getBlock();
        if (block instanceof BedBlock bed) return placeBedBlock(npc, stack, bed, placeAt);

        BlockState state = block.defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, npc.getDirection().getOpposite());
        }
        if (!state.canSurvive(npc.level(), placeAt)
                || !npc.level().isUnobstructed(state, placeAt, CollisionContext.of(npc))
                || !npc.level().setBlock(placeAt, state, Block.UPDATE_ALL)) return false;
        block.setPlacedBy(npc.level(), placeAt, state, npc, stack);
        stack.shrink(1);
        swingHand(npc);
        return true;
    }

    private boolean placeBedBlock(FakeNpcEntity npc, ItemStack stack, BedBlock bed, BlockPos foot) {
        Direction preferred = npc.getDirection();
        Direction[] directions = {
                preferred, preferred.getClockWise(), preferred.getCounterClockWise(), preferred.getOpposite()
        };
        for (Direction direction : directions) {
            BlockPos head = foot.relative(direction);
            if (!npc.level().getBlockState(head).canBeReplaced()) continue;
            BlockState footState = bed.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, direction)
                    .setValue(BedBlock.PART, BedPart.FOOT);
            BlockState headState = footState.setValue(BedBlock.PART, BedPart.HEAD);
            if (!footState.canSurvive(npc.level(), foot)
                    || !headState.canSurvive(npc.level(), head)
                    || !npc.level().isUnobstructed(footState, foot, CollisionContext.of(npc))
                    || !npc.level().isUnobstructed(headState, head, CollisionContext.of(npc))) continue;
            if (!npc.level().setBlock(foot, footState, Block.UPDATE_ALL)) continue;
            bed.setPlacedBy(npc.level(), foot, footState, npc, stack);
            if (!(npc.level().getBlockState(head).getBlock() instanceof BedBlock)) {
                npc.level().setBlock(foot, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            stack.shrink(1);
            swingHand(npc);
            return true;
        }
        return false;
    }

    /**
     * Place the main-hand block on the face of {@code against} pointed by {@code face}.
     * Example: place on top of a block with {@code face = Direction.UP}.
     */
    default boolean placeBlock(FakeNpcEntity npc, BlockPos against, Direction face) {
        Vec3 clickLocation = Vec3.atCenterOf(against).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D
        );
        BlockHitResult hit = new BlockHitResult(clickLocation, face, against, false);
        return placeBlock(npc, hit);
    }

    /** Convenience: place on top of the block at {@code below}. */
    default boolean placeBlockOnTop(FakeNpcEntity npc, BlockPos below) {
        BlockState state = npc.level().getBlockState(below);
        if (state.isAir()) {
            return false;
        }
        return placeBlock(npc, below, Direction.UP);
    }

    /**
     * Perform a mob melee attack against {@code target}.
     * Attack rate is controlled by the brain's AttackDebounce node.
     */
    default void attackEntity(FakeNpcEntity npc, Entity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        lookAt(npc, target instanceof LivingEntity livingTarget
                ? livingTarget.getEyePosition()
                : target.getBoundingBox().getCenter());
        if (npc.level() instanceof ServerLevel serverLevel) {
            npc.doHurtTarget(serverLevel, target);
        }
        swingHand(npc);
    }

    /**
     * Select the bag item with the highest main-hand attack damage and equip it.
     */
    default void equipBestWeapon(FakeNpcEntity npc) {
        SimpleContainer inventory = npc.getInventory();

        int bestSlot = -1;
        double bestDamage = getItemAttackDamage(npc.getMainHandItem());

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || isShield(stack)) {
                continue;
            }

            double damage = getItemAttackDamage(stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }

        if (bestSlot < 0) {
            return;
        }

        ItemStack better = inventory.getItem(bestSlot);
        inventory.setItem(bestSlot, ItemStack.EMPTY);
        ItemStack previous = npc.getMainHandItem();
        npc.setItemSlot(EquipmentSlot.MAINHAND, better);
        if (!previous.isEmpty()) {
            inventory.setItem(bestSlot, previous);
        }
    }

    /** Equip the fastest owned tool for the block being worked, preserving the previous hand item. */
    default void equipBestToolForBlock(FakeNpcEntity npc, BlockState state) {
        TagKey<Item> tools = null;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) tools = ItemTags.AXES;
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) tools = ItemTags.PICKAXES;
        else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) tools = ItemTags.SHOVELS;
        else if (state.is(BlockTags.MINEABLE_WITH_HOE)) tools = ItemTags.HOES;
        if (tools == null) return;

        SimpleContainer bag = npc.getInventory();
        int bestSlot = -1;
        double bestSpeed = npc.getMainHandItem().is(tools)
                ? npc.getMainHandItem().getDestroySpeed(state) : 1.0D;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.is(tools) || stack.nextDamageWillBreak()) continue;
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return;
        ItemStack tool = bag.getItem(bestSlot);
        bag.setItem(bestSlot, npc.getMainHandItem());
        npc.setItemInHand(InteractionHand.MAIN_HAND, tool);
        bag.setChanged();
    }

    /**
     * Move a shield from the bag into the offhand if the offhand is empty
     * or does not already hold a shield.
     */
    default void equipShield(FakeNpcEntity npc) {
        if (hasTotemEquipped(npc) || hasShieldEquipped(npc)) {
            return;
        }

        SimpleContainer inventory = npc.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isShield(stack)) {
                continue;
            }

            ItemStack shield = inventory.getItem(slot);
            inventory.setItem(slot, ItemStack.EMPTY);
            ItemStack previousOffhand = npc.getOffhandItem();
            npc.setItemInHand(InteractionHand.OFF_HAND, shield);
            if (!previousOffhand.isEmpty()) {
                inventory.setItem(slot, previousOffhand);
            }
            return;
        }
    }

    /** True if this stack is a Totem of Undying. */
    default boolean isTotem(ItemStack stack) {
        return stack != null && stack.is(Items.TOTEM_OF_UNDYING);
    }

    default boolean hasTotemEquipped(FakeNpcEntity npc) {
        return isTotem(npc.getOffhandItem());
    }

    default boolean hasTotemOwned(FakeNpcEntity npc) {
        if (hasTotemEquipped(npc)) {
            return true;
        }
        SimpleContainer inventory = npc.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isTotem(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    default boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    default boolean hasFood(FakeNpcEntity npc) {
        return countFood(npc) > 0;
    }

    default int countFood(FakeNpcEntity npc) {
        int count = 0;
        SimpleContainer inventory = npc.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isFood(inventory.getItem(slot))) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    /** Consume the most nourishing bag food and convert nutrition into mob health. */
    default boolean eatBestFood(FakeNpcEntity npc) {
        SimpleContainer inventory = npc.getInventory();
        int bestSlot = -1;
        float bestHealing = 0.0F;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) {
                continue;
            }
            float healing = Math.max(1.0F, food.nutrition() * 0.75F + food.saturation() * 0.5F);
            if (healing > bestHealing) {
                bestHealing = healing;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            return false;
        }

        ItemStack food = inventory.getItem(bestSlot);
        food.shrink(1);
        if (food.isEmpty()) {
            inventory.setItem(bestSlot, ItemStack.EMPTY);
        }
        inventory.setChanged();
        npc.heal(bestHealing);
        swingHand(npc);
        return true;
    }

    /** Equip a totem from the bag, replacing any shield because totems have higher priority. */
    default void equipTotem(FakeNpcEntity npc) {
        if (hasTotemEquipped(npc)) {
            return;
        }

        SimpleContainer inventory = npc.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!isTotem(inventory.getItem(slot))) {
                continue;
            }

            ItemStack totem = inventory.getItem(slot);
            inventory.setItem(slot, ItemStack.EMPTY);
            ItemStack previousOffhand = npc.getOffhandItem();
            npc.setItemInHand(InteractionHand.OFF_HAND, totem);
            if (!previousOffhand.isEmpty()) {
                inventory.setItem(slot, previousOffhand);
            }
            return;
        }
    }

    /** True if this stack is wearable armor (head/chest/legs/feet). */
    default boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot().isArmor();
    }

    /** Armor equipment slot for this stack, or null if it is not armor. */
    default @Nullable EquipmentSlot getArmorSlot(ItemStack stack) {
        if (!isArmor(stack)) {
            return null;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null ? equippable.slot() : null;
    }

    /** Score armor by defense + toughness (higher is better). */
    default double getArmorScore(ItemStack stack) {
        EquipmentSlot slot = getArmorSlot(stack);
        if (slot == null) {
            return 0.0D;
        }

        double[] armor = {0.0D};
        double[] toughness = {0.0D};
        double[] knockback = {0.0D};

        stack.forEachModifier(slot, (attribute, modifier) -> {
            double amount = modifier.amount();
            if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
                return;
            }
            if (attribute.equals(Attributes.ARMOR)) {
                armor[0] += amount;
            } else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
                toughness[0] += amount;
            } else if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) {
                knockback[0] += amount;
            }
        });

        return armor[0] + toughness[0] * 0.5D + knockback[0] * 2.0D;
    }

    /**
     * For each armor slot, equip the highest-scoring piece from the bag
     * (swapping the previous piece back into the bag).
     */
    default void equipBestArmor(FakeNpcEntity npc) {
        SimpleContainer inventory = npc.getInventory();

        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            int bestSlot = -1;
            double bestScore = getArmorScore(npc.getItemBySlot(slot));

            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || getArmorSlot(stack) != slot) {
                    continue;
                }
                double score = getArmorScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }

            if (bestSlot < 0) {
                continue;
            }

            ItemStack better = inventory.getItem(bestSlot);
            inventory.setItem(bestSlot, ItemStack.EMPTY);
            ItemStack previous = npc.getItemBySlot(slot);
            npc.setItemSlot(slot, better);
            if (!previous.isEmpty()) {
                inventory.setItem(bestSlot, previous);
            }
        }
    }

    /** Approximate total attack damage if {@code stack} were held in the main hand. */
    default double getItemAttackDamage(ItemStack stack) {
        double base = 1.0D;
        if (stack == null || stack.isEmpty()) {
            return base;
        }

        double[] addValue = {0.0D};
        double[] mulBase = {0.0D};
        double[] mulTotal = {1.0D};

        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }

            switch (modifier.operation()) {
                case AttributeModifier.Operation.ADD_VALUE -> addValue[0] += modifier.amount();
                case AttributeModifier.Operation.ADD_MULTIPLIED_BASE -> mulBase[0] += modifier.amount();
                case AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> mulTotal[0] *= (1.0D + modifier.amount());
            }
        });

        return (base + addValue[0]) * (1.0D + mulBase[0]) * mulTotal[0];
    }

    /** True if this stack deals more than bare-fist damage. */
    default boolean isWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !isShield(stack) && getItemAttackDamage(stack) > 1.0D;
    }

    /** Best weapon damage currently available in the bag or main hand. */
    default double getBestOwnedWeaponDamage(FakeNpcEntity npc) {
        double best = getItemAttackDamage(npc.getMainHandItem());
        SimpleContainer inventory = npc.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isWeapon(stack)) {
                best = Math.max(best, getItemAttackDamage(stack));
            }
        }
        return best;
    }

    /** True if the main-hand tool is damaged enough that the next hits may break it. */
    default boolean isMainToolAboutToBreak(FakeNpcEntity npc) {
        ItemStack stack = npc.getMainHandItem();
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        if (stack.nextDamageWillBreak()) {
            return true;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        int remaining = max - stack.getDamageValue();
        return remaining <= Math.max(5, max / 10);
    }

    /** Score a ground/container stack by how useful it currently is to this NPC. */
    default double getDesirableLootScore(FakeNpcEntity npc, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0D;
        }
        if (isTotem(stack)) {
            return 30.0D;
        }
        if (stack.is(ItemTags.BEDS) && !SurvivalNeeds.ownsBed(npc)) {
            return 9.0D;
        }
        if (stack.is(ItemTags.WOOL) && !SurvivalNeeds.ownsBed(npc)
                && countInventoryTag(npc.getInventory(), ItemTags.WOOL) < 3) {
            return 5.0D;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && (npc.getHealth() < npc.getMaxHealth() * 0.8F
                || countFood(npc) < SurvivalNeeds.FOOD_TARGET)) {
            return 12.0D + food.nutrition() + food.saturation();
        }
        SimpleContainer bag = npc.getInventory();
        SurvivalPlanner.Plan plan = SurvivalNeeds.planFor(npc, this);
        double plannedResourceScore = getPlannedResourceLootScore(stack, plan);
        if (plannedResourceScore > 0.0D) {
            return plannedResourceScore;
        }
        if (stack.is(ItemTags.PLANKS) && countInventoryTag(bag, ItemTags.PLANKS) < 16) {
            return 3.5D;
        }
        if (stack.is(Items.STICK) && bag.countItem(Items.STICK) < 8) {
            return 3.0D;
        }
        if (stack.is(Items.WHEAT) && countFood(npc) < SurvivalNeeds.FOOD_TARGET
                && bag.countItem(Items.WHEAT) < 12) {
            return 2.5D;
        }

        if (isWeapon(stack)) {
            double damage = getItemAttackDamage(stack);
            double ownedDamage = getBestOwnedWeaponDamage(npc);
            if (damage > ownedDamage || ownedDamage <= 1.0D || isMainToolAboutToBreak(npc)) {
                return 10.0D + damage;
            }
            return 0.0D;
        }

        if (isShield(stack)) {
            ItemStack ownedShield = npc.getOffhandItem();
            SimpleContainer inventory = npc.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (isShield(inventory.getItem(slot))) {
                    ownedShield = inventory.getItem(slot);
                    break;
                }
            }
            if (!isShield(ownedShield)) {
                return 8.0D;
            }
            if (ownedShield.isDamageableItem() && stack.isDamageableItem()) {
                int ownedRemaining = ownedShield.getMaxDamage() - ownedShield.getDamageValue();
                int candidateRemaining = stack.getMaxDamage() - stack.getDamageValue();
                return candidateRemaining > ownedRemaining + 20 ? 6.0D : 0.0D;
            }
            return 0.0D;
        }

        EquipmentSlot armorSlot = getArmorSlot(stack);
        if (armorSlot == null) {
            return 0.0D;
        }
        double bestOwnedScore = getArmorScore(npc.getItemBySlot(armorSlot));
        SimpleContainer inventory = npc.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (getArmorSlot(inventory.getItem(slot)) == armorSlot) {
                bestOwnedScore = Math.max(bestOwnedScore, getArmorScore(inventory.getItem(slot)));
            }
        }
        double candidateScore = getArmorScore(stack);
        return candidateScore > bestOwnedScore ? 5.0D + candidateScore : 0.0D;
    }

    /** Scores direct resource drops from every unmet entry in the weighted plan, not only its top need. */
    default double getPlannedResourceLootScore(ItemStack stack, SurvivalPlanner.Plan plan) {
        SurvivalPlanner.Resource resource = getPlannedResourceForLoot(stack);
        if (resource == null) return 0.0D;
        double baseScore = switch (resource) {
            case LOGS -> 4.0D;
            case COBBLESTONE, FUEL -> 2.5D;
            case SOIL -> 2.0D;
            case IRON_ORE -> 6.0D;
            case TORCHES -> 3.0D;
            default -> 0.0D;
        };
        if (baseScore <= 0.0D) return 0.0D;

        SurvivalPlanner.Need need = plan.needs().get(resource);
        if (need == null || need.current() >= need.target()) return 0.0D;
        // Keep the base score when a block search temporarily reduced confidence;
        // seeing the dropped item is direct evidence that the resource is available.
        return baseScore + Math.max(0.0D, need.score()) * 0.1D;
    }

    /** Maps tangible drops back to the same resource keys used by planning and gathering. */
    default SurvivalPlanner.@Nullable Resource getPlannedResourceForLoot(ItemStack stack) {
        if (stack.is(ItemTags.LOGS)) {
            return SurvivalPlanner.Resource.LOGS;
        } else if (stack.is(Items.COBBLESTONE)) {
            return SurvivalPlanner.Resource.COBBLESTONE;
        } else if (stack.is(Items.DIRT) || stack.is(Items.NETHERRACK)) {
            return SurvivalPlanner.Resource.SOIL;
        } else if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return SurvivalPlanner.Resource.FUEL;
        } else if (stack.is(Items.RAW_IRON) || stack.is(Items.IRON_INGOT)) {
            return SurvivalPlanner.Resource.IRON_ORE;
        } else if (stack.is(Items.TORCH)) {
            return SurvivalPlanner.Resource.TORCHES;
        }
        return null;
    }

    default int countInventoryTag(SimpleContainer inventory, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (stack.is(tag)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    default int countBuildingBlocks(SimpleContainer inventory) {
        return inventory.countItem(Items.DIRT)
                + inventory.countItem(Items.COBBLESTONE)
                + inventory.countItem(Items.NETHERRACK);
    }

    /**
     * Creeper blast radius used for shield decisions.
     * Normal creepers are 3; charged creepers are 6.
     */
    default float getCreeperExplosionRadius(Creeper creeper) {
        return creeper.isPowered() ? 6.0F : 3.0F;
    }

    /** True when a creeper is mid-fuse (ignited or swelling) and close enough to threaten the NPC. */
    default boolean shouldBlockCreeper(FakeNpcEntity npc, Creeper creeper) {
        if (creeper == null || !creeper.isAlive()) {
            return false;
        }
        boolean aboutToExplode = creeper.isIgnited() || creeper.getSwellDir() > 0;
        if (!aboutToExplode) {
            return false;
        }
        float radius = getCreeperExplosionRadius(creeper);
        return npc.distanceToSqr(creeper) <= (radius + 0.5F) * (radius + 0.5F);
    }

    /** Find a nearby ignited creeper that the NPC should block against. */
    default Creeper findThreateningCreeper(FakeNpcEntity npc, double searchRadius) {
        AABB box = npc.getBoundingBox().inflate(searchRadius);
        List<Creeper> creepers = npc.level().getEntitiesOfClass(Creeper.class, box, Creeper::isAlive);
        Creeper nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Creeper creeper : creepers) {
            if (!shouldBlockCreeper(npc, creeper)) {
                continue;
            }
            double dist = npc.distanceToSqr(creeper);
            if (dist < nearestDist) {
                nearest = creeper;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    /** True when a bow or crossbow wielder is aimed at this NPC and close to firing. */
    default boolean shouldBlockRangedAttack(FakeNpcEntity npc, LivingEntity attacker) {
        if (attacker == null || !attacker.isAlive() || attacker == npc || !attacker.hasLineOfSight(npc)) {
            return false;
        }

        Vec3 toNpc = npc.getEyePosition().subtract(attacker.getEyePosition());
        if (toNpc.lengthSqr() > 48.0D * 48.0D) {
            return false;
        }
        boolean explicitlyTargetingNpc = attacker instanceof Mob mob && mob.getTarget() == npc;
        if (!explicitlyTargetingNpc && attacker.getViewVector(1.0F).dot(toNpc.normalize()) < 0.65D) {
            return false;
        }

        if (attacker.isUsingItem()) {
            ItemStack weapon = attacker.getUseItem();
            int useTicks = attacker.getTicksUsingItem();
            if (weapon.getItem() instanceof BowItem) {
                // Begin before the final draw ticks so the shield's activation delay
                // completes before a skeleton releases the arrow.
                return BowItem.getPowerForTime(useTicks) >= 0.45F;
            }
            if (weapon.getItem() instanceof CrossbowItem) {
                int chargeTicks = CrossbowItem.getChargeDuration(weapon, attacker);
                return useTicks >= Math.max(1, chargeTicks - 6);
            }
        }

        ItemStack mainHand = attacker.getMainHandItem();
        ItemStack offHand = attacker.getOffhandItem();
        return (mainHand.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(mainHand))
                || (offHand.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(offHand));
    }

    /** Find the nearest hostile ranged attacker that is about to fire at this NPC. */
    default LivingEntity findThreateningRangedAttacker(FakeNpcEntity npc, double searchRadius) {
        AABB box = npc.getBoundingBox().inflate(searchRadius);
        List<LivingEntity> attackers = npc.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                attacker -> isValidCombatTarget(npc, attacker) && shouldBlockRangedAttack(npc, attacker)
        );

        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity attacker : attackers) {
            double distance = npc.distanceToSqr(attacker);
            if (distance < nearestDistance) {
                nearest = attacker;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * Find the best nearby weapon/shield/armor item entity worth walking to.
     * @param safeDistance combat targets at or beyond this distance are considered safe enough to loot
     */
    default ItemEntity findDesirableGroundLoot(FakeNpcEntity npc, double radius, @Nullable LivingEntity combatTarget, double safeDistance) {
        final int pathfindingShortlistSize = 12;
        boolean toolBreaking = isMainToolAboutToBreak(npc);
        boolean canLeaveCombat = combatTarget == null
                || !combatTarget.isAlive()
                || npc.distanceTo(combatTarget) >= safeDistance
                || (toolBreaking && npc.distanceTo(combatTarget) >= safeDistance * 0.5D);

        if (!canLeaveCombat) {
            return null;
        }

        AABB box = npc.getBoundingBox().inflate(radius);
        List<ItemEntity> items = npc.level().getEntitiesOfClass(
                ItemEntity.class,
                box,
                item -> item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
        );

        record LootCandidate(ItemEntity entity, double score, double distanceSqr) {}
        PriorityQueue<LootCandidate> shortlist = new PriorityQueue<>(Comparator
                .comparingDouble(LootCandidate::score)
                .thenComparing(Comparator.comparingDouble(LootCandidate::distanceSqr).reversed()));

        for (ItemEntity itemEntity : items) {
            if (!LootReservations.isAvailable(npc, itemEntity)) {
                continue;
            }

            ItemStack stack = itemEntity.getItem();
            double score = getDesirableLootScore(npc, stack);

            // Keep an existing claim when otherwise-equivalent drops are reordered.
            if (score > 0.0D && LootReservations.isClaimedBy(npc, itemEntity)) {
                score += 0.25D;
            }

            if (score <= 0.0D) continue;
            LootCandidate candidate = new LootCandidate(itemEntity, score, npc.distanceToSqr(itemEntity));
            if (shortlist.size() < pathfindingShortlistSize) {
                shortlist.add(candidate);
            } else {
                LootCandidate worst = shortlist.peek();
                if (score > worst.score()
                        || (score == worst.score() && candidate.distanceSqr() < worst.distanceSqr())) {
                    shortlist.poll();
                    shortlist.add(candidate);
                }
            }
        }

        List<LootCandidate> ranked = shortlist.stream()
                .sorted(Comparator.comparingDouble(LootCandidate::score).reversed()
                        .thenComparingDouble(LootCandidate::distanceSqr))
                .toList();
        for (LootCandidate candidate : ranked) {
            if (npc.getNpcNavigation().canReach(candidate.entity())) return candidate.entity();
        }
        return null;
    }

    /** Simple sensing hook — plug in a raycast/AABB scan here, feed results to memory. */
    default void sense(FakeNpcEntity npc) {
        // e.g. box-scan nearby entities, store into a per-controller memory map
    }
}
