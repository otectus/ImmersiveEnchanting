package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** The player backed out. Free during the count-in; afterwards the ritual resolves as a failure. */
public record RitualCancelC2S(UUID sessionId) {

    public static void encode(RitualCancelC2S msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.sessionId);
    }

    public static RitualCancelC2S decode(FriendlyByteBuf buf) {
        return new RitualCancelC2S(buf.readUUID());
    }

    public static void handle(RitualCancelC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) RitualSessionManager.cancel(player, msg.sessionId);
    }
}
