package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.client.ClientPacketHandler;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The authoritative result. Revealing the plan is fine now: the client shows what bound, what weakened and what was
 * lost, and what the attempt cost.
 */
public record RitualResolvedS2C(UUID sessionId, OutcomeBand band, double score, double accuracy, int perfect, int good,
                                int graze, int miss, int longestCombo, boolean abandoned, List<Entry> planned,
                                List<Entry> bound, int xpLevels, int xpPoints, int lapis, boolean seedAdvanced) {
    private static final int MAX_ENTRIES = 128;

    /** An enchantment by registry id and level; {@code planIndex} links a bound entry to its planned one. */
    public record Entry(ResourceLocation id, int level, int planIndex) {}

    public static void encode(RitualResolvedS2C msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.sessionId);
        buf.writeEnum(msg.band);
        buf.writeDouble(msg.score);
        buf.writeDouble(msg.accuracy);
        buf.writeVarInt(msg.perfect);
        buf.writeVarInt(msg.good);
        buf.writeVarInt(msg.graze);
        buf.writeVarInt(msg.miss);
        buf.writeVarInt(msg.longestCombo);
        buf.writeBoolean(msg.abandoned);
        writeEntries(buf, msg.planned);
        writeEntries(buf, msg.bound);
        buf.writeVarInt(msg.xpLevels);
        buf.writeVarInt(msg.xpPoints);
        buf.writeVarInt(msg.lapis);
        buf.writeBoolean(msg.seedAdvanced);
    }

    public static RitualResolvedS2C decode(FriendlyByteBuf buf) {
        return new RitualResolvedS2C(buf.readUUID(), buf.readEnum(OutcomeBand.class), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                readEntries(buf), readEntries(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    private static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeResourceLocation(e.id);
            buf.writeVarInt(e.level);
            buf.writeVarInt(e.planIndex);
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) throw new DecoderException("too many enchantments: " + n);
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new Entry(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt()));
        return out;
    }

    public static void handle(RitualResolvedS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onResolved(msg));
    }
}
