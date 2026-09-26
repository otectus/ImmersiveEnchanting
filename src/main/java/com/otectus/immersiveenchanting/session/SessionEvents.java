package com.otectus.immersiveenchanting.session;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.command.RitualCommands;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.data.EnchantmentProfileManager;
import com.otectus.immersiveenchanting.data.PatternSetManager;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.enchanting.OfferPlanner;
import com.otectus.immersiveenchanting.network.ModNetwork;
import com.otectus.immersiveenchanting.network.OfferDifficultyS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Forge-bus wiring for sessions, datapack data, commands and offer previews. */
@Mod.EventBusSubscriber(modid = ImmersiveEnchanting.MOD_ID)
public final class SessionEvents {
    /** Offer-state key last previewed per player; previews are recomputed only when it changes. */
    private static final Map<UUID, Long> PREVIEWED = new HashMap<>();
    private static int dataGeneration;

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new PatternSetManager());
        event.addListener(new EnchantmentProfileManager());
        dataGeneration++;
        PREVIEWED.clear();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        RitualCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) RitualSessionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RitualSessionManager.onLogout(player);
            PREVIEWED.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RitualSessionManager.clear();
        PREVIEWED.clear();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) {
            if (!PREVIEWED.isEmpty()) PREVIEWED.remove(player.getUUID());
            return;
        }
        if (!ServerConfig.get(ServerConfig.ENABLED) || !ServerConfig.get(ServerConfig.PREVIEW_RITUAL_DIFFICULTY)) return;
        if (RitualSessionManager.get(player).isPresent()) return;
        Optional<EnchantingContextAdapter> owner = AdapterRegistry.find(player, menu);
        if (owner.isEmpty()) return;
        EnchantingContextAdapter adapter = owner.get();
        long key = adapter.offerStateKey(player, menu);
        if (key == 0) return;
        key = key * 31 + menu.containerId;
        key = key * 31 + dataGeneration;
        Long previous = PREVIEWED.put(player.getUUID(), key);
        if (previous != null && previous == key) return;
        byte[] tiers = new byte[3];
        for (int offer = 0; offer < 3; offer++) {
            tiers[offer] = -1;
            try {
                Optional<OfferSnapshot> snapshot = adapter.snapshot(player, menu, offer);
                if (snapshot.isPresent() && !snapshot.get().plannedEnchantments().isEmpty()) {
                    tiers[offer] = (byte) OfferPlanner.plan(player, adapter, snapshot.get(), 1.0, true).tier();
                }
            } catch (RuntimeException e) {
                ImmersiveEnchanting.debug("offer preview failed for offer {} on {}: {}", offer, adapter.id(), e.toString());
            }
        }
        ModNetwork.sendTo(player, new OfferDifficultyS2C(menu.containerId, tiers));
    }

    private SessionEvents() {}
}
