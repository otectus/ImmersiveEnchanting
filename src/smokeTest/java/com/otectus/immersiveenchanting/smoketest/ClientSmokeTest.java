package com.otectus.immersiveenchanting.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.client.ClientRitual;
import com.otectus.immersiveenchanting.client.ClientRitualController;
import com.otectus.immersiveenchanting.client.OfferPreview;
import com.otectus.immersiveenchanting.client.ResultView;
import com.otectus.immersiveenchanting.client.RitualKeyMappings;
import com.otectus.immersiveenchanting.client.RitualPracticeScreen;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.ritual.Autoplay;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Drives a dev client through real enchanting: a fresh flat survival world, an enchanting table with 15 bookshelves,
 * the table opened through the block, offers clicked through Forge's screen input path, and rituals played with key
 * events dispatched the way the keyboard handler dispatches them (perfect, silent, sloppy on classic lanes, cancelled,
 * practice). Verifies server state after each ritual, saves screenshots to {@code run/screenshots/smoketest_*.png},
 * writes {@code smoketest-result.json} and quits.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    /** Which integration should own the table: empty for vanilla, or "apotheosis" / "easymagic" in compat runs. */
    private static final String EXPECT = System.getProperty("immersive_enchanting.smoketest.expect", "").trim();
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final Map<String, Object> server = new ConcurrentHashMap<>();
    private static final long START = Util.getMillis();

    private static final List<Step> steps = new ArrayList<>();
    private static int stepIndex;
    private static int stepTicks;
    private static int titleTicks;
    private static boolean worldRequested;
    private static boolean finished;
    private static Integer originalGuiScale;
    private static Boolean originalVsync;
    private static Integer originalFrameLimit;
    private static Boolean originalPauseOnLostFocus;
    private static BlockPos table;

    /** Inputs the autoplayer still has to deliver, relative to the target ritual's clock. */
    private static List<InputRecord> script = List.of();
    private static int scriptIndex;
    private static ClientRitual scripted;

    private record Step(String name, int timeoutTicks, Predicate<Minecraft> action) {}

    static {
        plan();
    }

    private static void step(String name, int timeoutTicks, Predicate<Minecraft> action) {
        steps.add(new Step(name, timeoutTicks, action));
    }

    /** One-shot action followed by a fixed wait. */
    private static void act(String name, int waitTicks, java.util.function.Consumer<Minecraft> action) {
        steps.add(new Step(name, waitTicks + 5, mc -> {
            if (stepTicks == 0) action.accept(mc);
            return stepTicks >= waitTicks;
        }));
    }

    private static void plan() {
        act("stage", 20, ClientSmokeTest::stageWorld);
        act("open table", 5, mc -> server(s -> {
            ServerPlayer p = player(s);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false);
            s.overworld().getBlockState(table).use(s.overworld(), p, InteractionHand.MAIN_HAND, hit);
        }));
        step("enchanting screen", 60, mc -> mc.screen instanceof EnchantmentScreen);

        // 1. A perfect ritual on the default radial layout.
        load(Items.DIAMOND_SWORD);
        step("offers and preview", 100, mc -> menu(mc) != null && menu(mc).costs[2] > 0 && OfferPreview.get(menu(mc).containerId) != null);
        act("hover offer 3", 12, mc -> hover(mc, 2));
        act("shot offers", 4, mc -> shot(mc, "smoketest_offers"));
        ritual("perfect", 2, true, 0.0, 0);
        step("result shown", 900, mc -> ClientRitualController.phase() == ClientRitualController.Phase.RESULT);
        act("shot perfect result", 14, mc -> shot(mc, "smoketest_result_perfect"));
        verify("perfect", true);
        act("dismiss", 16, mc -> key(mc, GLFW.GLFW_KEY_SPACE, true));

        // 2. A silent ritual: nothing pressed, so it shatters and charges the full price.
        load(Items.IRON_CHESTPLATE);
        step("offers 2", 100, mc -> ClientRitualController.phase() == ClientRitualController.Phase.IDLE && menu(mc).costs[1] > 0);
        ritual("silent", 1, false, 0.0, 0);
        step("result shown 2", 900, mc -> ClientRitualController.phase() == ClientRitualController.Phase.RESULT);
        act("shot failed result", 14, mc -> shot(mc, "smoketest_result_failed"));
        verify("silent", false);
        act("dismiss 2", 16, mc -> key(mc, GLFW.GLFW_KEY_SPACE, true));

        // 3. A sloppy ritual on classic lanes.
        act("classic lanes", 2, mc -> ClientConfig.RITUAL_LAYOUT.set(ClientConfig.RitualLayout.CLASSIC_LANES));
        load(Items.BOW);
        step("offers 3", 100, mc -> ClientRitualController.phase() == ClientRitualController.Phase.IDLE && menu(mc).costs[2] > 0);
        ritual("sloppy", 2, true, 0.3, 70);
        step("result shown 3", 900, mc -> ClientRitualController.phase() == ClientRitualController.Phase.RESULT);
        act("shot partial result", 14, mc -> shot(mc, "smoketest_result_sloppy"));
        verify("sloppy", false);
        act("dismiss 3", 16, mc -> {
            key(mc, GLFW.GLFW_KEY_SPACE, true);
            ClientConfig.RITUAL_LAYOUT.set(ClientConfig.RitualLayout.RADIAL);
        });

        // 4. Escape during the count-in: free.
        load(Items.GOLDEN_SWORD);
        step("offers 4", 100, mc -> ClientRitualController.phase() == ClientRitualController.Phase.IDLE && menu(mc).costs[0] > 0);
        act("remember costs", 2, mc -> remember("cancel"));
        act("click offer 1", 2, mc -> click(mc, 0));
        step("ritual starts 4", 100, mc -> ClientRitualController.phase() == ClientRitualController.Phase.PLAYING);
        act("escape", 30, mc -> key(mc, GLFW.GLFW_KEY_ESCAPE, true));
        act("verify cancel", 6, mc -> {
            snapshotServer("cancel_after");
            checks.put("cancel_back_to_idle", ClientRitualController.phase() == ClientRitualController.Phase.IDLE);
            checks.put("cancel_screen_still_open", mc.screen instanceof EnchantmentScreen);
        });
        act("verify cancel costs", 4, mc -> {
            checks.put("cancel_is_free", server.get("cancel_before_xp").equals(server.get("cancel_after_xp"))
                    && server.get("cancel_before_lapis").equals(server.get("cancel_after_lapis")));
            checks.put("cancel_item_untouched", ((Map<?, ?>) server.get("cancel_after_item")).isEmpty());
        });

        // 5. Practice at the hardest tier: holds and chords on screen.
        act("close table", 10, mc -> mc.player.closeContainer());
        act("practice", 4, mc -> {
            originalGuiScale = mc.options.guiScale().get();
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            mc.setScreen(new RitualPracticeScreen(5));
        });
        act("practice autoplay", 2, mc -> {
            if (mc.screen instanceof RitualPracticeScreen p && p.ritual() != null) {
                autoplay(p.ritual(), Autoplay.perfect(p.ritual().pattern));
                checks.put("practice_tier5_holds", p.ritual().pattern.holdCount());
                checks.put("practice_tier5_chords", p.ritual().pattern.chordCount());
            }
        });
        step("practice mid", 600, mc -> mc.screen instanceof RitualPracticeScreen p && p.ritual() != null && p.ritual().nowMs() > p.ritual().pattern.endMs() * 0.42);
        act("shot practice", 4, mc -> {
            checks.put("practice_gui_scale_3", mc.getWindow().getGuiScale() == 3.0);
            shot(mc, "smoketest_practice");
        });
        step("practice done", 900, mc -> mc.screen instanceof RitualPracticeScreen p && p.result() != null);
        act("shot practice result", 14, mc -> {
            shot(mc, "smoketest_practice_result");
            if (mc.screen instanceof RitualPracticeScreen p) checks.put("practice_band", p.result().band().id());
        });
        act("leave practice", 6, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, true);
            mc.options.guiScale().set(originalGuiScale);
            mc.resizeDisplay();
        });
        step("practice closed", 40, mc -> mc.screen == null);

        // 6. Accessibility presentation: high-contrast runes, the standard palette, reduced motion, classic lanes.
        act("accessible practice", 4, mc -> {
            ClientConfig.HIGH_CONTRAST_RUNES.set(true);
            ClientConfig.REDUCED_MOTION.set(true);
            mc.setScreen(new RitualPracticeScreen(3));
        });
        act("accessible autoplay", 2, mc -> {
            if (mc.screen instanceof RitualPracticeScreen p && p.ritual() != null) autoplay(p.ritual(), Autoplay.play(p.ritual().pattern, 0.15, 60, 5L));
        });
        step("accessible mid", 600, mc -> mc.screen instanceof RitualPracticeScreen p && p.ritual() != null && p.ritual().nowMs() > p.ritual().pattern.endMs() * 0.5);
        act("shot accessible", 4, mc -> shot(mc, "smoketest_high_contrast"));
        act("standard palette", 2, mc -> {
            ClientConfig.HIGH_CONTRAST_RUNES.set(false);
            ClientConfig.COLORBLIND_SAFE_MODE.set(false);
            ClientConfig.RITUAL_LAYOUT.set(ClientConfig.RitualLayout.CLASSIC_LANES);
        });
        act("shot standard palette", 10, mc -> shot(mc, "smoketest_standard_palette_lanes"));
        act("restore presentation", 2, mc -> {
            ClientConfig.REDUCED_MOTION.set(false);
            ClientConfig.COLORBLIND_SAFE_MODE.set(true);
            ClientConfig.RITUAL_LAYOUT.set(ClientConfig.RitualLayout.RADIAL);
            key(mc, GLFW.GLFW_KEY_ESCAPE, true);
            key(mc, GLFW.GLFW_KEY_ESCAPE, true);
        });
        step("accessible closed", 40, mc -> mc.screen == null);
    }

    /** Puts {@code item} and a full stack of lapis on the open table, server side. */
    private static void load(Item item) {
        act("load " + item, 10, mc -> server(s -> {
            ServerPlayer p = player(s);
            if (p.containerMenu instanceof EnchantmentMenu menu) {
                menu.getSlot(0).set(new ItemStack(item));
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
                p.setExperienceLevels(60);
            }
        }));
    }

    private static void ritual(String label, int offer, boolean play, double missChance, int errorMs) {
        act("remember " + label, 2, mc -> remember(label));
        act("click " + label, 2, mc -> {
            click(mc, offer);
            checks.put(label + "_intercepted", ClientRitualController.phase() == ClientRitualController.Phase.REQUESTED);
        });
        step("playing " + label, 100, mc -> ClientRitualController.phase() == ClientRitualController.Phase.PLAYING);
        act("capture plan " + label, 2, mc -> {
            ClientRitual ritual = ClientRitualController.ritual();
            ritual.releaseOnFocusLoss = false;
            server(s -> RitualSessionManager.get(player(s)).ifPresent(session -> {
                server.put(label + "_plan", map(session.snapshot().plannedEnchantments()));
                server.put(label + "_adapter", session.adapterId().toString());
                server.put(label + "_price", session.snapshot().cost().lapis());
            }));
            checks.put(label + "_item_unchanged_while_playing", menu(mc).getSlot(0).getItem().getEnchantmentTags().isEmpty());
            if (play) autoplay(ritual, missChance > 0 ? Autoplay.play(ritual.pattern, missChance, errorMs, 99L) : Autoplay.perfect(ritual.pattern));
            checks.put(label + "_tier", ritual.params.tier());
            checks.put(label + "_runes", ritual.pattern.events().size());
        });
        if (label.equals("perfect")) {
            step("count-in", 200, mc -> ritualAt(0.0) && ClientRitualController.ritual().nowMs() > ClientRitualController.ritual().pattern.countInMs() * 0.55);
            act("shot count-in", 2, mc -> shot(mc, "smoketest_countin"));
        }
        step("mid " + label, 600, mc -> ritualAt(0.45));
        act("shot mid " + label, 2, mc -> shot(mc, "smoketest_" + (label.equals("sloppy") ? "lanes" : label.equals("silent") ? "silent" : "radial")));
    }

    private static boolean ritualAt(double fraction) {
        ClientRitual r = ClientRitualController.ritual();
        return r != null && ClientRitualController.phase() == ClientRitualController.Phase.PLAYING && r.nowMs() >= r.pattern.endMs() * fraction;
    }

    private static void remember(String label) {
        snapshotServer(label + "_before");
    }

    /** Records the server player's levels, lapis and the table item's enchantments under {@code prefix}. */
    private static void snapshotServer(String prefix) {
        server(s -> {
            ServerPlayer p = player(s);
            server.put(prefix + "_xp", p.experienceLevel);
            server.put(prefix + "_xpvalue", p.experienceLevel + (double) p.experienceProgress);
            if (p.containerMenu instanceof EnchantmentMenu menu) {
                server.put(prefix + "_lapis", menu.getSlot(1).getItem().getCount());
                server.put(prefix + "_item", map(EnchantmentHelper.getEnchantments(menu.getSlot(0).getItem())));
            }
        });
    }

    /** Snapshot now, evaluate a few ticks later once the integrated server has answered. */
    private static void verify(String label, boolean fullExpected) {
        act("snapshot " + label, 8, mc -> snapshotServer(label + "_after"));
        act("verify " + label, 2, mc -> evaluate(label, fullExpected));
    }

    private static void evaluate(String label, boolean fullExpected) {
        ResultView result = ClientRitualController.result();
        Map<?, ?> plan = (Map<?, ?>) server.get(label + "_plan");
        Map<?, ?> after = (Map<?, ?>) server.get(label + "_after_item");
        int xpBefore = (int) server.get(label + "_before_xp"), xpAfter = (int) server.get(label + "_after_xp");
        int lapisBefore = (int) server.get(label + "_before_lapis"), lapisAfter = (int) server.get(label + "_after_lapis");
        checks.put(label + "_band", result == null ? "none" : result.band().id());
        checks.put(label + "_score", result == null ? -1 : result.score());
        checks.put(label + "_plan", String.valueOf(plan));
        checks.put(label + "_bound", String.valueOf(after));
        if (result != null && ClientRitualController.localResult() != null) {
            checks.put(label + "_local_score_matches_server", ClientRitualController.localResult().finalScore() == result.score());
        }
        String expectedAdapter = ImmersiveEnchanting.MOD_ID + ":" + (EXPECT.isEmpty() ? "vanilla" : EXPECT);
        checks.put(label + "_adapter", server.get(label + "_adapter"));
        checks.put(label + "_owned_by_expected_adapter", expectedAdapter.equals(server.get(label + "_adapter")));
        checks.put(label + "_levels_charged", xpBefore - xpAfter);
        checks.put(label + "_lapis_charged", lapisBefore - lapisAfter);
        if (fullExpected) {
            checks.put(label + "_full_binding", result != null && result.band().grantsFull());
            checks.put(label + "_bound_exactly_the_plan", plan != null && plan.equals(after));
        } else if (label.equals("silent")) {
            checks.put(label + "_failed", result != null && result.band().isFailure());
            checks.put(label + "_nothing_bound", after != null && after.isEmpty());
        } else {
            boolean subset = plan != null && after != null;
            if (subset) {
                for (Map.Entry<?, ?> e : after.entrySet()) {
                    Object planned = plan.get(e.getKey());
                    subset &= planned != null && (int) e.getValue() <= (int) planned;
                }
            }
            checks.put(label + "_bound_subset_of_plan", subset);
        }
        int price = (int) server.getOrDefault(label + "_price", -1);
        // Apotheosis charges experience points (a fraction of a level at high levels); vanilla charges whole levels.
        boolean levelsCharged = EXPECT.equals("apotheosis")
                ? (double) server.get(label + "_after_xpvalue") < (double) server.get(label + "_before_xpvalue")
                : xpBefore - xpAfter == price;
        checks.put(label + "_charged_normal_price", lapisBefore - lapisAfter == price && levelsCharged);
    }

    private static Map<String, Integer> map(Map<Enchantment, Integer> enchantments) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        enchantments.forEach((e, l) -> out.put(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(e)), l));
        return out;
    }

    private static Map<String, Integer> map(List<EnchantmentInstance> list) {
        Map<Enchantment, Integer> m = new HashMap<>();
        for (EnchantmentInstance e : list) m.put(e.enchantment, e.level);
        return map(m);
    }

    // ---- driving ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) {
                    originalVsync = mc.options.enableVsync().get();
                    originalFrameLimit = mc.options.framerateLimit().get();
                    originalPauseOnLostFocus = mc.options.pauseOnLostFocus;
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(120);
                    mc.options.pauseOnLostFocus = false;
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    mc.getToasts().clear();
                    sizeWindow(mc, 1280, 720);
                }
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    createWorld(mc);
                }
                return;
            }
            if (stepIndex >= steps.size()) {
                finish(mc, allPassed(), "complete");
                return;
            }
            Step step = steps.get(stepIndex);
            boolean done = step.action().test(mc);
            stepTicks++;
            if (done) {
                log("step done: " + step.name() + " (" + stepTicks + " ticks)");
                stepIndex++;
                stepTicks = 0;
            } else if (stepTicks > step.timeoutTicks()) {
                checks.put("timeout", step.name());
                checks.put("timeout_screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
                finish(mc, false, "timed out at " + step.name());
            }
        } catch (Exception e) {
            ImmersiveEnchanting.LOGGER.error("SMOKETEST failed at step {}", stepIndex < steps.size() ? steps.get(stepIndex).name() : "end", e);
            checks.put("agent_exception", e.toString());
            finish(mc, false, "agent exception");
        }
    }

    /** Delivers scripted key transitions every frame, exactly as the keyboard handler would. */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (scripted == null || scriptIndex >= script.size()) return;
        Screen screen = event.getScreen();
        long now = scripted.nowMs();
        while (scriptIndex < script.size() && script.get(scriptIndex).timeMs() <= now) {
            InputRecord r = script.get(scriptIndex++);
            int key = RitualKeyMappings.bindings(scripted.pattern.anchors())[r.lane()].getKey().getValue();
            if (r.pressed()) {
                if (!ForgeHooksClient.onScreenKeyPressedPre(screen, key, 0, 0)) screen.keyPressed(key, 0, 0);
            } else if (!ForgeHooksClient.onScreenKeyReleasedPre(screen, key, 0, 0)) {
                screen.keyReleased(key, 0, 0);
            }
        }
    }

    private static void autoplay(ClientRitual ritual, List<InputRecord> inputs) {
        ritual.releaseOnFocusLoss = false;
        scripted = ritual;
        script = inputs;
        scriptIndex = 0;
    }

    private static void key(Minecraft mc, int key, boolean release) {
        Screen screen = mc.screen;
        if (screen == null) return;
        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, key, 0, 0)) screen.keyPressed(key, 0, 0);
        if (release && mc.screen == screen && !ForgeHooksClient.onScreenKeyReleasedPre(screen, key, 0, 0)) screen.keyReleased(key, 0, 0);
    }

    /** Clicks an offer row through Forge's pre-click event, as the mouse handler does. */
    private static void click(Minecraft mc, int offer) {
        if (!(mc.screen instanceof EnchantmentScreen screen)) return;
        double x = screen.getGuiLeft() + 60 + 54, y = screen.getGuiTop() + 14 + 19 * offer + 9;
        boolean cancelled = ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, 0);
        if (!cancelled) screen.mouseClicked(x, y, 0);
        if (screen.getClass() == EnchantmentScreen.class) checks.put("click_" + offer + "_cancelled_vanilla", cancelled);
        else checks.put("click_" + offer + "_screen", screen.getClass().getName());
    }

    private static void hover(Minecraft mc, int offer) {
        if (!(mc.screen instanceof EnchantmentScreen screen)) return;
        double scale = mc.getWindow().getGuiScale();
        double x = (screen.getGuiLeft() + 60 + 30) * scale, y = (screen.getGuiTop() + 14 + 19 * offer + 9) * scale;
        try {
            Field xf = MouseHandler.class.getDeclaredField("xpos");
            Field yf = MouseHandler.class.getDeclaredField("ypos");
            xf.setAccessible(true);
            yf.setAccessible(true);
            xf.setDouble(mc.mouseHandler, x);
            yf.setDouble(mc.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            log("cannot move the mouse: " + e);
        }
    }

    private static EnchantmentMenu menu(Minecraft mc) {
        return mc.player != null && mc.player.containerMenu instanceof EnchantmentMenu m ? m : null;
    }

    /**
     * A predictable test window. A tiling Wayland compositor ignores the requested size, so on Hyprland this
     * client's own window (matched by process id) is floated and sized; nothing else on the desktop is touched.
     */
    private static void sizeWindow(Minecraft mc, int width, int height) {
        mc.getWindow().setWindowed(width, height);
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null) return;
        String target = "window = \"pid:" + ProcessHandle.current().pid() + "\"";
        Thread thread = new Thread(() -> {
            // Hyprland 0.55+ takes Lua dispatchers.
            for (String dispatch : new String[]{"hl.dsp.window.float({ action = \"enable\", " + target + " })",
                    "hl.dsp.window.resize({ x = " + width + ", y = " + height + ", " + target + " })", "hl.dsp.window.move({ x = 40, y = 40, " + target + " })"}) {
                try {
                    new ProcessBuilder("hyprctl", "dispatch", dispatch).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start().waitFor();
                } catch (IOException e) {
                    log("hyprctl unavailable: " + e.getMessage());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "smoketest-window");
        thread.setDaemon(true);
        thread.start();
    }

    private static void createWorld(Minecraft mc) {
        String name = "SmokeTest" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260925L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void stageWorld(Minecraft mc) {
        server(s -> {
            ServerLevel level = s.overworld();
            ServerPlayer player = player(s);
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
            BlockPos base = player.blockPosition();
            for (int x = -4; x <= 4; x++) for (int z = -2; z <= 7; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            table = base.offset(0, 0, 3);
            level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
            for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
                if (offset.getZ() == -2 && Math.abs(offset.getX()) <= 1) continue; // leave the player's side open
                level.setBlockAndUpdate(table.offset(offset), Blocks.BOOKSHELF.defaultBlockState());
            }
            player.teleportTo(level, table.getX() + 0.5, table.getY(), table.getZ() - 1.5, 0F, 30F);
            player.setExperienceLevels(60);
        });
    }

    private static void server(java.util.function.Consumer<IntegratedServer> action) {
        IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> action.accept(s));
    }

    private static ServerPlayer player(IntegratedServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    private static boolean allPassed() {
        for (Object value : checks.values()) if (value instanceof Boolean b && !b) return false;
        return !checks.containsKey("agent_exception") && !checks.containsKey("timeout");
    }

    private static void finish(Minecraft mc, boolean pass, String reason) {
        finished = true;
        if (originalGuiScale != null) { mc.options.guiScale().set(originalGuiScale); mc.resizeDisplay(); }
        if (originalVsync != null) mc.options.enableVsync().set(originalVsync);
        if (originalFrameLimit != null) mc.options.framerateLimit().set(originalFrameLimit);
        if (originalPauseOnLostFocus != null) mc.options.pauseOnLostFocus = originalPauseOnLostFocus;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("seconds", (Util.getMillis() - START) / 1000);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "smoketest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveEnchanting.LOGGER.error("SMOKETEST could not write result", e);
        }
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void log(String message) {
        ImmersiveEnchanting.LOGGER.info("SMOKETEST {}", message);
    }

    private ClientSmokeTest() {}
}
