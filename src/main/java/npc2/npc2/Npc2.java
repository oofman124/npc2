package npc2.npc2;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import npc2.npc2.network.NpcDebugNetworking;

public class Npc2 implements ModInitializer {
	public static final String MOD_ID = "npc2";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing npc2 survival NPCs");
		Npc2Config.load();
		ModEntities.register();
		ModItems.register();
		ModCreativeTabs.register();
		NpcDebugNetworking.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
