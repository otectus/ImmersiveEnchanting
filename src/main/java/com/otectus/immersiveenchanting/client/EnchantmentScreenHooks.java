package com.otectus.immersiveenchanting.client;

import com.mojang.datafixers.util.Either;
import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.client.render.RitualRenderer;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.enchanting.RitualPolicy;
import com.otectus.immersiveenchanting.ritual.DifficultyTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * The enchanting gate on the client. The enchanting screen is never replaced: for the vanilla screen, a press on an
 * offer row is caught by Forge's screen input event before the screen sends its enchant button packet, and the ritual
 * starts instead. Other screens over a supported menu are caught one step later, when they send the button packet
 * ({@code MultiPlayerGameModeMixin}). Unsupported menus and screens are never touched.
 *
 * <p>While a ritual runs, the screen stays open beneath the ritual overlay, and every key and mouse event goes to the
 * ritual instead, so the inventory cannot be touched and the screen cannot be closed by accident.
 */
@Mod.EventBusSubscriber(modid = ImmersiveEnchanting.MOD_ID, value = Dist.CLIENT)
public final class EnchantmentScreenHooks {
    private static final int ROW_X = 60, ROW_Y = 14, ROW_W = 108, ROW_H = 19;

    /** Whether clicking offer {@code button} of {@code menu} should start a ritual instead of enchanting. */
    static boolean shouldIntercept(LocalPlayer player, AbstractContainerMenu menu, int button) {
        if (player == null || menu == null || !ServerConfig.get(ServerConfig.ENABLED)) return false;
        Optional<EnchantingContextAdapter> owner = AdapterRegistry.findForMenu(menu);
        if (owner.isEmpty() || !owner.get().isOfferButton(menu, button)) return false;
        return RitualPolicy.requiredForPlayer(player, owner.get().targetItem(menu));
    }

    /** From the button-packet mixin: true to swallow the packet because a ritual starts (or runs) instead. */
    public static boolean interceptButtonClick(int containerId, int buttonId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu.containerId != containerId) return false;
        if (!shouldIntercept(player, player.containerMenu, buttonId)) return false;
        if (!ClientRitualController.isActive()) ClientRitualController.requestStart(containerId, buttonId);
        return true;
    }

    private static boolean ritualScreen(Screen screen) {
        return ClientRitualController.isActive() && !(screen instanceof RitualPracticeScreen);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ritualScreen(event.getScreen())) {
            ClientRitualController.mousePressed(event.getButton());
            event.setCanceled(true);
            return;
        }
        if (event.getScreen().getClass() != EnchantmentScreen.class) return;
        EnchantmentScreen screen = (EnchantmentScreen) event.getScreen();
        LocalPlayer player = Minecraft.getInstance().player;
        EnchantmentMenu menu = screen.getMenu();
        for (int offer = 0; offer < 3; offer++) {
            double dx = event.getMouseX() - (screen.getGuiLeft() + ROW_X);
            double dy = event.getMouseY() - (screen.getGuiTop() + ROW_Y + ROW_H * offer);
            if (dx < 0 || dy < 0 || dx >= ROW_W || dy >= ROW_H) continue;
            // The same client-side check the screen makes before sending its packet; the server re-validates.
            if (shouldIntercept(player, menu, offer) && menu.clickMenuButton(player, offer)) {
                event.setCanceled(true);
                ClientRitualController.requestStart(menu.containerId, offer);
            }
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!ritualScreen(event.getScreen())) return;
        ClientRitualController.mouseReleased(event.getButton());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (ritualScreen(event.getScreen())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (ritualScreen(event.getScreen())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!ritualScreen(event.getScreen())) return;
        ClientRitualController.keyPressed(event.getKeyCode(), event.getScanCode());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (!ritualScreen(event.getScreen())) return;
        ClientRitualController.keyReleased(event.getKeyCode(), event.getScanCode());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (ritualScreen(event.getScreen())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof RitualPracticeScreen) return;
        ClientRitualController.frame();
        int w = screen.width, h = screen.height;
        switch (ClientRitualController.phase()) {
            case REQUESTED -> RitualRenderer.renderPending(event.getGuiGraphics(), w, h);
            case PLAYING, AWAITING_RESULT -> {
                ClientRitual ritual = ClientRitualController.ritual();
                if (ritual != null) RitualRenderer.renderRitual(event.getGuiGraphics(), ritual, w, h,
                        ClientRitualController.phase() == ClientRitualController.Phase.AWAITING_RESULT);
            }
            case RESULT -> {
                ResultView result = ClientRitualController.result();
                if (result != null) RitualRenderer.renderResult(event.getGuiGraphics(), result, w, h);
            }
            case IDLE -> {
                if (screen.getClass() == EnchantmentScreen.class && ClientConfig.get(ClientConfig.SHOW_OFFER_COMPLEXITY)
                        && ServerConfig.get(ServerConfig.PREVIEW_RITUAL_DIFFICULTY)) {
                    EnchantmentScreen es = (EnchantmentScreen) screen;
                    byte[] tiers = OfferPreview.get(es.getMenu().containerId);
                    if (tiers != null) RitualRenderer.renderOfferGauge(event.getGuiGraphics(), es.getGuiLeft(), es.getGuiTop(), tiers);
                }
            }
        }
        Component message = ClientRitualController.message();
        if (message != null) RitualRenderer.renderToast(event.getGuiGraphics(), message, w, ClientRitualController.messageAlpha());
    }

    @SubscribeEvent
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && ritualScreen(screen)) event.setCanceled(true);
    }

    /** Adds the complexity line to the vanilla offer tooltip ("Ritual: Expert"), under the clue. */
    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof EnchantmentScreen screen) || screen.getClass() != EnchantmentScreen.class) return;
        if (ClientRitualController.isActive() || !ServerConfig.get(ServerConfig.PREVIEW_RITUAL_DIFFICULTY)) return;
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.isEmpty()) return;
        Optional<FormattedText> first = elements.get(0).left();
        if (first.isEmpty() || !(first.get() instanceof Component c) || !(c.getContents() instanceof TranslatableContents t)
                || !"container.enchant.clue".equals(t.getKey())) return;
        byte[] tiers = OfferPreview.get(screen.getMenu().containerId);
        if (tiers == null) return;
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        int offer = (int) Math.floor((mouseY - screen.getGuiTop() - ROW_Y) / ROW_H);
        if (offer < 0 || offer >= tiers.length || tiers[offer] < 0) return;
        DifficultyTier tier = DifficultyTier.byIndex(tiers[offer]);
        StringBuilder gauge = new StringBuilder();
        for (int i = 0; i < 6; i++) gauge.append(i <= tier.index() ? '◆' : '◇');
        elements.add(1, Either.left(Component.translatable("immersive_enchanting.preview.tooltip",
                Component.translatable("immersive_enchanting.tier." + tier.id()), gauge.toString()).withStyle(ChatFormatting.GOLD)));
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof RitualPracticeScreen) return;
        if (ClientRitualController.isActive()) ClientRitualController.onScreenClosed();
        OfferPreview.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRitualController.onLogout();
        OfferPreview.clear();
    }

    private EnchantmentScreenHooks() {}
}
