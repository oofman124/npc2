package npc2.npc2.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.network.NpcDebugRequestPayload;
import npc2.npc2.network.NpcDebugSnapshotPayload;

import java.util.List;

/** Right-side crosshair hover inspector for server-owned NPC state. */
public final class NpcDebugHud {
    private static final int PANEL_WIDTH = 196;
    private static final long REQUEST_INTERVAL_NANOS = 500_000_000L;
    private static final long SNAPSHOT_TIMEOUT_NANOS = 1_500_000_000L;
    private static NpcDebugSnapshotPayload snapshot;
    private static int displayedEntityId = -1;
    private static int pinnedEntityId = -1;
    private static long lastRequestNanos;
    private static long lastSnapshotNanos;

    private NpcDebugHud() {
    }

    public static void registerControls() {
        KeyMapping pinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.npc2.pin_debug", InputConstants.Type.KEYSYM, InputConstants.KEY_X, KeyMapping.Category.DEBUG));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (pinKey.consumeClick()) {
                if (client.gui.screen() == null) togglePin(client);
            }
        });
    }

    public static void accept(NpcDebugSnapshotPayload payload) {
        if (payload.entityId() == displayedEntityId) {
            snapshot = payload;
            lastSnapshotNanos = System.nanoTime();
        }
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            return;
        }

        int hovered = hoveredNpcId(minecraft);
        int entityId = pinnedEntityId >= 0 ? pinnedEntityId : hovered;
        if (entityId < 0) {
            displayedEntityId = -1;
            snapshot = null;
            return;
        }
        long now = System.nanoTime();
        if (displayedEntityId != entityId) {
            displayedEntityId = entityId;
            snapshot = null;
            lastRequestNanos = 0L;
        }
        if (now - lastRequestNanos >= REQUEST_INTERVAL_NANOS
                && ClientPlayNetworking.canSend(NpcDebugRequestPayload.TYPE)) {
            ClientPlayNetworking.send(new NpcDebugRequestPayload(entityId));
            lastRequestNanos = now;
        }
        if (snapshot == null || snapshot.entityId() != entityId
                || now - lastSnapshotNanos > SNAPSHOT_TIMEOUT_NANOS) {
            return;
        }
        drawPanel(graphics, minecraft.font, snapshot, pinnedEntityId == entityId);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, Font font, NpcDebugSnapshotPayload data, boolean pinned) {
        int x = Math.max(4, graphics.guiWidth() - PANEL_WIDTH - 6);
        int y = 4;
        int aiCount = Math.min(5, data.aiLines().size());
        int movementCount = Math.min(4, data.movementLines().size());
        int layoutCursor = y + 35 + 10 + aiCount * 9;
        layoutCursor += 2 + 10 + movementCount * 9;
        layoutCursor += 32;
        int inventoryRows = Math.max(1, (data.inventory().size() + 8) / 9);
        int requiredBottom = layoutCursor + 10 + inventoryRows * 19 + 5;
        int bottom = Math.min(graphics.guiHeight() - 2, requiredBottom);
        graphics.fill(x, y, x + PANEL_WIDTH, bottom, 0xD010141A);
        graphics.outline(x, y, PANEL_WIDTH, bottom - y, 0xFF5B91C9);

        text(graphics, font, (pinned ? "[PINNED] " : "") + data.name() + "  #" + data.entityId(),
                x + 7, y + 6, pinned ? 0xFFFFD45A : 0xFFFFFFFF);
        drawHealth(graphics, font, data, x + 7, y + 19);

        int cursor = y + 35;
        cursor = drawSection(graphics, font, "AI", data.aiLines(), 5, x + 7, cursor);
        cursor = drawSection(graphics, font, "Movement", data.movementLines(), 4, x + 7, cursor + 2);

        text(graphics, font, "Equipment", x + 7, cursor + 2, 0xFF79B8F3);
        drawSlots(graphics, font, data.equipment(), x + 7, cursor + 12, 6);
        cursor += 32;

        text(graphics, font, "Inventory", x + 7, cursor, 0xFF79B8F3);
        drawSlots(graphics, font, data.inventory(), x + 7, cursor + 10, 9);
    }

    private static void drawHealth(GuiGraphicsExtractor graphics, Font font, NpcDebugSnapshotPayload data, int x, int y) {
        int width = PANEL_WIDTH - 14;
        float ratio = data.maxHealth() <= 0.0F ? 0.0F : Math.clamp(data.health() / data.maxHealth(), 0.0F, 1.0F);
        graphics.fill(x, y, x + width, y + 9, 0xFF351B1B);
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * ratio), y + 8, 0xFFC83B3B);
        String health = String.format(java.util.Locale.ROOT, "%.1f / %.1f HP", data.health(), data.maxHealth());
        graphics.centeredText(font, health, x + width / 2, y + 1, 0xFFFFFFFF);
    }

    private static int drawSection(GuiGraphicsExtractor graphics, Font font, String title, List<String> lines,
                                   int limit, int x, int y) {
        text(graphics, font, title, x, y, 0xFF79B8F3);
        int cursor = y + 10;
        for (int i = 0; i < Math.min(limit, lines.size()); i++) {
            text(graphics, font, lines.get(i), x + 3, cursor, 0xFFD7DEE7);
            cursor += 9;
        }
        return cursor;
    }

    private static void drawSlots(GuiGraphicsExtractor graphics, Font font, List<ItemStack> stacks,
                                  int x, int y, int columns) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            int slotX = x + (slot % columns) * 19;
            int slotY = y + (slot / columns) * 19;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xB0293038);
            graphics.outline(slotX, slotY, 18, 18, 0xFF4B5663);
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty()) {
                graphics.item(stack, slotX + 1, slotY + 1);
                graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
            }
        }
    }

    private static void text(GuiGraphicsExtractor graphics, Font font, String value, int x, int y, int color) {
        graphics.text(font, font.plainSubstrByWidth(value, PANEL_WIDTH - 17), x, y, color, true);
    }

    private static void togglePin(Minecraft minecraft) {
        int hovered = hoveredNpcId(minecraft);
        if (pinnedEntityId >= 0) {
            pinnedEntityId = -1;
            if (hovered < 0) {
                displayedEntityId = -1;
                snapshot = null;
            }
        } else if (hovered >= 0) {
            pinnedEntityId = hovered;
            displayedEntityId = hovered;
            snapshot = null;
            lastRequestNanos = 0L;
        }
    }

    private static int hoveredNpcId(Minecraft minecraft) {
        return minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof FakeNpcEntity npc
                ? npc.getId() : -1;
    }
}
