package npc2.npc2.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import npc2.npc2.ModEntities;
import npc2.npc2.Npc2;
import npc2.npc2.network.NpcDebugSnapshotPayload;

public class Npc2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.FAKE_NPC, FakeNpcRenderer::new);
		NpcDebugHud.registerControls();
		ClientPlayNetworking.registerGlobalReceiver(NpcDebugSnapshotPayload.TYPE,
			(payload, context) -> NpcDebugHud.accept(payload));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Npc2.id("npc_debug_hud"),
			(graphics, deltaTracker) -> NpcDebugHud.render(graphics)
		);
	}
}
