package com.otectus.immersiveenchanting.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * The only way Immersive Enchanting reads the vanilla table's internals. {@code getEnchantmentList} is the exact
 * private method {@code clickMenuButton} uses to build the list it applies, so invoking it captures precisely what
 * the table would have enchanted, including Forge hooks and other mods' influence on selection. Nothing here
 * reimplements vanilla selection. A signature change fails loudly at startup (injectors require their targets).
 */
@Mixin(EnchantmentMenu.class)
public interface EnchantmentMenuAccessor {
    @Accessor("enchantSlots")
    Container immersiveenchanting$getEnchantSlots();

    @Accessor("access")
    ContainerLevelAccess immersiveenchanting$getAccess();

    @Accessor("enchantmentSeed")
    DataSlot immersiveenchanting$getEnchantmentSeed();

    @Invoker("getEnchantmentList")
    List<EnchantmentInstance> immersiveenchanting$getEnchantmentList(ItemStack stack, int offerIndex, int cost);
}
