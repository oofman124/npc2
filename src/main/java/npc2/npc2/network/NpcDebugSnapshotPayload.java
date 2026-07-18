package npc2.npc2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
        List<ItemStack> equipment,
        List<ItemStack> inventory
) implements CustomPacketPayload {
    private static final int MAX_LINES = 16;
    private static final int MAX_STACKS = 40;
    public static final Type<NpcDebugSnapshotPayload> TYPE = new Type<>(Npc2.id("npc_debug_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDebugSnapshotPayload> CODEC = StreamCodec.of(
            NpcDebugSnapshotPayload::encode,
            NpcDebugSnapshotPayload::decode
    );

    public NpcDebugSnapshotPayload {
        aiLines = List.copyOf(aiLines);
        movementLines = List.copyOf(movementLines);
        equipment = copyStacks(equipment);
        inventory = copyStacks(inventory);
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
        writeStacks(buffer, payload.equipment);
        writeStacks(buffer, payload.inventory);
    }

    private static NpcDebugSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcDebugSnapshotPayload(
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readFloat(),
                buffer.readFloat(),
                readStrings(buffer),
                readStrings(buffer),
                readStacks(buffer),
                readStacks(buffer)
        );
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

    private static int checkedSize(int size, int maximum) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid NPC debug list size: " + size);
        return size;
    }
}
