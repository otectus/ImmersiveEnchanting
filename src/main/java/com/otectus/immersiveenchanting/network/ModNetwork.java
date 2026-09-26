package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;

/**
 * Small, purpose-specific packets. Clients report which offer they clicked and their key inputs; the server owns the
 * plan, the score and the result. No packet from a client carries a score or an item.
 */
public final class ModNetwork {
    public static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveEnchanting.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Test hook: receives every server-to-client message sent to a fake player (automated tests only). */
    public static volatile BiConsumer<ServerPlayer, Object> fakePlayerSink;

    public static void register() {
        int id = 0;
        // Client -> server
        CHANNEL.messageBuilder(StartRitualC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartRitualC2S::encode).decoder(StartRitualC2S::decode).consumerMainThread(StartRitualC2S::handle).add();
        CHANNEL.messageBuilder(RitualInputBatchC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RitualInputBatchC2S::encode).decoder(RitualInputBatchC2S::decode).consumerMainThread(RitualInputBatchC2S::handle).add();
        CHANNEL.messageBuilder(RitualCancelC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RitualCancelC2S::encode).decoder(RitualCancelC2S::decode).consumerMainThread(RitualCancelC2S::handle).add();
        // Server -> client
        CHANNEL.messageBuilder(RitualStartedS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RitualStartedS2C::encode).decoder(RitualStartedS2C::decode).consumerMainThread(RitualStartedS2C::handle).add();
        CHANNEL.messageBuilder(RitualResolvedS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RitualResolvedS2C::encode).decoder(RitualResolvedS2C::decode).consumerMainThread(RitualResolvedS2C::handle).add();
        CHANNEL.messageBuilder(RitualAbortedS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RitualAbortedS2C::encode).decoder(RitualAbortedS2C::decode).consumerMainThread(RitualAbortedS2C::handle).add();
        CHANNEL.messageBuilder(OfferDifficultyS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OfferDifficultyS2C::encode).decoder(OfferDifficultyS2C::decode).consumerMainThread(OfferDifficultyS2C::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        if (player instanceof FakePlayer) {
            BiConsumer<ServerPlayer, Object> sink = fakePlayerSink;
            if (sink != null) sink.accept(player, message);
            return;
        }
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private ModNetwork() {}
}
