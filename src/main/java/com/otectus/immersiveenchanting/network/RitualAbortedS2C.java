package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The ritual did not start, or ended without an outcome, and nothing was charged. {@code sessionId} is null when no
 * session was created. The reason is localized on the client; {@code detail}, when present, is a message another mod
 * composed (a {@code RitualPlanEvent} denial) and is shown instead of the reason's generic text.
 */
public record RitualAbortedS2C(@Nullable UUID sessionId, int containerId, AbortReason reason, @Nullable Component detail) {

    public RitualAbortedS2C(@Nullable UUID sessionId, int containerId, AbortReason reason) {
        this(sessionId, containerId, reason, null);
    }

    public static void encode(RitualAbortedS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.sessionId != null);
        if (msg.sessionId != null) buf.writeUUID(msg.sessionId);
        buf.writeVarInt(msg.containerId);
        buf.writeVarInt(msg.reason.ordinal());
        buf.writeBoolean(msg.detail != null);
        if (msg.detail != null) buf.writeComponent(msg.detail);
    }

    public static RitualAbortedS2C decode(FriendlyByteBuf buf) {
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        int containerId = buf.readVarInt();
        AbortReason reason = AbortReason.byOrdinal(buf.readVarInt());
        Component detail = buf.readBoolean() ? buf.readComponent() : null;
        return new RitualAbortedS2C(id, containerId, reason, detail);
    }

    public static void handle(RitualAbortedS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onAborted(msg));
    }
}
