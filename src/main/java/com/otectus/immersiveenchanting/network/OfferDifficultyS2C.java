package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Ritual complexity tier (0-5, or -1 for no offer) of each of the three offers. Difficulty only, never identities. */
public record OfferDifficultyS2C(int containerId, byte[] tiers) {

    public static void encode(OfferDifficultyS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeByteArray(msg.tiers);
    }

    public static OfferDifficultyS2C decode(FriendlyByteBuf buf) {
        return new OfferDifficultyS2C(buf.readVarInt(), buf.readByteArray(8));
    }

    public static void handle(OfferDifficultyS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onOfferDifficulty(msg));
    }
}
