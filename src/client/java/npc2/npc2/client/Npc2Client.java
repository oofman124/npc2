package npc2.npc2.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import npc2.npc2.ModEntities;
import npc2.npc2.Npc2;
import npc2.npc2.Npc2Config;
import npc2.npc2.network.NpcDebugSnapshotPayload;

import java.net.URI;

public class Npc2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.FAKE_NPC, FakeNpcRenderer::new);
		NpcDebugHud.registerControls();
		ClientPlayNetworking.registerGlobalReceiver(NpcDebugSnapshotPayload.TYPE,
			(payload, context) -> NpcDebugHud.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
			NpcDebugHud.reset(client));
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) ->
			client.execute(() -> {
				if (!Npc2Config.get().startupMessage) return;
				var giveEggLink = Component.translatable("message.npc2.give_egg")
					.withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("/give @s npc2:fake_npc_spawn_egg"))
						.withHoverEvent(new HoverEvent.ShowText(
							Component.translatable("message.npc2.give_egg.hover"))));
				var issueLink = Component.translatable("message.npc2.issues_link")
					.withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(
							URI.create("https://github.com/oofman124/npc2/issues")))
						.withHoverEvent(new HoverEvent.ShowText(
							Component.translatable("message.npc2.issues_link.hover"))));
				var startupMessage = Component.empty()
					.append(Component.translatable("message.npc2.loaded")
						.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
					.append("\n")
					.append(Component.translatable("message.npc2.quickstart", giveEggLink)
						.withStyle(ChatFormatting.GRAY))
					.append("\n")
					.append(Component.translatable("message.npc2.quickstart2")
						.withStyle(ChatFormatting.YELLOW))
					.append("\n")
					.append(Component.translatable("message.npc2.debug_controls",
							NpcDebugHud.pinKeyName(), NpcDebugHud.toggleDebugKeyName())
						.withStyle(ChatFormatting.GRAY))
					.append("\n")
					.append(Component.translatable("message.npc2.quickstart3", issueLink)
						.withStyle(ChatFormatting.GRAY));
				client.gui.hud.getChat().addClientSystemMessage(startupMessage);
			}));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Npc2.id("npc_debug_hud"),
			(graphics, deltaTracker) -> NpcDebugHud.render(graphics)
		);
	}
}
