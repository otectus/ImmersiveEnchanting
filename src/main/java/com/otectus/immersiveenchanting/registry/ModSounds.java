package com.otectus.immersiveenchanting.registry;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ritual sounds. Every cue is also shown visually; nothing requires hearing. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ImmersiveEnchanting.MOD_ID);

    public static final RegistryObject<SoundEvent> RITUAL_BEGIN = register("ritual.begin");
    public static final RegistryObject<SoundEvent> COUNT_TICK = register("ritual.count_tick");
    public static final RegistryObject<SoundEvent> RUNE_PERFECT = register("rune.perfect");
    public static final RegistryObject<SoundEvent> RUNE_GOOD = register("rune.good");
    public static final RegistryObject<SoundEvent> RUNE_GRAZE = register("rune.graze");
    public static final RegistryObject<SoundEvent> RUNE_MISS = register("rune.miss");
    public static final RegistryObject<SoundEvent> HOLD_PULSE = register("rune.hold_pulse");
    public static final RegistryObject<SoundEvent> RITUAL_SUCCESS = register("ritual.success");
    public static final RegistryObject<SoundEvent> RITUAL_PERFECT = register("ritual.perfect");
    public static final RegistryObject<SoundEvent> RITUAL_FRAYED = register("ritual.frayed");
    public static final RegistryObject<SoundEvent> RITUAL_FAILURE = register("ritual.failure");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ImmersiveEnchanting.id(name)));
    }

    private ModSounds() {}
}
