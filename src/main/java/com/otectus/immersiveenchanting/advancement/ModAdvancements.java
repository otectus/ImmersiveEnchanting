package com.otectus.immersiveenchanting.advancement;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ritual milestones use advancements with a single {@code minecraft:impossible} criterion, awarded from code (since
 * 1.1.0). The root is earned with the first binding. Nothing in the mod is locked behind them; progression mods and
 * title systems read them.
 */
public final class ModAdvancements {
    public static final String ROOT = "root";
    public static final String FIRST_BINDING = "first_binding";
    public static final String PERFECT_BINDING = "perfect_binding";

    public static void award(ServerPlayer player, String name) {
        if (player.getServer() == null) return;
        if (!ROOT.equals(name)) award(player, ROOT);
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(ImmersiveEnchanting.id(name));
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private ModAdvancements() {}
}
