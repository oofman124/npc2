package npc2.npc2.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import npc2.npc2.FakeNpcEntity;
import npc2.npc2.network.NpcDebugRequestPayload;
import npc2.npc2.network.NpcDebugSnapshotPayload;

import java.util.List;

/** Right-side crosshair hover inspector for server-owned NPC state. */
public final class NpcDebugHud {
    private static final int PANEL_WIDTH = 282;
    private static final int INNER_WIDTH = PANEL_WIDTH - 14;
    private static final int COLUMN_GAP = 8;
    private static final int COLUMN_WIDTH = (INNER_WIDTH - COLUMN_GAP) / 2;
    private static final long REQUEST_INTERVAL_NANOS = 500_000_000L;
    private static final long SNAPSHOT_TIMEOUT_NANOS = 1_500_000_000L;
    private static NpcDebugSnapshotPayload snapshot;
    private static int displayedEntityId = -1;
    private static int pinnedEntityId = -1;
    private static long lastRequestNanos;
    private static long lastSnapshotNanos;
    private static int highlightedEntityId = -1;

    private NpcDebugHud() {
    }

    public static void registerControls() {
        KeyMapping pinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.npc2.pin_debug", InputConstants.Type.KEYSYM, InputConstants.KEY_X, KeyMapping.Category.DEBUG));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (pinKey.consumeClick()) {
                if (client.gui.screen() == null) togglePin(client);
            }
            if (client.level != null && client.gui.screen() == null) {
                try (var ignored = client.collectPerTickGizmos()) {
                    emitPathTrace(client);
                }
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
        clearInvalidPin(minecraft);
        if (minecraft.gui.screen() != null) {
            updateHighlight(minecraft, -1);
            return;
        }

        int hovered = hoveredNpcId(minecraft);
        int entityId = pinnedEntityId >= 0 ? pinnedEntityId : hovered;
        if (entityId < 0) {
            updateHighlight(minecraft, -1);
            displayedEntityId = -1;
            snapshot = null;
            return;
        }
        updateHighlight(minecraft, entityId);
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

    /** A removed target must not leave the otherwise invisible HUD logically pinned. */
    private static void clearInvalidPin(Minecraft minecraft) {
        if (pinnedEntityId < 0) return;
        Entity pinned = minecraft.level == null ? null : minecraft.level.getEntity(pinnedEntityId);
        if (pinned instanceof FakeNpcEntity npc && npc.isAlive() && !npc.isRemoved()) return;

        updateHighlight(minecraft, -1);
        pinnedEntityId = -1;
        displayedEntityId = -1;
        snapshot = null;
        lastRequestNanos = 0L;
        lastSnapshotNanos = 0L;
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, Font font, NpcDebugSnapshotPayload data, boolean pinned) {
        int x = Math.max(4, graphics.guiWidth() - PANEL_WIDTH - 6);
        int y = 4;
        int aiCount = Math.min(7, data.aiLines().size());
        int movementCount = Math.min(6, data.movementLines().size());
        int columnsY = y + 59;
        int leftBottom = columnsY + 10 + aiCount * 9 + 4 + 10 + movementCount * 9;
        int needCount = Math.min(4, data.needs().size());
        int rightBottom = columnsY + 10 + Math.max(9, needCount * 26) + 4 + 32;
        int inventoryY = Math.max(leftBottom, rightBottom) + 5;
        int inventoryColumns = 14;
        int inventoryRows = Math.max(1, (data.inventory().size() + inventoryColumns - 1) / inventoryColumns);
        int requiredBottom = inventoryY + 10 + inventoryRows * 19 + 5;
        int bottom = Math.min(graphics.guiHeight() - 2, requiredBottom);
        graphics.fill(x, y, x + PANEL_WIDTH, bottom, 0xD010141A);
        graphics.outline(x, y, PANEL_WIDTH, bottom - y, 0xFF5B91C9);

        text(graphics, font, (pinned ? "[PINNED] " : "") + data.name() + "  #" + data.entityId(),
                x + 7, y + 6, pinned ? 0xFFFFD45A : 0xFFFFFFFF);
        drawHealth(graphics, font, data, x + 7, y + 19);

        int statusY = y + 36;
        textClipped(graphics, font, "Status", x + 7, statusY + 5, 0xFF79B8F3, 40);
        drawSlots(graphics, font, data.statusIcons(), x + 50, statusY, 10);

        int leftX = x + 7;
        int rightX = leftX + COLUMN_WIDTH + COLUMN_GAP;
        int leftCursor = drawSection(graphics, font, "Decision", data.aiLines(), 7,
                leftX, columnsY, COLUMN_WIDTH);
        drawSection(graphics, font, "Movement / path", data.movementLines(), 6,
                leftX, leftCursor + 4, COLUMN_WIDTH);

        textClipped(graphics, font, "Resource needs", rightX, columnsY, 0xFF79B8F3, COLUMN_WIDTH);
        int rightCursor = columnsY + 10;
        if (needCount == 0) {
            textClipped(graphics, font, "No current deficits", rightX + 3, rightCursor,
                    0xFF93A0AE, COLUMN_WIDTH - 3);
            rightCursor += 9;
        } else {
            for (int index = 0; index < needCount; index++) {
                drawNeed(graphics, font, data.needs().get(index), rightX, rightCursor, COLUMN_WIDTH);
                rightCursor += 26;
            }
        }
        rightCursor += 4;
        textClipped(graphics, font, "Equipment", rightX, rightCursor, 0xFF79B8F3, COLUMN_WIDTH);
        drawSlots(graphics, font, data.equipment(), rightX, rightCursor + 10, 6);

        textClipped(graphics, font, "Inventory", x + 7, inventoryY, 0xFF79B8F3, INNER_WIDTH);
        drawSlots(graphics, font, data.inventory(), x + 7, inventoryY + 10, inventoryColumns);
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
                                   int limit, int x, int y, int width) {
        textClipped(graphics, font, title, x, y, 0xFF79B8F3, width);
        int cursor = y + 10;
        for (int i = 0; i < Math.min(limit, lines.size()); i++) {
            textClipped(graphics, font, lines.get(i), x + 3, cursor, 0xFFD7DEE7, width - 3);
            cursor += 9;
        }
        return cursor;
    }

    private static void drawNeed(GuiGraphicsExtractor graphics, Font font,
                                 NpcDebugSnapshotPayload.NeedEntry need, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 24, 0x8A202832);
        graphics.outline(x, y, width, 24, 0xFF3C4A58);
        graphics.item(need.icon(), x + 2, y + 3);
        int textX = x + 22;
        textClipped(graphics, font, need.label() + "  " + need.current() + "/" + need.target(),
                textX, y + 2, 0xFFF1F4F7, width - 24);
        String score = String.format(java.util.Locale.ROOT, "score %.0f  local %.0f%%",
                need.score(), need.confidence() * 100.0F);
        textClipped(graphics, font, score, textX, y + 11, confidenceColor(need.confidence()), width - 24);
        int barWidth = width - 24;
        graphics.fill(textX, y + 21, textX + barWidth, y + 23, 0xFF151A20);
        graphics.fill(textX, y + 21, textX + Math.round(barWidth * Math.clamp(need.confidence(), 0.0F, 1.0F)),
                y + 23, confidenceColor(need.confidence()));
    }

    private static int confidenceColor(float confidence) {
        if (confidence >= 0.6F) return 0xFF67D17A;
        if (confidence >= 0.2F) return 0xFFFFC857;
        return 0xFFEF6A6A;
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
        textClipped(graphics, font, value, x, y, color, PANEL_WIDTH - 17);
    }

    private static void textClipped(GuiGraphicsExtractor graphics, Font font, String value,
                                    int x, int y, int color, int width) {
        graphics.text(font, font.plainSubstrByWidth(value, Math.max(1, width)), x, y, color, true);
    }

    /** Emit a visible trace for the currently hovered or pinned NPC's server path. */
    private static void emitPathTrace(Minecraft minecraft) {
        NpcDebugSnapshotPayload data = snapshot;
        if (data == null || data.entityId() != displayedEntityId
                || System.nanoTime() - lastSnapshotNanos > SNAPSHOT_TIMEOUT_NANOS
                || data.pathNodes().isEmpty()) return;
        if (minecraft.level == null
                || !(minecraft.level.getEntity(data.entityId()) instanceof FakeNpcEntity npc)) return;

        List<BlockPos> nodes = data.pathNodes();
        int next = Math.clamp(data.nextPathNode(), 0, nodes.size() - 1);
        Vec3 npcPosition = npc.position().add(0.0D, 0.15D, 0.0D);
        Vec3 nextPosition = Vec3.atBottomCenterOf(nodes.get(next)).add(0.0D, 0.15D, 0.0D);
        Gizmos.line(npcPosition, nextPosition, 0xFFFFC857, 3.0F).setAlwaysOnTop();

        for (int index = 0; index + 1 < nodes.size(); index++) {
            Vec3 start = Vec3.atBottomCenterOf(nodes.get(index)).add(0.0D, 0.15D, 0.0D);
            Vec3 end = Vec3.atBottomCenterOf(nodes.get(index + 1)).add(0.0D, 0.15D, 0.0D);
            int color = index + 1 < next ? 0x777C8794
                    : data.pathReachable() ? 0xDD42D9FF : 0xDDEF6A6A;
            Gizmos.line(start, end, color, 2.5F).setAlwaysOnTop();
        }
        Gizmos.point(nextPosition, 0xFFFFC857, 0.14F).setAlwaysOnTop();
        Vec3 destination = Vec3.atBottomCenterOf(nodes.getLast()).add(0.0D, 0.2D, 0.0D);
        Gizmos.point(destination, data.pathReachable() ? 0xFF67D17A : 0xFFEF6A6A, 0.2F)
                .setAlwaysOnTop();
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
        if (minecraft.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof FakeNpcEntity npc
                && npc.isAlive() && !npc.isRemoved()) {
            return npc.getId();
        }
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || minecraft.level == null) return -1;

        Vec3 start = camera.getEyePosition();
        Vec3 view = camera.getViewVector(1.0F).normalize();
        Vec3 end = start.add(view.scale(1_000_000.0D));
        FakeNpcEntity selected = null;
        Vec3 selectedHit = null;
        double closest = Double.POSITIVE_INFINITY;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof FakeNpcEntity candidate) || !candidate.isAlive()) continue;
            double forwardDistance = candidate.getBoundingBox().getCenter().subtract(start).dot(view);
            if (forwardDistance <= 0.0D) continue;
            // A small angular cone makes nearby NPCs easy to acquire and remains
            // practical at long range without selecting half the screen.
            double tolerance = Math.min(6.0D, 0.8D + forwardDistance * 0.025D);
            Vec3 intersection = candidate.getBoundingBox().inflate(tolerance).clip(start, end).orElse(null);
            if (intersection == null) continue;
            double distance = start.distanceToSqr(intersection);
            if (distance < closest) {
                closest = distance;
                selected = candidate;
                selectedHit = intersection;
            }
        }
        if (selected == null || selectedHit == null) return -1;

        Vec3 selectedCenter = selected.getBoundingBox().getCenter();
        HitResult obstruction = minecraft.level.clip(new ClipContext(
                start, selectedCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        if (obstruction.getType() == HitResult.Type.BLOCK
                && start.distanceToSqr(obstruction.getLocation()) + 0.25D
                < start.distanceToSqr(selectedCenter)) return -1;
        return selected.getId();
    }

    private static void updateHighlight(Minecraft minecraft, int entityId) {
        if (highlightedEntityId == entityId) {
            if (entityId >= 0 && minecraft.level != null
                    && minecraft.level.getEntity(entityId) instanceof FakeNpcEntity npc) {
                npc.setGlowingTag(true);
            }
            return;
        }
        if (highlightedEntityId >= 0 && minecraft.level != null
                && minecraft.level.getEntity(highlightedEntityId) instanceof FakeNpcEntity previous) {
            previous.setGlowingTag(false);
        }
        highlightedEntityId = entityId;
        if (entityId >= 0 && minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof FakeNpcEntity npc) {
            npc.setGlowingTag(true);
        }
    }
}
