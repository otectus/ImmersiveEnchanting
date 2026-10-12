package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Ritual complexity tier (0-5, or -1 for no offer) of each of the three offers, and, since 1.1.0, the reason a
 * {@code RitualPlanEvent} listener locked an offer (null when it is open). Difficulty and locks only, never identities.
 */
public record OfferDifficultyS2C(int containerId, byte[] tiers, @Nullable Component[] denials) {

    public OfferDifficultyS2C(int containerId, byte[] tiers) {
        this(containerId, tiers, new Component[tiers.length]);
    }

    public static void encode(OfferDifficultyS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeByteArray(msg.tiers);
        Component[] denials = msg.denials == null ? new Component[msg.tiers.length] : msg.denials;
        buf.writeVarInt(denials.length);
        for (Component denial : denials) {
            buf.writeBoolean(denial != null);
            if (denial != null) buf.writeComponent(denial);
        }
    }

    public static OfferDifficultyS2C decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        byte[] tiers = buf.readByteArray(8);
        int count = Math.min(8, buf.readVarInt());
        Component[] denials = new Component[count];
        for (int i = 0; i < count; i++) denials[i] = buf.readBoolean() ? buf.readComponent() : null;
        return new OfferDifficultyS2C(containerId, tiers, denials);
    }

    public static void handle(OfferDifficultyS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onOfferDifficulty(msg));
    }
}
