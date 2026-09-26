package com.otectus.immersiveenchanting.compat.easymagic;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.enchanting.VanillaEnchantingAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * Easy Magic 8.x ({@code fuzs.easymagic.world.inventory.ModEnchantmentMenu}). Its menu subclasses the vanilla one:
 * it swaps the vanilla item container for the table's persistent inventory (through the same field this mod reads),
 * builds offers with the vanilla private {@code getEnchantmentList}, and delegates offer buttons 0-2 to the vanilla
 * {@code clickMenuButton}. So capture and application are exactly the vanilla adapter's, and:
 * <ul>
 *   <li>the reroll button (id 4) is not an offer and is never intercepted or guarded, and a ritual never rerolls;</li>
 *   <li>items and lapis stay in the table's own inventory; a ritual closed mid-way is settled against it by the
 *       session tick, because this menu's {@code removed} does not call the vanilla one;</li>
 *   <li>offer previews follow Easy Magic's own offer updates through the vanilla offer arrays.</li>
 * </ul>
 * Needs no compile-time dependency: the menu is recognised by class name.
 */
public final class EasyMagicAdapter extends VanillaEnchantingAdapter {
    public static final ResourceLocation ID = ImmersiveEnchanting.id("easymagic");
    static final String MENU_CLASS = "fuzs.easymagic.world.inventory.ModEnchantmentMenu";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supportsMenu(AbstractContainerMenu menu) {
        return menu instanceof EnchantmentMenu && menu.getClass().getName().equals(MENU_CLASS);
    }
}
