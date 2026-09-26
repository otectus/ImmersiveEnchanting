package com.otectus.immersiveenchanting.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Optional;

/**
 * Connects the ritual to one enchanting system. The adapter decides <i>what magic was rolled</i> (by asking the
 * system itself, never by reimplementing its rules) and <i>how the result is charged and applied</i>; Immersive
 * Enchanting decides how well the player bound it.
 *
 * <p>Adapters are registered on both logical sides (see {@link ImmersiveEnchantingApi#registerAdapter}).
 * {@link #supportsMenu} runs on the client too, to decide whether an enchant click should start a ritual; every
 * other method runs on the server thread only.
 *
 * <p>The first adapter, by descending {@link #priority()}, whose {@link #supports} accepts a menu owns it. Select by
 * the menu's actual type and behaviour, not merely by whether a mod is installed.
 */
public interface EnchantingContextAdapter {

    ResourceLocation id();

    int priority();

    /** Cheap, side-agnostic check of the menu's type. Must not touch server-only state. */
    boolean supportsMenu(AbstractContainerMenu menu);

    /** Server-side ownership check; may inspect player and menu state. */
    default boolean supports(ServerPlayer player, AbstractContainerMenu menu) {
        return supportsMenu(menu);
    }

    /** Whether a menu button id is one of the enchant offers. Other buttons (rerolls, pages) are never intercepted. */
    default boolean isOfferButton(AbstractContainerMenu menu, int buttonId) {
        return buttonId >= 0 && buttonId < 3;
    }

    /**
     * Captures the exact enchantments the system would apply for an offer, and its true cost. Must not mutate the
     * item, the player or the menu's offers. Does not check whether the player can afford the offer.
     *
     * @return empty if the offer does not exist
     */
    Optional<OfferSnapshot> snapshot(ServerPlayer player, AbstractContainerMenu menu, int offerIndex);

    /** Whether the player may begin this offer now (the system's own affordability and state rules). */
    Optional<AbortReason> checkStart(ServerPlayer player, AbstractContainerMenu menu, OfferSnapshot snapshot);

    /** Whether the menu still holds the item and offer the snapshot captured. Checked throughout the ritual. */
    boolean stillMatches(ServerPlayer player, AbstractContainerMenu menu, OfferSnapshot snapshot);

    /**
     * Charges and applies a resolved outcome. Validate every precondition before mutating anything; if a check
     * fails, mutate nothing and return {@link ApplyResult#rejected}. {@code resolved} is authoritative and must not
     * be re-validated against vanilla compatibility rules. Charge {@link RitualOutcome#charge()}.
     *
     * @param closing the menu is being closed; skip client sync of the menu
     */
    ApplyResult apply(ServerPlayer player, AbstractContainerMenu menu, RitualSessionView session,
                      List<EnchantmentInstance> resolved, RitualOutcome outcome, boolean closing);

    /** Performs the system's normal, immediate enchant for a player who is exempt from rituals. */
    boolean passThrough(ServerPlayer player, AbstractContainerMenu menu, int offerIndex);

    /** Whether direct enchant-button packets for this menu should be rejected while a ritual is required. */
    default boolean guardsDirectEnchanting() {
        return true;
    }

    /** Where the enchanting block is, for world effects around it. */
    default Optional<BlockPos> position(ServerPlayer player, AbstractContainerMenu menu) {
        return Optional.empty();
    }

    /**
     * The highest level this system can roll for an enchantment. A system that raises level caps reports its own
     * cap, so rolled levels above vanilla's maximum still count toward ritual difficulty. Server thread only.
     */
    default int maxLevel(Enchantment enchantment) {
        return enchantment.getMaxLevel();
    }

    /** Whether this system treats an enchantment as treasure. Server thread only. */
    default boolean isTreasure(Enchantment enchantment) {
        return enchantment.isTreasureOnly();
    }

    /** The item being enchanted. */
    default ItemStack targetItem(AbstractContainerMenu menu) {
        return menu.slots.isEmpty() ? ItemStack.EMPTY : menu.getSlot(0).getItem();
    }

    /**
     * A cheap value that changes whenever the menu's offers change, so offer previews are only recomputed then.
     * Return 0 to disable previews for this menu.
     */
    default long offerStateKey(ServerPlayer player, AbstractContainerMenu menu) {
        return 0L;
    }
}
