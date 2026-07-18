package npc2.npc2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import npc2.npc2.Npc2;

public record NpcDebugRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<NpcDebugRequestPayload> TYPE = new Type<>(Npc2.id("npc_debug_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDebugRequestPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.entityId),
            buffer -> new NpcDebugRequestPayload(buffer.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
