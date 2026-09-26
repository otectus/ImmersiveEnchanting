package com.otectus.immersiveenchanting.test;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/**
 * A deliberately odd enchantment from a namespace Immersive Enchanting knows nothing about: very rare, levels 2-6, and
 * a minimum cost that falls as the level rises. It rolls at the table like any other, proving unknown modded
 * enchantments need no integration.
 */
@Mod.EventBusSubscriber(modid = ImmersiveEnchanting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TestContent {
    public static final ResourceLocation ODD_ID = new ResourceLocation("ie_test", "odd_binding");
    public static Enchantment odd;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENCHANTMENT, helper -> {
            odd = new OddEnchantment();
            helper.register(ODD_ID, odd);
        });
    }

    static final class OddEnchantment extends Enchantment {
        OddEnchantment() {
            super(Rarity.VERY_RARE, EnchantmentCategory.BREAKABLE, EquipmentSlot.values());
        }

        @Override
        public int getMinLevel() {
            return 2;
        }

        @Override
        public int getMaxLevel() {
            return 6;
        }

        @Override
        public int getMinCost(int level) {
            return 40 - level * 3;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 35;
        }
    }

    private TestContent() {}
}
