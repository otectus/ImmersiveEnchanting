package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player clicked an enchant offer. Only the container and offer are claimed; the server validates everything
 * else. {@code timingAssist} is the player's accessibility setting, which the server caps.
 */
public record StartRitualC2S(int containerId, int offerIndex, float timingAssist) {

    public static void encode(StartRitualC2S msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeVarInt(msg.offerIndex);
        buf.writeFloat(msg.timingAssist);
    }

    public static StartRitualC2S decode(FriendlyByteBuf buf) {
        return new StartRitualC2S(buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(StartRitualC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) RitualSessionManager.start(player, msg.containerId, msg.offerIndex, msg.timingAssist);
    }
}
