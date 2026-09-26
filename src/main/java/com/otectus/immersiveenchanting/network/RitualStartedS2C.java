package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.client.ClientPacketHandler;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything the client needs to play the ritual, and nothing about the hidden plan: the pattern seed and resolved
 * parameters (the client generates the same timeline), the theme, and the offer's clue, which the table already
 * showed. The client's ritual clock starts when this arrives.
 */
public record RitualStartedS2C(UUID sessionId, int containerId, int offerIndex, long patternSeed, String patternSetId,
                               int generatorVersion, RitualParams params, ResourceLocation theme,
                               @Nullable ResourceLocation clueId, int clueLevel) {

    public static void encode(RitualStartedS2C msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.sessionId);
        buf.writeVarInt(msg.containerId);
        buf.writeVarInt(msg.offerIndex);
        buf.writeLong(msg.patternSeed);
        buf.writeUtf(msg.patternSetId, 256);
        buf.writeVarInt(msg.generatorVersion);
        ParamsCodec.write(buf, msg.params);
        buf.writeResourceLocation(msg.theme);
        buf.writeBoolean(msg.clueId != null);
        if (msg.clueId != null) buf.writeResourceLocation(msg.clueId);
        buf.writeVarInt(msg.clueLevel);
    }

    public static RitualStartedS2C decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int container = buf.readVarInt();
        int offer = buf.readVarInt();
        long seed = buf.readLong();
        String set = buf.readUtf(256);
        int version = buf.readVarInt();
        RitualParams params = ParamsCodec.read(buf);
        ResourceLocation theme = buf.readResourceLocation();
        ResourceLocation clue = buf.readBoolean() ? buf.readResourceLocation() : null;
        int clueLevel = buf.readVarInt();
        return new RitualStartedS2C(id, container, offer, seed, set, version, params, theme, clue, clueLevel);
    }

    public static void handle(RitualStartedS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onStarted(msg));
    }
}
