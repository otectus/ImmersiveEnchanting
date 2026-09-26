package com.otectus.immersiveenchanting.network;

import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A chunk of the player's binding-key transitions, in milliseconds from the start of their ritual timeline.
 * Sequence numbers start at 0 and increase by one; {@code complete} marks the last chunk. An oversized chunk decodes
 * as {@code malformed} (without allocating it) and aborts the ritual.
 */
public record RitualInputBatchC2S(UUID sessionId, int sequence, boolean complete, List<InputRecord> inputs, boolean malformed) {
    public static final int MAX_INPUTS_PER_PACKET = 256;

    public RitualInputBatchC2S(UUID sessionId, int sequence, boolean complete, List<InputRecord> inputs) {
        this(sessionId, sequence, complete, inputs, false);
    }

    public static void encode(RitualInputBatchC2S msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.sessionId);
        buf.writeVarInt(msg.sequence);
        buf.writeBoolean(msg.complete);
        buf.writeVarInt(msg.inputs.size());
        for (InputRecord input : msg.inputs) {
            buf.writeVarInt(input.timeMs());
            buf.writeByte(input.lane() | (input.pressed() ? 0x80 : 0));
        }
    }

    public static RitualInputBatchC2S decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int sequence = buf.readVarInt();
        boolean complete = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_INPUTS_PER_PACKET) {
            buf.skipBytes(buf.readableBytes());
            return new RitualInputBatchC2S(id, sequence, complete, List.of(), true);
        }
        List<InputRecord> inputs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int time = buf.readVarInt();
            int packed = buf.readUnsignedByte();
            inputs.add(new InputRecord(time, packed & 0x7F, (packed & 0x80) != 0));
        }
        return new RitualInputBatchC2S(id, sequence, complete, inputs, false);
    }

    public static void handle(RitualInputBatchC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) RitualSessionManager.receiveInputs(player, msg);
    }
}
