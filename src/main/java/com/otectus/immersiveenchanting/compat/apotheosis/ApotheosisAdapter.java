package com.otectus.immersiveenchanting.compat.apotheosis;

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
import dev.shadowsoffire.apotheosis.advancements.EnchantedTrigger;
import dev.shadowsoffire.apotheosis.ench.Ench;
import dev.shadowsoffire.apotheosis.ench.asm.EnchHooks;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apotheosis.ench.table.EnchantingRecipe;
import dev.shadowsoffire.apotheosis.ench.table.IEnchantableItem;
import dev.shadowsoffire.apotheosis.util.ApothMiscUtil;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Apotheosis 7.x enchanting table ({@code ApothEnchantmentMenu}).
 *
 * <p>Apotheosis owns selection (Eterna, Quanta, Arcana, Rectification, clues, blacklists, treasure shelves), so the
 * plan comes from its own private {@code getEnchantmentList}, not vanilla's. Application mirrors its
 * {@code clickMenuButton}: the experience cost is in points ({@code ApothMiscUtil#getExpCostForSlot}), the seed is
 * advanced without charging levels, items are enchanted through {@code IEnchantableItem#onEnchantment}, lapis comes
 * from the table's own inventory, and its extended advancement trigger receives the table stats.
 *
 * <p>Infusion (slot 3 when an enchanting recipe matches) transforms the item instead of enchanting it. It needs a
 * full binding (Stable or Perfect); any lesser result is charged as a failure and leaves the item as it was.
 *
 * <p>Difficulty uses {@code apotheosisReferenceMaxCost} (default 50) instead of vanilla's 30, so Apotheosis's high
 * levels spread across the tiers instead of all landing in the hardest one. Level caps and treasure status come from
 * Apotheosis's own configuration ({@code EnchHooks}), so a Sharpness IX roll ranks above Sharpness V.
 */
public final class ApotheosisAdapter implements EnchantingContextAdapter {
    public static final ResourceLocation ID = ImmersiveEnchanting.id("apotheosis");

    private final Method getEnchantmentList;
    private final Field stats;

    ApotheosisAdapter() throws ReflectiveOperationException {
        // Private in Apotheosis; named m_39471_ in production, getEnchantmentList in development.
        this.getEnchantmentList = ObfuscationReflectionHelper.findMethod(ApothEnchantmentMenu.class, "m_39471_",
                ItemStack.class, int.class, int.class);
        this.stats = ApothEnchantmentMenu.class.getDeclaredField("stats");
        this.stats.setAccessible(true);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        return 100;
    }

    /** Apotheosis's configured cap (Sharpness IX in some packs), which vanilla's {@code getMaxLevel} does not report. */
    @Override
    public int maxLevel(Enchantment enchantment) {
        return EnchHooks.getMaxLevel(enchantment);
    }

    @Override
    public boolean isTreasure(Enchantment enchantment) {
        return EnchHooks.isTreasureOnly(enchantment);
    }

    @Override
    public boolean supportsMenu(AbstractContainerMenu menu) {
        return menu != null && menu.getClass() == ApothEnchantmentMenu.class;
    }

    private static Container slots(ApothEnchantmentMenu menu) {
        return ((EnchantmentMenuAccessor) menu).immersiveenchanting$getEnchantSlots();
    }

    private ApothEnchantmentMenu.TableStats stats(ApothEnchantmentMenu menu) {
        try {
            return (ApothEnchantmentMenu.TableStats) stats.get(menu);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read Apotheosis table stats", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EnchantmentInstance> plan(ApothEnchantmentMenu menu, ItemStack item, int offer, int level) {
        try {
            return (List<EnchantmentInstance>) getEnchantmentList.invoke(menu, item, offer, level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the Apotheosis offer", e);
        }
    }

    @Override
    public Optional<OfferSnapshot> snapshot(ServerPlayer player, AbstractContainerMenu menu, int offerIndex) {
        if (!(menu instanceof ApothEnchantmentMenu m) || offerIndex < 0 || offerIndex > 2) return Optional.empty();
        int level = m.costs[offerIndex];
        ItemStack item = slots(m).getItem(0);
        if (level <= 0 || item.isEmpty()) return Optional.empty();
        List<EnchantmentInstance> list = plan(m, item, offerIndex, level);
        Enchantment clue = Enchantment.byId(m.enchantClue[offerIndex]);
        ResourceLocation clueId = clue == null ? null : BuiltInRegistries.ENCHANTMENT.getKey(clue);
        CompoundTag data = new CompoundTag();
        boolean infusion = list != null && !list.isEmpty() && list.get(0).enchantment == Ench.Enchantments.INFUSION.get();
        data.putBoolean("infusion", infusion);
        return Optional.of(new OfferSnapshot(m.containerId, offerIndex, level,
                new CostSnapshot(0, ApothMiscUtil.getExpCostForSlot(level, offerIndex), offerIndex + 1, level), clueId,
                m.levelClue[offerIndex], list == null ? List.of() : list, ItemFingerprint.of(item), m.getEnchantmentSeed(),
                new DifficultyScale(ServerConfig.get(ServerConfig.APOTHEOSIS_REFERENCE_MAX_COST), 1.0), item.is(Items.BOOK), data));
    }

    @Override
    public Optional<AbortReason> checkStart(ServerPlayer player, AbstractContainerMenu menu, OfferSnapshot snapshot) {
        if (!(menu instanceof ApothEnchantmentMenu m)) return Optional.of(AbortReason.NOT_SUPPORTED);
        if (snapshot.plannedEnchantments().isEmpty()) return Optional.of(AbortReason.NO_ENCHANTMENTS);
        return canAfford(player, m, snapshot) ? Optional.empty() : Optional.of(AbortReason.CANNOT_AFFORD);
    }

    /** The preconditions of {@code ApothEnchantmentMenu#clickMenuButton}. */
    private static boolean canAfford(ServerPlayer player, ApothEnchantmentMenu m, OfferSnapshot snapshot) {
        int slotCost = snapshot.offerIndex() + 1;
        ItemStack lapis = m.getSlot(1).getItem();
        boolean creative = player.getAbilities().instabuild;
        if ((lapis.isEmpty() || lapis.getCount() < slotCost) && !creative) return false;
        int level = m.costs[snapshot.offerIndex()];
        return level > 0 && !slots(m).getItem(0).isEmpty()
                && (player.experienceLevel >= slotCost && player.experienceLevel >= level || creative);
    }

    @Override
    public boolean stillMatches(ServerPlayer player, AbstractContainerMenu menu, OfferSnapshot snapshot) {
        if (!(menu instanceof ApothEnchantmentMenu m) || player.containerMenu != menu || menu.containerId != snapshot.containerId()) return false;
        if (!menu.stillValid(player)) return false;
        return m.costs[snapshot.offerIndex()] == snapshot.displayedRequirement()
                && m.getEnchantmentSeed() == snapshot.playerEnchantSeed()
                && snapshot.itemFingerprint().matches(slots(m).getItem(0));
    }

    @Override
    public ApplyResult apply(ServerPlayer player, AbstractContainerMenu menu, RitualSessionView session,
                             List<EnchantmentInstance> resolved, RitualOutcome outcome, boolean closing) {
        if (!(menu instanceof ApothEnchantmentMenu m)) return ApplyResult.rejected(AbortReason.MENU_CHANGED);
        EnchantmentMenuAccessor accessor = (EnchantmentMenuAccessor) m;
        Container slots = accessor.immersiveenchanting$getEnchantSlots();
        OfferSnapshot snapshot = session.snapshot();
        int offer = snapshot.offerIndex();
        int level = snapshot.displayedRequirement();
        int points = snapshot.cost().xpPoints();
        int fuel = snapshot.cost().lapis();
        ItemStack item = slots.getItem(0);
        Slot lapisSlot = m.getSlot(1);
        boolean creative = player.getAbilities().instabuild;
        boolean infusion = snapshot.adapterData().getBoolean("infusion");
        boolean binds = outcome.bindsAnything() && (!infusion || outcome.band().grantsFull());

        if (binds) {
            if (!snapshot.itemFingerprint().matches(item)) return ApplyResult.rejected(AbortReason.ITEM_CHANGED);
            if (m.getEnchantmentSeed() != snapshot.playerEnchantSeed() || player.getEnchantmentSeed() != snapshot.playerEnchantSeed()) {
                return ApplyResult.rejected(AbortReason.OFFER_CHANGED);
            }
            if (!canAfford(player, m, snapshot)) return ApplyResult.rejected(AbortReason.CANNOT_AFFORD);
            ApothEnchantmentMenu.TableStats table = stats(m);
            Level world = accessor.immersiveenchanting$getAccess().evaluate((w, p) -> w).orElse(null);
            EnchantingRecipe recipe = null;
            if (infusion) {
                if (world == null) return ApplyResult.rejected(AbortReason.MENU_CHANGED);
                recipe = EnchantingRecipe.findMatch(world, item, table.eterna(), table.quanta(), table.arcana());
                if (recipe == null) return ApplyResult.rejected(AbortReason.OFFER_CHANGED);
            }

            // Mutation, in the order of ApothEnchantmentMenu#clickMenuButton.
            EnchantmentUtils.chargeExperience(player, points);
            player.onEnchantmentPerformed(item, 0);
            ItemStack result = infusion
                    ? recipe.assemble(item, table.eterna(), table.quanta(), table.arcana())
                    : ((IEnchantableItem) item.getItem()).onEnchantment(item, resolved);
            slots.setItem(0, result);
            int lapisCharged = 0;
            if (!creative) lapisCharged = takeLapis(lapisSlot, fuel);
            player.awardStat(Stats.ENCHANT_ITEM);
            if (CriteriaTriggers.ENCHANTED_ITEM instanceof EnchantedTrigger trigger) {
                trigger.trigger(player, result, level, table.eterna(), table.quanta(), table.arcana(), table.rectification());
            } else {
                CriteriaTriggers.ENCHANTED_ITEM.trigger(player, result, level);
            }
            refresh(m, accessor, slots, player, closing);
            accessor.immersiveenchanting$getAccess().execute((w, pos) -> w.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, 1.0F, w.random.nextFloat() * 0.1F + 0.9F));
            return ApplyResult.applied(result, 0, points, lapisCharged, true);
        }

        // Nothing bound (or an infusion without a full binding): the failure policy, in Apotheosis's own currency.
        ChargePolicy charge = outcome.failureCharge();
        int pointsCharged = 0;
        if (charge.chargeXp() && points > 0) {
            // Report the amount charged, not a before/after difference: Placebo's getExperience weighs level progress
            // by a cumulative total, so the difference is unreliable once the player has partial progress.
            int amount = Math.min(points, EnchantmentUtils.getExperience(player));
            if (amount > 0 && EnchantmentUtils.chargeExperience(player, amount)) pointsCharged = amount;
        }
        if (charge.advanceSeed()) player.onEnchantmentPerformed(item, 0);
        int lapisCharged = charge.chargeLapis() && !creative ? takeLapis(lapisSlot, fuel) : 0;
        if (pointsCharged > 0 || lapisCharged > 0 || charge.advanceSeed()) refresh(m, accessor, slots, player, closing);
        ApplyResult result = ApplyResult.applied(item, 0, pointsCharged, lapisCharged, charge.advanceSeed());
        return outcome.bindsAnything() ? result.withBound(List.of()) : result;
    }

    private static int takeLapis(Slot slot, int amount) {
        ItemStack lapis = slot.getItem();
        if (lapis.isEmpty()) return 0;
        int taken = Math.min(amount, lapis.getCount());
        ItemStack left = lapis.copy();
        left.shrink(taken);
        slot.set(left.isEmpty() ? ItemStack.EMPTY : left);
        return taken;
    }

    private static void refresh(ApothEnchantmentMenu m, EnchantmentMenuAccessor accessor, Container slots, ServerPlayer player, boolean closing) {
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
        if (!(menu instanceof ApothEnchantmentMenu m)) return 0L;
        long h = 23;
        for (int k = 0; k < 3; k++) {
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
        if (!(menu instanceof ApothEnchantmentMenu m)) return Optional.empty();
        return ((EnchantmentMenuAccessor) m).immersiveenchanting$getAccess().evaluate((level, pos) -> pos);
    }
}
