package npc2.npc2;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import npc2.npc2.ai.CoolEntity;

/**
 * Registry for custom NPC entity types.
 */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> FAKE_NPC_KEY =
		ResourceKey.create(Registries.ENTITY_TYPE, Npc2.id("fake_npc"));

	public static final EntityType<CoolEntity> FAKE_NPC = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		FAKE_NPC_KEY,
		FabricEntityType.Builder.<CoolEntity>createMob(
			npc2.npc2.ai.CoolEntity::new,
			MobCategory.CREATURE,
			mob -> mob.defaultAttributes(FakeNpcEntity::createAttributes)
		)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.clientTrackingRange(8)
			.updateInterval(3)
			.build(FAKE_NPC_KEY)
	);

	private ModEntities() {
	}

	public static void register() {
	}
}
