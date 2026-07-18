package npc2.npc2.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.NpcController;
import npc2.npc2.ai.survival.SurvivalNeeds;
import npc2.npc2.ai.util.ReachableApproach;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Finds, reserves, and transfers useful equipment from loaded chests. */
public final class ChestLooting {
    private static final Map<ChestKey, UUID> RESERVATIONS = new HashMap<>();

    private ChestLooting() {
    }

    public static @Nullable Target findTarget(FakeNpcEntity npc, NpcController controller, double radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        int chunkRadius = (int)Math.ceil(radius / 16.0D);
        int centerChunkX = npc.blockPosition().getX() >> 4;
        int centerChunkZ = npc.blockPosition().getZ() >> 4;
        double radiusSqr = radius * radius;
        Target best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChestBlockEntity chest)) {
                        continue;
                    }
                    BlockPos chestPos = chest.getBlockPos();
                    double distance = npc.distanceToSqr(Vec3.atCenterOf(chestPos));
                    if (distance > radiusSqr || distance >= bestDistance
                            || ChestBlock.isChestBlockedAt(level, chestPos)
                            || !isAvailable(npc, chestPos)
                            || !hasDesirableLoot(npc, controller, chest)) {
                        continue;
                    }
                    Vec3 approach = ReachableApproach.beside(npc, chestPos);
                    if (approach != null) {
                        best = new Target(chestPos.immutable(), approach);
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    public static @Nullable Target findDepositTarget(FakeNpcEntity npc, NpcController controller, double radius) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        int chunkRadius = (int)Math.ceil(radius / 16.0D);
        int centerChunkX = npc.blockPosition().getX() >> 4;
        int centerChunkZ = npc.blockPosition().getZ() >> 4;
        double radiusSqr = radius * radius;
        Target best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChestBlockEntity chest)) continue;
                    BlockPos chestPos = chest.getBlockPos();
                    double distance = npc.distanceToSqr(Vec3.atCenterOf(chestPos));
                    if (distance > radiusSqr || distance >= bestDistance
                            || ChestBlock.isChestBlockedAt(level, chestPos)
                            || !isAvailable(npc, chestPos) || !canAcceptAnyDeposit(npc, controller, chest)) {
                        continue;
                    }
                    Vec3 approach = ReachableApproach.beside(npc, chestPos);
                    if (approach != null) {
                        best = new Target(chestPos.immutable(), approach);
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    public static boolean claim(FakeNpcEntity npc, Target target) {
        ServerLevel level = (ServerLevel)npc.level();
        prune(level);
        ChestKey key = new ChestKey(level.dimension(), target.chestPos);
        UUID owner = RESERVATIONS.get(key);
        if (owner != null && !owner.equals(npc.getUUID())) {
            return false;
        }
        release(npc);
        RESERVATIONS.put(key, npc.getUUID());
        return true;
    }

    public static boolean isStillDesirable(FakeNpcEntity npc, NpcController controller, Target target) {
        BlockEntity blockEntity = npc.level().getBlockEntity(target.chestPos);
        return blockEntity instanceof ChestBlockEntity chest
                && !ChestBlock.isChestBlockedAt(npc.level(), target.chestPos)
                && hasDesirableLoot(npc, controller, chest);
    }

    public static boolean isStillDepositable(FakeNpcEntity npc, NpcController controller, Target target) {
        BlockEntity blockEntity = npc.level().getBlockEntity(target.chestPos);
        return blockEntity instanceof ChestBlockEntity chest
                && !ChestBlock.isChestBlockedAt(npc.level(), target.chestPos)
                && hasItemsToDeposit(npc, controller)
                && canAcceptAnyDeposit(npc, controller, chest);
    }

    public static boolean hasItemsToDeposit(FakeNpcEntity npc, NpcController controller) {
        SimpleContainer bag = npc.getInventory();
        boolean crowded = occupiedSlots(bag) >= 22;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (depositAmount(npc, controller, bag.getItem(slot), crowded) > 0) return true;
        }
        return false;
    }

    public static boolean deposit(FakeNpcEntity npc, NpcController controller, Target target) {
        BlockEntity blockEntity = npc.level().getBlockEntity(target.chestPos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) return false;
        SimpleContainer bag = npc.getInventory();
        boolean movedAny = false;

        for (int bagSlot = 0; bagSlot < bag.getContainerSize(); bagSlot++) {
            ItemStack stack = bag.getItem(bagSlot);
            int amount = depositAmount(npc, controller, stack, occupiedSlots(bag) >= 22);
            if (amount <= 0) continue;
            int moved = moveInto(chest, stack, amount);
            if (moved > 0) {
                stack.shrink(moved);
                if (stack.isEmpty()) bag.setItem(bagSlot, ItemStack.EMPTY);
                movedAny = true;
            }
        }
        if (movedAny) {
            bag.setChanged();
            chest.setChanged();
        }
        return movedAny;
    }

    public static boolean loot(FakeNpcEntity npc, NpcController controller, Target target) {
        BlockEntity blockEntity = npc.level().getBlockEntity(target.chestPos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            return false;
        }

        boolean movedAny = false;
        SimpleContainer bag = npc.getInventory();
        while (true) {
            int bestSlot = -1;
            double bestScore = 0.0D;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                double score = controller.getDesirableLootScore(npc, chest.getItem(slot));
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
            if (bestSlot < 0) {
                break;
            }

            ItemStack chestStack = chest.getItem(bestSlot);
            int originalCount = chestStack.getCount();
            ItemStack remainder = bag.addItem(chestStack.copy());
            int moved = originalCount - remainder.getCount();
            if (moved <= 0) {
                break;
            }
            chestStack.shrink(moved);
            chest.setItem(bestSlot, chestStack);
            movedAny = true;
        }
        if (movedAny) {
            chest.setChanged();
        }
        return movedAny;
    }

    public static void release(FakeNpcEntity npc) {
        UUID npcId = npc.getUUID();
        RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(npcId));
    }

    private static boolean hasDesirableLoot(FakeNpcEntity npc, NpcController controller, Container chest) {
        SimpleContainer bag = npc.getInventory();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (controller.getDesirableLootScore(npc, stack) > 0.0D && bag.canAddItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canAcceptAnyDeposit(FakeNpcEntity npc, NpcController controller, Container chest) {
        SimpleContainer bag = npc.getInventory();
        boolean crowded = occupiedSlots(bag) >= 22;
        for (ItemStack stack : bag) {
            if (depositAmount(npc, controller, stack, crowded) > 0 && hasSpace(chest, stack)) return true;
        }
        return false;
    }

    private static int depositAmount(FakeNpcEntity npc, NpcController controller, ItemStack stack, boolean crowded) {
        if (stack.isEmpty()) return 0;
        SimpleContainer bag = npc.getInventory();
        if (stack.is(ItemTags.BEDS) && controller.countInventoryTag(bag, ItemTags.BEDS) <= 1) return 0;
        int excess = 0;
        if (controller.isFood(stack)) excess = controller.countFood(npc) - SurvivalNeeds.FOOD_TARGET;
        else if (stack.is(ItemTags.LOGS)) excess = controller.countInventoryTag(bag, ItemTags.LOGS) - SurvivalNeeds.LOG_TARGET;
        else if (stack.is(ItemTags.PLANKS)) excess = controller.countInventoryTag(bag, ItemTags.PLANKS) - 16;
        else if (stack.is(Items.STICK)) excess = bag.countItem(Items.STICK) - 8;
        else if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL))
            excess = SurvivalNeeds.countFuel(bag) - SurvivalNeeds.FUEL_TARGET;
        else if (stack.is(Items.TORCH)) excess = bag.countItem(Items.TORCH) - SurvivalNeeds.TORCH_TARGET;
        else if (stack.is(Items.IRON_INGOT)) excess = bag.countItem(Items.IRON_INGOT) - 16;
        else if (stack.is(Items.COBBLESTONE)) excess = bag.countItem(Items.COBBLESTONE) - SurvivalNeeds.COBBLESTONE_TARGET;
        else if (stack.is(Items.DIRT)) excess = bag.countItem(Items.DIRT) - SurvivalNeeds.SOIL_TARGET;
        else if (stack.is(Items.NETHERRACK)) excess = bag.countItem(Items.NETHERRACK) - SurvivalNeeds.SOIL_TARGET;
        if (excess > 0) return Math.min(excess, stack.getCount());
        if (crowded && controller.getDesirableLootScore(npc, stack) <= 0.0D) return stack.getCount();
        return 0;
    }

    private static int occupiedSlots(SimpleContainer bag) {
        int occupied = 0;
        for (ItemStack stack : bag) if (!stack.isEmpty()) occupied++;
        return occupied;
    }

    private static boolean hasSpace(Container chest, ItemStack incoming) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stored = chest.getItem(slot);
            if (stored.isEmpty() || (ItemStack.isSameItemSameComponents(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize())) return true;
        }
        return false;
    }

    private static int moveInto(Container chest, ItemStack source, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < chest.getContainerSize() && remaining > 0; slot++) {
            ItemStack stored = chest.getItem(slot);
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, source)) {
                int moved = Math.min(remaining, stored.getMaxStackSize() - stored.getCount());
                if (moved > 0) {
                    stored.grow(moved);
                    remaining -= moved;
                }
            }
        }
        for (int slot = 0; slot < chest.getContainerSize() && remaining > 0; slot++) {
            if (!chest.getItem(slot).isEmpty()) continue;
            int moved = Math.min(remaining, source.getMaxStackSize());
            chest.setItem(slot, source.copyWithCount(moved));
            remaining -= moved;
        }
        return amount - remaining;
    }

    private static boolean isAvailable(FakeNpcEntity npc, BlockPos chestPos) {
        ServerLevel level = (ServerLevel)npc.level();
        UUID owner = RESERVATIONS.get(new ChestKey(level.dimension(), chestPos));
        return owner == null || owner.equals(npc.getUUID());
    }

    private static void prune(ServerLevel level) {
        RESERVATIONS.entrySet().removeIf(entry -> {
            ChestKey key = entry.getKey();
            if (!key.dimension.equals(level.dimension())) {
                return false;
            }
            return !(level.getBlockEntity(key.pos) instanceof ChestBlockEntity)
                    || !(level.getEntity(entry.getValue()) instanceof FakeNpcEntity owner)
                    || !owner.isAlive();
        });
    }

    public record Target(BlockPos chestPos, Vec3 approachPosition) {
    }

    private record ChestKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
