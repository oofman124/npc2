package npc2.npc2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import npc2.npc2.Npc2;

import java.util.ArrayList;
import java.util.List;

public record NpcDebugSnapshotPayload(
        int entityId,
        String name,
        float health,
        float maxHealth,
        List<String> aiLines,
        List<String> movementLines,
        List<NeedEntry> needs,
        List<ItemStack> statusIcons,
        List<ItemStack> equipment,
        List<ItemStack> inventory,
        List<BlockPos> pathNodes,
        int nextPathNode,
        boolean pathReachable
) implements CustomPacketPayload {
    private static final int MAX_LINES = 16;
    private static final int MAX_STACKS = 40;
    private static final int MAX_NEEDS = 8;
    private static final int MAX_PATH_NODES = 128;
    public static final Type<NpcDebugSnapshotPayload> TYPE = new Type<>(Npc2.id("npc_debug_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDebugSnapshotPayload> CODEC = StreamCodec.of(
            NpcDebugSnapshotPayload::encode,
            NpcDebugSnapshotPayload::decode
    );

    public NpcDebugSnapshotPayload {
        aiLines = List.copyOf(aiLines);
        movementLines = List.copyOf(movementLines);
        needs = needs.stream().map(NeedEntry::copy).toList();
        statusIcons = copyStacks(statusIcons);
        equipment = copyStacks(equipment);
        inventory = copyStacks(inventory);
        pathNodes = List.copyOf(pathNodes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcDebugSnapshotPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeUtf(payload.name, 128);
        buffer.writeFloat(payload.health);
        buffer.writeFloat(payload.maxHealth);
        writeStrings(buffer, payload.aiLines);
        writeStrings(buffer, payload.movementLines);
        writeNeeds(buffer, payload.needs);
        writeStacks(buffer, payload.statusIcons);
        writeStacks(buffer, payload.equipment);
        writeStacks(buffer, payload.inventory);
        writePath(buffer, payload.pathNodes);
        buffer.writeVarInt(payload.nextPathNode);
        buffer.writeBoolean(payload.pathReachable);
    }

    private static NpcDebugSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcDebugSnapshotPayload(
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readFloat(),
                buffer.readFloat(),
                readStrings(buffer),
                readStrings(buffer),
                readNeeds(buffer),
                readStacks(buffer),
                readStacks(buffer),
                readStacks(buffer),
                readPath(buffer),
                buffer.readVarInt(),
                buffer.readBoolean()
        );
    }

    private static void writeNeeds(RegistryFriendlyByteBuf buffer, List<NeedEntry> needs) {
        int size = Math.min(needs.size(), MAX_NEEDS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            NeedEntry need = needs.get(i);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, need.icon);
            buffer.writeUtf(need.label, 64);
            buffer.writeVarInt(need.current);
            buffer.writeVarInt(need.target);
            buffer.writeDouble(need.score);
            buffer.writeFloat(need.confidence);
            buffer.writeUtf(need.reason, 160);
        }
    }

    private static List<NeedEntry> readNeeds(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_NEEDS);
        List<NeedEntry> needs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            needs.add(new NeedEntry(
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readUtf(160)));
        }
        return needs;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values) {
        int size = Math.min(values.size(), MAX_LINES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) buffer.writeUtf(values.get(i), 160);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_LINES);
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buffer.readUtf(160));
        return values;
    }

    private static void writeStacks(RegistryFriendlyByteBuf buffer, List<ItemStack> stacks) {
        int size = Math.min(stacks.size(), MAX_STACKS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stacks.get(i));
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_STACKS);
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        return stacks;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static void writePath(RegistryFriendlyByteBuf buffer, List<BlockPos> path) {
        int size = Math.min(path.size(), MAX_PATH_NODES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) buffer.writeBlockPos(path.get(i));
    }

    private static List<BlockPos> readPath(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_PATH_NODES);
        List<BlockPos> path = new ArrayList<>(size);
        for (int i = 0; i < size; i++) path.add(buffer.readBlockPos());
        return path;
    }

    private static int checkedSize(int size, int maximum) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid NPC debug list size: " + size);
        return size;
    }

    public record NeedEntry(ItemStack icon, String label, int current, int target,
                            double score, float confidence, String reason) {
        private NeedEntry copy() {
            return new NeedEntry(this.icon.copy(), this.label, this.current, this.target,
                    this.score, this.confidence, this.reason);
        }
    }
}
