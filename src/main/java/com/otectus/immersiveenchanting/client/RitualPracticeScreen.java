package com.otectus.immersiveenchanting.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.client.render.RitualRenderer;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.registry.ModSounds;
import com.otectus.immersiveenchanting.ritual.DifficultyTier;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.ritual.OutcomeRules;
import com.otectus.immersiveenchanting.ritual.ParamsResolver;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.PatternSetDef;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.ScoreResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Practice rituals ({@code /ritualpractice [tier]}): the real overlay, patterns and scoring with the built-in pattern
 * set, played locally. Nothing is sent to the server and nothing is enchanted or charged.
 */
public final class RitualPracticeScreen extends Screen {
    private static boolean open;
    private final int tier;
    private ClientRitual ritual;
    private ResultView result;

    public RitualPracticeScreen(int tier) {
        super(Component.translatable("immersive_enchanting.practice.title"));
        this.tier = Math.max(0, Math.min(5, tier));
    }

    public static boolean isOpen() {
        return open;
    }

    @Override
    protected void init() {
        open = true;
        if (ritual == null && result == null) begin();
    }

    private void begin() {
        DifficultyTier t = DifficultyTier.byIndex(tier);
        double score = (t.lowScore() + t.highScore()) / 2.0;
        ParamsResolver.Tuning tuning = new ParamsResolver.Tuning(1.0, 1.0, 1.0,
                Math.min(1.25, ClientConfig.get(ClientConfig.TIMING_ASSIST)), 96, 25.0);
        RitualParams params = ParamsResolver.resolve(PatternSetDef.DEFAULT, score, tuning, ParamsResolver.Shaping.NONE);
        long seed = System.nanoTime();
        result = null;
        ritual = new ClientRitual(params, PatternGenerator.generate(params, seed), ImmersiveEnchanting.id("arcane"), null, -1, new ClientRitual.Host() {
            @Override
            public void sendInputs(int sequence, List<InputRecord> inputs, boolean complete) {
            }

            @Override
            public void cancel() {
                onClose();
            }

            @Override
            public void finished(ClientRitual finished, ScoreResult local) {
                result = ResultView.practice(OutcomeRules.DEFAULT.band(local.finalScore()), local);
                RitualSounds.result(result.band());
            }
        });
        RitualSounds.play(ModSounds.RITUAL_BEGIN.get(), 1.0F);
    }

    /** For automated checks: the ritual being practised, or null once it has finished. */
    public ClientRitual ritual() {
        return result == null ? ritual : null;
    }

    public ResultView result() {
        return result;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        if (result != null) {
            RitualRenderer.renderResult(g, result, width, height);
        } else if (ritual != null) {
            ritual.update();
            if (result == null) RitualRenderer.renderRitual(g, ritual, width, height, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (result != null || ritual == null || ritual.inCountIn() || ritual.escArmedUntilMs > ritual.nowMs()) onClose();
            else ritual.escArmedUntilMs = ritual.nowMs() + 3000;
            return true;
        }
        if (result != null) {
            if (result.ageMs() > 600) begin();
            return true;
        }
        if (ritual != null) ritual.press(RitualKeyMappings.laneForKey(keyCode, scanCode, ritual.pattern.anchors()));
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ritual != null && result == null) ritual.release(RitualKeyMappings.laneForKey(keyCode, scanCode, ritual.pattern.anchors()));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (result != null) {
            if (result.ageMs() > 600) begin();
            return true;
        }
        if (ritual != null) ritual.press(RitualKeyMappings.laneForMouse(button, ritual.pattern.anchors()));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ritual != null && result == null) ritual.release(RitualKeyMappings.laneForMouse(button, ritual.pattern.anchors()));
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        open = false;
    }

    /** Registers {@code /ritualpractice [tier]}. */
    @Mod.EventBusSubscriber(modid = ImmersiveEnchanting.MOD_ID, value = Dist.CLIENT)
    public static final class Command {
        @SubscribeEvent
        public static void register(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("ritualpractice")
                    .executes(c -> openLater(2))
                    .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5))
                            .executes(c -> openLater(IntegerArgumentType.getInteger(c, "tier")))));
        }

        private static int openLater(int tier) {
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.setScreen(new RitualPracticeScreen(tier)));
            return 1;
        }

        private Command() {}
    }
}
