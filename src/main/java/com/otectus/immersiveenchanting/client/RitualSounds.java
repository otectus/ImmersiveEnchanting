package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.registry.ModSounds;
import com.otectus.immersiveenchanting.ritual.Judgement;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Ritual UI sounds. Every sound mirrors something already shown on screen. */
public final class RitualSounds {
    private static final float[] LANE_PITCH = {0.89F, 1.0F, 1.12F, 1.26F};

    public static void play(SoundEvent sound, float pitch) {
        float volume = (float) ClientConfig.get(ClientConfig.RITUAL_SOUND_VOLUME);
        if (volume <= 0.001F) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void judgement(Judgement judgement, int lane) {
        float pitch = lane >= 0 && lane < LANE_PITCH.length ? LANE_PITCH[lane] : 1.0F;
        switch (judgement) {
            case PERFECT -> play(ModSounds.RUNE_PERFECT.get(), pitch);
            case GOOD -> play(ModSounds.RUNE_GOOD.get(), pitch);
            case GRAZE -> play(ModSounds.RUNE_GRAZE.get(), pitch);
            case MISS -> play(ModSounds.RUNE_MISS.get(), 1.0F);
        }
    }

    public static void holdStart(int lane) {
        play(ModSounds.HOLD_PULSE.get(), lane >= 0 && lane < LANE_PITCH.length ? LANE_PITCH[lane] : 1.0F);
    }

    public static void result(OutcomeBand band) {
        switch (band) {
            case PERFECT -> play(ModSounds.RITUAL_PERFECT.get(), 1.0F);
            case STABLE -> play(ModSounds.RITUAL_SUCCESS.get(), 1.0F);
            case FRAYED, WEAK -> play(ModSounds.RITUAL_FRAYED.get(), 1.0F);
            case FAILED, SHATTERED -> play(ModSounds.RITUAL_FAILURE.get(), 1.0F);
        }
    }

    private RitualSounds() {}
}
