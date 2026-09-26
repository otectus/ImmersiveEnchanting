package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The ritual did not start, or ended without an outcome, and nothing was charged. {@code sessionId} is null when no
 * session was created. The reason is localized on the client.
 */
public record RitualAbortedS2C(@Nullable UUID sessionId, int containerId, AbortReason reason) {

    public static void encode(RitualAbortedS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.sessionId != null);
        if (msg.sessionId != null) buf.writeUUID(msg.sessionId);
        buf.writeVarInt(msg.containerId);
        buf.writeVarInt(msg.reason.ordinal());
    }

    public static RitualAbortedS2C decode(FriendlyByteBuf buf) {
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        return new RitualAbortedS2C(id, buf.readVarInt(), AbortReason.byOrdinal(buf.readVarInt()));
    }

    public static void handle(RitualAbortedS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onAborted(msg));
    }
}
