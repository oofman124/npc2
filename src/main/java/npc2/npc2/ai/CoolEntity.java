package npc2.npc2.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.ModEntities;
import npc2.npc2.NpcController;

public class CoolEntity extends FakeNpcEntity implements NpcController {
    public final NpcBrain brain;

    public CoolEntity(EntityType<? extends FakeNpcEntity> type, Level level) {
        super(type, level);
        attachController(this);
        this.brain = new NpcBrain(this, null);
    }

    public CoolEntity(Level level) {
        this(ModEntities.FAKE_NPC, level);
    }

    public static CoolEntity spawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
        CoolEntity npc = new CoolEntity(ModEntities.FAKE_NPC, level);
        npc.configureSpawn(server, level, name, x, y, z);
        equipRandomGear(npc);
        level.addFreshEntity(npc);
        return npc;
    }

    private static void equipRandomGear(CoolEntity npc) {
        // Pools of items per slot. Leaving 'null' inside the array allows an "empty" rolled option.
        Item[] helmets = { null, Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET };
        Item[] chestplates = { null, Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE };
        Item[] leggings = { null, Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS };
        Item[] boots = { null, Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS };

        // Restricted to standard swords, axes, and pickaxes.
        Item[] weaponsAndTools = {
                null,
                Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
                Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
                Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE
        };

        Item[] offhands = { null, Items.SHIELD, Items.TOTEM_OF_UNDYING };

        npc.setItemSlot(EquipmentSlot.HEAD, getRandomStack(npc, helmets));
        npc.setItemSlot(EquipmentSlot.CHEST, getRandomStack(npc, chestplates));
        npc.setItemSlot(EquipmentSlot.LEGS, getRandomStack(npc, leggings));
        npc.setItemSlot(EquipmentSlot.FEET, getRandomStack(npc, boots));
        npc.setItemSlot(EquipmentSlot.MAINHAND, getRandomStack(npc, weaponsAndTools));
        npc.setItemSlot(EquipmentSlot.OFFHAND, getRandomStack(npc, offhands));
    }

    private static ItemStack getRandomStack(CoolEntity npc, Item[] pool) {
        int index = npc.getRandom().nextInt(pool.length);
        Item selectedItem = pool[index];
        return selectedItem == null ? ItemStack.EMPTY : new ItemStack(selectedItem);
    }

    @Override
    public void tick(FakeNpcEntity npc) {
        this.brain.Tick();
    }

    @Override
    public CoolEntity respawn() {
        this.detachController();
        if (!this.isRemoved()) {
            this.discard();
        }
        return spawn(this.npcServer, this.respawnLevel, this.npcName, this.respawnX, this.respawnY, this.respawnZ);
    }
}
