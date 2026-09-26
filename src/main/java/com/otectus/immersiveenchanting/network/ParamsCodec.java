package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.ritual.RitualParams;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire form of {@link RitualParams}. Doubles travel as doubles so the client generates from bit-identical values.
 */
public final class ParamsCodec {
    private static final int MAX_MOTIFS = 64;

    public static void write(FriendlyByteBuf buf, RitualParams p) {
        buf.writeVarInt(p.generatorVersion());
        buf.writeVarInt(p.tier());
        buf.writeDouble(p.complexity());
        buf.writeVarInt(p.anchors());
        buf.writeVarInt(p.targetEvents());
        buf.writeDouble(p.bpm());
        buf.writeVarInt(p.perfectMs());
        buf.writeVarInt(p.goodMs());
        buf.writeVarInt(p.grazeMs());
        buf.writeBoolean(p.allowHolds());
        buf.writeBoolean(p.allowChords());
        buf.writeVarInt(p.maxChordSize());
        buf.writeVarInt(p.maxSimultaneousHolds());
        buf.writeVarInt(p.maxEvents());
        buf.writeVarInt(p.maxDurationMs());
        buf.writeVarInt(p.countInBeats());
        buf.writeDouble(p.holdBias());
        buf.writeDouble(p.chordBias());
        buf.writeVarInt(p.motifs().size());
        for (RitualParams.MotifWeight m : p.motifs()) {
            buf.writeUtf(m.id(), 128);
            buf.writeDouble(m.weight());
            buf.writeVarInt(m.minTier() + 1);
        }
    }

    public static RitualParams read(FriendlyByteBuf buf) {
        int version = buf.readVarInt();
        int tier = buf.readVarInt();
        double complexity = buf.readDouble();
        int anchors = buf.readVarInt();
        int target = buf.readVarInt();
        double bpm = buf.readDouble();
        int perfect = buf.readVarInt();
        int good = buf.readVarInt();
        int graze = buf.readVarInt();
        boolean holds = buf.readBoolean();
        boolean chords = buf.readBoolean();
        int chordSize = buf.readVarInt();
        int simultaneousHolds = buf.readVarInt();
        int maxEvents = buf.readVarInt();
        int maxDuration = buf.readVarInt();
        int countIn = buf.readVarInt();
        double holdBias = buf.readDouble();
        double chordBias = buf.readDouble();
        int motifCount = buf.readVarInt();
        if (motifCount < 0 || motifCount > MAX_MOTIFS) throw new DecoderException("too many motifs: " + motifCount);
        List<RitualParams.MotifWeight> motifs = new ArrayList<>(motifCount);
        for (int i = 0; i < motifCount; i++) {
            motifs.add(new RitualParams.MotifWeight(buf.readUtf(128), buf.readDouble(), buf.readVarInt() - 1));
        }
        return new RitualParams(version, tier, complexity, anchors, target, bpm, perfect, good, graze, holds, chords,
                chordSize, simultaneousHolds, maxEvents, maxDuration, countIn, holdBias, chordBias, motifs);
    }

    private ParamsCodec() {}
}
