package com.otectus.immersiveenchanting.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Enough of an item's state to notice it being swapped or changed during a ritual, without keeping its NBT.
 * Computed on the server only; a client never supplies one.
 */
public record ItemFingerprint(ResourceLocation itemId, int count, int damage, int tagHash) {
    public static final ItemFingerprint EMPTY = new ItemFingerprint(new ResourceLocation("minecraft", "air"), 0, 0, 0);

    public static ItemFingerprint of(ItemStack stack) {
        if (stack.isEmpty()) return EMPTY;
        CompoundTag tag = stack.getTag();
        return new ItemFingerprint(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), stack.getDamageValue(),
                tag == null ? 0 : tag.hashCode());
    }

    public boolean matches(ItemStack stack) {
        return equals(of(stack));
    }

    public long hash() {
        long h = itemId.toString().hashCode();
        h = h * 31 + count;
        h = h * 31 + damage;
        return h * 31 + tagHash;
    }
}
