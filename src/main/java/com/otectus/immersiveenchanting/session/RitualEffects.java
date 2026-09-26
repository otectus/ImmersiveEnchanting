package com.otectus.immersiveenchanting.session;

import com.otectus.immersiveenchanting.registry.ModSounds;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * What bystanders see and hear at the table when a ritual resolves. The enchanter hears the result through the
 * ritual overlay instead, so world sounds exclude them.
 */
final class RitualEffects {

    static void play(ServerPlayer player, RitualSession session, OutcomeBand band, boolean abandoned) {
        BlockPos pos = session.adapter().position(player, session.menu()).orElse(null);
        if (pos == null) return;
        ServerLevel level = player.serverLevel();
        double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
        SoundEvent sound = switch (band) {
            case PERFECT -> ModSounds.RITUAL_PERFECT.get();
            case STABLE -> ModSounds.RITUAL_SUCCESS.get();
            case FRAYED, WEAK -> ModSounds.RITUAL_FRAYED.get();
            case FAILED, SHATTERED -> ModSounds.RITUAL_FAILURE.get();
        };
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
        switch (band) {
            case PERFECT -> {
                level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.3, z, 60, 0.6, 0.5, 0.6, 0.9);
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 14, 0.4, 0.4, 0.4, 0.04);
            }
            case STABLE -> level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.3, z, 40, 0.5, 0.4, 0.5, 0.7);
            case FRAYED, WEAK -> {
                level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.3, z, 20, 0.5, 0.4, 0.5, 0.5);
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.25, 0.15, 0.25, 0.01);
            }
            case FAILED, SHATTERED -> {
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, abandoned ? 8 : 18, 0.3, 0.2, 0.3, 0.02);
                if (band == OutcomeBand.SHATTERED) level.sendParticles(ParticleTypes.CRIT, x, y, z, 16, 0.3, 0.3, 0.3, 0.25);
            }
        }
    }

    private RitualEffects() {}
}
