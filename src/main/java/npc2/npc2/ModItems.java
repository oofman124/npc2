package npc2.npc2;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Item registry for the NPC spawn egg.
 */
public final class ModItems {
	public static final ResourceKey<Item> FAKE_NPC_SPAWN_EGG_KEY =
		ResourceKey.create(Registries.ITEM, Npc2.id("fake_npc_spawn_egg"));

	public static final Item FAKE_NPC_SPAWN_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		Npc2.id("fake_npc_spawn_egg"),
		new SpawnEggItem(new Item.Properties().setId(FAKE_NPC_SPAWN_EGG_KEY).spawnEgg(ModEntities.FAKE_NPC))
	);

	private ModItems() {
	}

	public static void register() {
	}
}
