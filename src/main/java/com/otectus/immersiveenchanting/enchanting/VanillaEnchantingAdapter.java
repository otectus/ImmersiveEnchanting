package com.otectus.immersiveenchanting.enchanting;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.api.ApplyResult;
import com.otectus.immersiveenchanting.api.ChargePolicy;
import com.otectus.immersiveenchanting.api.CostSnapshot;
import com.otectus.immersiveenchanting.api.DifficultyScale;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.ItemFingerprint;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.api.RitualOutcome;
import com.otectus.immersiveenchanting.api.RitualSessionView;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.mixin.EnchantmentMenuAccessor;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Optional;

/**
 * The vanilla/Forge enchanting table: exactly {@link EnchantmentMenu}, plus subclasses registered as
 * vanilla-compatible. Offers come from the table's own private {@code getEnchantmentList}; application mirrors
 * {@code EnchantmentMenu#clickMenuButton} step for step, with the resolved list in place of the full one.
 */
public class VanillaEnchantingAdapter implements EnchantingContextAdapter {
    public static final ResourceLocation ID = ImmersiveEnchanting.id("vanilla");
    public static final VanillaEnchantingAdapter INSTANCE = new VanillaEnchantingAdapter();

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean supportsMenu(AbstractContainerMenu menu) {
        return menu != null && (menu.getClass() == EnchantmentMenu.class || AdapterRegistry.isVanillaCompatible(menu));
    }

    protected DifficultyScale scale() {
        return new DifficultyScale(ServerConfig.get(ServerConfig.VANILLA_REFERENCE_MAX_COST), 1.0);
    }

    protected static Container slots(EnchantmentMenu menu) {
        return ((EnchantmentMenuAccessor) menu).immersiveenchanting$getEnchantSlots();
    }

    @Override
    public Optional<OfferSnapshot> snapshot(ServerPlayer player, AbstractContainerMenu menu, int offerIndex) {
        if (!(menu instanceof EnchantmentMenu m) || offerIndex < 0 || offerIndex >= m.costs.length) return Optional.empty();
        int cost = m.costs[offerIndex];
        ItemStack item = slots(m).getItem(0);
        if (cost <= 0 || item.isEmpty()) return Optional.empty();
        List<EnchantmentInstance> list = ((EnchantmentMenuAccessor) m).immersiveenchanting$getEnchantmentList(item, offerIndex, cost);
        Enchantment clue = Enchantment.byId(m.enchantClue[offerIndex]);
        ResourceLocation clueId = clue == null ? null : BuiltInRegistries.ENCHANTMENT.getKey(clue);
        int price = offerIndex + 1;
        return Optional.of(new OfferSnapshot(m.containerId, offerIndex, cost, new CostSnapshot(price, 0, price, cost), clueId,
                m.levelClue[offerIndex], list == null ? List.of() : list, ItemFingerprint.of(item), m.getEnchantmentSeed(),
                scale(), item.is(Items.BOOK), new CompoundTag()));
    }

    @Override
    public Optional<AbortReason> checkStart(ServerPlayer player, AbstractContainerMenu menu, OfferSnapshot snapshot) {
        if (!(menu instanceof EnchantmentMenu m)) return Optional.of(AbortReason.NOT_SUPPORTED);
        if (snapshot.plannedEnchantments().isEmpty()) return Optional.of(AbortReason.NO_ENCHANTMENTS);
        return canAfford(player, m, snapshot) ? Optional.empty() : Optional.of(AbortReason.CANNOT_AFFORD);
    }

    /** The preconditions of {@code EnchantmentMenu#clickMenuButton}. */
    protected boolean canAfford(ServerPlayer player, EnchantmentMenu m, OfferSnapshot snapshot) {
        int price = snapshot.cost().xpLevels();
        int requirement = m.costs[snapshot.offerIndex()];
        ItemStack item = slots(m).getItem(0);
        ItemStack lapis = slots(m).getItem(1);
        boolean creative = player.getAbilities().instabuild;
        if ((lapis.isEmpty() || lapis.getCount() < snapshot.cost().lapis()) && !creative) return false;
        return requirement > 0 && !item.isEmpty()
                && (player.experienceLevel >= price && player.experienceLevel >= requirement || creative);
    }

    @Override
    public boolean stillMatches(ServerPlayer player, AbstractContainerMenu menu, OfferSnapshot snapshot) {
        if (!(menu instanceof EnchantmentMenu m) || player.containerMenu != menu || menu.containerId != snapshot.containerId()) return false;
        if (!menu.stillValid(player)) return false;
        return m.costs[snapshot.offerIndex()] == snapshot.displayedRequirement()
                && m.getEnchantmentSeed() == snapshot.playerEnchantSeed()
                && snapshot.itemFingerprint().matches(slots(m).getItem(0));
    }

    @Override
    public ApplyResult apply(ServerPlayer player, AbstractContainerMenu menu, RitualSessionView session,
                             List<EnchantmentInstance> resolved, RitualOutcome outcome, boolean closing) {
        if (!(menu instanceof EnchantmentMenu m)) return ApplyResult.rejected(AbortReason.MENU_CHANGED);
        EnchantmentMenuAccessor accessor = (EnchantmentMenuAccessor) m;
        Container slots = accessor.immersiveenchanting$getEnchantSlots();
        OfferSnapshot snapshot = session.snapshot();
        int price = snapshot.cost().xpLevels();
        int fuel = snapshot.cost().lapis();
        ItemStack item = slots.getItem(0);
        ItemStack lapis = slots.getItem(1);
        boolean creative = player.getAbilities().instabuild;

        if (outcome.bindsAnything()) {
            // Validate everything before touching anything.
            if (!snapshot.itemFingerprint().matches(item)) return ApplyResult.rejected(AbortReason.ITEM_CHANGED);
            if (m.getEnchantmentSeed() != snapshot.playerEnchantSeed() || player.getEnchantmentSeed() != snapshot.playerEnchantSeed()) {
                return ApplyResult.rejected(AbortReason.OFFER_CHANGED);
            }
            if (!creative && (lapis.isEmpty() || lapis.getCount() < fuel)) return ApplyResult.rejected(AbortReason.CANNOT_AFFORD);
            if (!creative && (player.experienceLevel < price || player.experienceLevel < snapshot.displayedRequirement())) {
                return ApplyResult.rejected(AbortReason.CANNOT_AFFORD);
            }

            // Mutation, in the order of EnchantmentMenu#clickMenuButton.
            ItemStack result = item;
            player.onEnchantmentPerformed(item, price);
            boolean book = item.is(Items.BOOK);
            if (book) {
                result = new ItemStack(Items.ENCHANTED_BOOK);
                CompoundTag tag = item.getTag();
                if (tag != null) result.setTag(tag.copy());
                slots.setItem(0, result);
            }
            for (EnchantmentInstance instance : resolved) {
                if (book) EnchantedBookItem.addEnchantment(result, instance);
                else result.enchant(instance.enchantment, instance.level);
            }
            int lapisCharged = 0;
            if (!creative) {
                lapis.shrink(fuel);
                lapisCharged = fuel;
                if (lapis.isEmpty()) slots.setItem(1, ItemStack.EMPTY);
            }
            player.awardStat(Stats.ENCHANT_ITEM);
            CriteriaTriggers.ENCHANTED_ITEM.trigger(player, result, price);
            refresh(m, accessor, slots, player, closing);
            accessor.immersiveenchanting$getAccess().execute((level, pos) -> level.playSound(null, pos,
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F));
            return ApplyResult.applied(result, price, 0, lapisCharged, true);
        }

        // Nothing bound: charge the failure policy. The item is never touched, so it need not match.
        ChargePolicy charge = outcome.charge();
        int levelsBefore = player.experienceLevel;
        int xp = charge.chargeXp() ? price : 0;
        if (charge.advanceSeed()) player.onEnchantmentPerformed(item, xp);
        else if (xp > 0) player.giveExperienceLevels(-xp);
        int lapisCharged = 0;
        if (charge.chargeLapis() && !creative && !lapis.isEmpty()) {
            lapisCharged = Math.min(fuel, lapis.getCount());
            lapis.shrink(lapisCharged);
            if (lapis.isEmpty()) slots.setItem(1, ItemStack.EMPTY);
        }
        if (xp > 0 || lapisCharged > 0 || charge.advanceSeed()) refresh(m, accessor, slots, player, closing);
        return ApplyResult.applied(item, levelsBefore - player.experienceLevel, 0, lapisCharged, charge.advanceSeed());
    }

    /** The bookkeeping tail of {@code clickMenuButton}: new offers from the new seed, synced to the client. */
    private static void refresh(EnchantmentMenu m, EnchantmentMenuAccessor accessor, Container slots, ServerPlayer player, boolean closing) {
        slots.setChanged();
        accessor.immersiveenchanting$getEnchantmentSeed().set(player.getEnchantmentSeed());
        m.slotsChanged(slots);
        if (!closing) m.broadcastChanges();
    }

    @Override
    public boolean passThrough(ServerPlayer player, AbstractContainerMenu menu, int offerIndex) {
        boolean clicked = menu.clickMenuButton(player, offerIndex);
        if (clicked) menu.broadcastChanges();
        return clicked;
    }

    @Override
    public long offerStateKey(ServerPlayer player, AbstractContainerMenu menu) {
        if (!(menu instanceof EnchantmentMenu m)) return 0L;
        long h = 17;
        for (int k = 0; k < m.costs.length; k++) {
            h = h * 31 + m.costs[k];
            h = h * 31 + m.enchantClue[k];
            h = h * 31 + m.levelClue[k];
        }
        h = h * 31 + m.getEnchantmentSeed();
        h = h * 31 + ItemFingerprint.of(slots(m).getItem(0)).hash();
        return h == 0 ? 1 : h;
    }

    @Override
    public Optional<BlockPos> position(ServerPlayer player, AbstractContainerMenu menu) {
        if (!(menu instanceof EnchantmentMenu m)) return Optional.empty();
        return ((EnchantmentMenuAccessor) m).immersiveenchanting$getAccess().evaluate((level, pos) -> pos);
    }
}
