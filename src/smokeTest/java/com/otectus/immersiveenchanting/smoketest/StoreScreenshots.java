package com.otectus.immersiveenchanting.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersiveenchanting.client.ClientRitual;
import com.otectus.immersiveenchanting.client.ClientRitualController;
import com.otectus.immersiveenchanting.client.OfferPreview;
import com.otectus.immersiveenchanting.client.RitualKeyMappings;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.ritual.Autoplay;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Opt-in CurseForge screenshot capture, confined to the test agent and enabled only by
 * {@code -Dimmersive_enchanting.screenshots.output=<dir>}. It builds an enchanter's library, opens the real table,
 * clicks real offers and plays the rituals with key events, saving framebuffer captures without HUD, chat or toasts.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class StoreScreenshots {
    private static final String OUTPUT = System.getProperty("immersive_enchanting.screenshots.output");
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int step, action, baseY;
    private static long entered, started;
    private static boolean finished, renamed;
    private static volatile float lockYaw = Float.NaN, lockPitch;
    private static double cursorX = 0.3, cursorY = 0.3;
    private static BlockPos table;
    private static List<InputRecord> script = List.of();
    private static int scriptIndex;
    private static ClientRitual scripted;

    static boolean enabled() { return OUTPUT != null; }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (enabled()) event.setCanceled(true);
    }

    /** The player profile is built from the session user when the world is created, so rename it on the title screen. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!enabled() || renamed || !(event.getScreen() instanceof TitleScreen)) return;
        renamed = true;
        try {
            var name = net.minecraft.client.User.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(Minecraft.getInstance().getUser(), "Otectus");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not rename the showcase player", e);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (enabled() && event.phase == TickEvent.Phase.START) lockView(Minecraft.getInstance());
    }

    /** Delivers scripted key transitions every frame, exactly as the keyboard handler would. */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!enabled() || scripted == null || scriptIndex >= script.size()) return;
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

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (step == 0 && mc.screen != null) return;
        if (started == 0) started = Util.getMillis();
        if (Util.getMillis() - started > 240_000) throw new IllegalStateException("Store screenshots stalled at step " + step);
        mc.getToasts().clear();
        lockView(mc);
        if (mc.screen != null) placeCursor(mc);
        if (pending != null && !pending.isDone()) return;
        if (pending != null) { pending.join(); pending = null; }
        long age = Util.getMillis() - entered;
        switch (step) {
            case 0 -> {
                Files.createDirectories(Path.of(OUTPUT));
                baseY = mc.player.blockPosition().getY();
                table = new BlockPos(0, baseY, -3);
                mc.getWindow().setWindowed(WIDTH, HEIGHT);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.options.fov().set(70);
                mc.options.bobView().set(false);
                mc.options.renderDistance().set(12);
                mc.options.gamma().set(1.0D);
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                ClientConfig.RITUAL_LAYOUT.set(ClientConfig.RitualLayout.RADIAL);
                server(mc, StoreScreenshots::buildLibrary);
                advance(1);
            }
            case 1 -> {
                if (mc.getWindow().getWidth() != WIDTH && age > 1500 && action == 0) { mc.getWindow().toggleFullScreen(); action++; }
                if (age > 7000) {
                    EVIDENCE.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
                    shot(mc, "library");
                    server(mc, (level, player) -> look(player, 0.5, 2.05, 1.0, 0.5, 0.9, -2.5));
                    advance(2);
                }
            }
            case 2 -> {
                if (action == 0 && age > 3500) { shot(mc, "table"); action++; }
                if (action == 1 && age > 3800) { use(mc, table); action++; }
                if (action == 2 && mc.screen instanceof EnchantmentScreen) { load(mc, Items.DIAMOND_SWORD); advance(3); }
            }
            case 3 -> {
                EnchantmentMenu menu = menu(mc);
                if (action == 0 && menu != null && menu.costs[2] > 0 && OfferPreview.get(menu.containerId) != null && age > 1200) {
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                    action++;
                }
                if (action == 1 && age > 1600) { hover(mc, 2); action++; }
                if (action == 2 && age > 2600) { shot(mc, "offers"); action++; }
                if (action == 3 && age > 2800) {
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    action++;
                }
                if (action == 4 && age > 3300) { cursorX = .3; cursorY = .3; click(mc, 2); advance(4); }
            }
            case 4 -> ritual(mc, age, "radial", Autoplay::perfect, 5);
            case 5 -> {
                if (action == 0) {
                    ClientConfig.RITUAL_LAYOUT.set(ClientConfig.RitualLayout.CLASSIC_LANES);
                    load(mc, Items.BOW);
                    action++;
                }
                EnchantmentMenu menu = menu(mc);
                if (action == 1 && age > 1500 && ClientRitualController.phase() == ClientRitualController.Phase.IDLE
                        && menu != null && menu.costs[2] > 0) {
                    click(mc, 2);
                    advance(6);
                }
            }
            case 6 -> ritual(mc, age, "lanes", pattern -> Autoplay.play(pattern, 0.12, 60, 7L), 7);
            case 7 -> {
                if (action == 0) {
                    mc.player.closeContainer();
                    server(mc, (level, player) -> look(player, -2.2, 2.3, 2.2, 0.3, 0.9, -2.6));
                    action++;
                }
                if (action == 1 && age > 4000) {
                    shot(mc, "library_outro");
                    EVIDENCE.put("shots", SHOTS);
                    EVIDENCE.put("pass", true);
                    Files.writeString(Path.of(OUTPUT, "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
                    finished = true;
                    mc.stop();
                }
            }
        }
    }

    /** Plays the ritual just requested: count-in, a mid-ritual frame, the result panel, then dismisses it. */
    private static void ritual(Minecraft mc, long age, String label, java.util.function.Function<com.otectus.immersiveenchanting.ritual.RitualPattern, List<InputRecord>> player, int next) {
        var phase = ClientRitualController.phase();
        ClientRitual ritual = ClientRitualController.ritual();
        if (action == 0 && phase == ClientRitualController.Phase.PLAYING && ritual != null) {
            ritual.releaseOnFocusLoss = false;
            scripted = ritual;
            script = player.apply(ritual.pattern);
            scriptIndex = 0;
            EVIDENCE.put(label + "_tier", ritual.params.tier());
            EVIDENCE.put(label + "_runes", ritual.pattern.events().size());
            action++;
        }
        if (action == 1 && ritual != null && phase == ClientRitualController.Phase.PLAYING
                && ritual.nowMs() > ritual.pattern.countInMs() * 0.55 && ritual.nowMs() < ritual.pattern.countInMs()) {
            shot(mc, label + "_countin");
            action++;
        }
        if (action >= 1 && action <= 4 && ritual != null && phase == ClientRitualController.Phase.PLAYING) {
            // Frames at 30, 50 and 70% of the pattern; the busiest one is chosen afterwards.
            if (action <= 2 && ritual.nowMs() >= ritual.pattern.endMs() * 0.3) { shot(mc, label + "_ritual_30"); action = 3; }
            else if (action == 3 && ritual.nowMs() >= ritual.pattern.endMs() * 0.5) { shot(mc, label + "_ritual_50"); action = 4; }
            else if (action == 4 && ritual.nowMs() >= ritual.pattern.endMs() * 0.7) { shot(mc, label + "_ritual_70"); action = 5; }
        }
        if (action >= 3 && action <= 5 && phase == ClientRitualController.Phase.RESULT) {
            action = 6;
            entered = Util.getMillis();
            return;
        }
        if (action == 6 && age > 1400) { shot(mc, label + "_result"); action++; }
        if (action == 7 && age > 1700) {
            key(mc, GLFW.GLFW_KEY_SPACE);
            scripted = null;
            advance(next);
        }
    }

    // ------------------------------------------------------------------ table

    private static void load(Minecraft mc, Item item) {
        server(mc, (level, player) -> {
            if (player.containerMenu instanceof EnchantmentMenu menu) {
                menu.getSlot(0).set(new ItemStack(item));
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
                player.setExperienceLevels(60);
            }
        });
    }

    private static EnchantmentMenu menu(Minecraft mc) {
        return mc.player != null && mc.player.containerMenu instanceof EnchantmentMenu m ? m : null;
    }

    /** Clicks an offer row through Forge's pre-click event, as the mouse handler does. */
    private static void click(Minecraft mc, int offer) {
        if (!(mc.screen instanceof EnchantmentScreen screen)) return;
        double x = screen.getGuiLeft() + 60 + 54, y = screen.getGuiTop() + 14 + 19 * offer + 9;
        if (!ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, 0)) screen.mouseClicked(x, y, 0);
    }

    private static void hover(Minecraft mc, int offer) {
        if (!(mc.screen instanceof EnchantmentScreen screen)) return;
        cursorX = screen.getGuiLeft() + 60 + 30;
        cursorY = screen.getGuiTop() + 14 + 19 * offer + 9;
    }

    private static void key(Minecraft mc, int key) {
        Screen screen = mc.screen;
        if (screen == null) return;
        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, key, 0, 0)) screen.keyPressed(key, 0, 0);
        if (mc.screen == screen && !ForgeHooksClient.onScreenKeyReleasedPre(screen, key, 0, 0)) screen.keyReleased(key, 0, 0);
    }

    // ------------------------------------------------------------------ helpers

    private static void shot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String file = String.format("%02d_%s.png", SHOTS.size() + 1, name);
            image.writeToFile(Path.of(OUTPUT, file));
            SHOTS.add(file);
        } catch (Exception e) {
            throw new IllegalStateException("Screenshot " + name + " failed", e);
        }
    }

    /** Pins the view to the last scripted camera so pointer motion reaching the window cannot turn the shot. */
    private static void lockView(Minecraft mc) {
        var player = mc.player;
        if (player == null || Float.isNaN(lockYaw)) return;
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
        player.setYHeadRot(lockYaw);
        player.yHeadRotO = lockYaw;
        player.yBodyRot = lockYaw;
        player.yBodyRotO = lockYaw;
    }

    /** Puts the pointer where the script wants it (a corner otherwise); gamescope ignores cursor warps. */
    private static void placeCursor(Minecraft mc) {
        double scale = mc.getWindow().getGuiScale();
        try {
            setField(mc.mouseHandler, "xpos", cursorX * scale);
            setField(mc.mouseHandler, "ypos", cursorY * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not place the cursor", e);
        }
    }

    private static void use(Minecraft mc, BlockPos position) {
        server(mc, (level, player) -> level.getBlockState(position).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false)));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> work) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Store screenshots need an integrated server");
        pending = CompletableFuture.runAsync(() -> work.accept(server.overworld(), server.getPlayerList().getPlayers().get(0)), server);
    }

    /** Places the eye at (x, baseY + y, z) looking at (tx, baseY + ty, tz); flight keeps elevated cameras still. */
    private static void look(ServerPlayer player, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        lockYaw = yaw;
        lockPitch = pitch;
        player.teleportTo(player.serverLevel(), x, baseY + y - player.getEyeHeight(), z, yaw, pitch);
    }

    private static void cmd(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        cmd(server, "fill " + x1 + " " + (baseY + y1) + " " + z1 + " " + x2 + " " + (baseY + y2) + " " + z2 + " " + block);
    }

    private static void set(MinecraftServer server, int x, int y, int z, String block) {
        cmd(server, "setblock " + x + " " + (baseY + y) + " " + z + " " + block);
    }

    private static void buildLibrary(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        level.setDayTime(18000);
        level.setWeatherParameters(60000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
        cmd(server, "kill @e[type=!player]");
        fill(server, -16, 0, -18, 16, 10, 12, "air");
        RandomSource random = RandomSource.create(20260927L);
        // Floor: deepslate tiles with a purple rug around the table.
        for (int x = -7; x <= 7; x++) for (int z = -10; z <= 4; z++) {
            float r = random.nextFloat();
            set(server, x, -1, z, r < .55F ? "deepslate_tiles" : r < .8F ? "polished_deepslate" : r < .92F ? "cracked_deepslate_tiles" : "deepslate_bricks");
        }
        fill(server, -3, 0, -6, 3, 0, 0, "purple_carpet");
        fill(server, -2, 0, -5, 2, 0, -1, "magenta_carpet");
        // Walls of shelves between dark oak pillars, open to the front.
        fill(server, -7, 0, -10, 7, 5, -10, "bookshelf");
        fill(server, -7, 0, -10, -7, 5, 4, "bookshelf");
        fill(server, 7, 0, -10, 7, 5, 4, "bookshelf");
        for (int x : new int[]{-7, -4, 0, 4, 7}) fill(server, x, 0, -10, x, 5, -10, "dark_oak_log");
        for (int z : new int[]{-10, -6, -2, 2}) {
            fill(server, -7, 0, z, -7, 5, z, "dark_oak_log");
            fill(server, 7, 0, z, 7, 5, z, "dark_oak_log");
        }
        fill(server, -2, 2, -10, 2, 4, -10, "purple_stained_glass_pane");
        fill(server, 0, 2, -10, 0, 4, -10, "dark_oak_log");
        fill(server, -7, 6, -10, 7, 6, 4, "dark_oak_planks");
        for (int z : new int[]{-8, -4, 0, 3}) fill(server, -6, 5, z, 6, 5, z, "dark_oak_log[axis=x]");
        for (int x : new int[]{-4, 4}) fill(server, x, 5, -9, x, 5, 4, "dark_oak_log[axis=z]");
        for (int[] p : new int[][]{{-4, -2}, {4, -2}, {-4, 2}, {4, 2}, {0, -8}}) {
            set(server, p[0], 4, p[1], "chain");
            set(server, p[0], 3, p[1], "soul_lantern[hanging=true]");
        }
        // The table and its shelves, open on the reader's side.
        level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (offset.getZ() == 2 && Math.abs(offset.getX()) <= 1) continue;
            level.setBlockAndUpdate(table.offset(offset), Blocks.BOOKSHELF.defaultBlockState());
        }
        String[] candles = {"candle[candles=3,lit=true]", "purple_candle[candles=2,lit=true]", "white_candle[candles=4,lit=true]",
                "amethyst_cluster[facing=up]", "candle[candles=1,lit=true]"};
        int i = 0;
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (offset.getY() != 1 || (offset.getZ() == 2 && Math.abs(offset.getX()) <= 1) || random.nextFloat() < .35F) continue;
            BlockPos top = table.offset(offset).above();
            set(server, top.getX(), top.getY() - baseY, top.getZ(), candles[i++ % candles.length]);
        }
        // Reading corners and curios.
        set(server, -5, 0, -8, "lectern[facing=south,has_book=true]");
        set(server, 5, 0, -8, "lectern[facing=south,has_book=true]");
        set(server, -6, 0, -4, "brewing_stand");
        set(server, 6, 0, -4, "cauldron");
        set(server, -6, 0, 1, "chiseled_bookshelf[facing=east,slot_0_occupied=true,slot_1_occupied=true,slot_3_occupied=true,slot_4_occupied=true]");
        set(server, 6, 0, 1, "chiseled_bookshelf[facing=west,slot_1_occupied=true,slot_2_occupied=true,slot_5_occupied=true]");
        set(server, -6, 1, 1, "purple_candle[candles=3,lit=true]");
        set(server, 6, 1, 1, "candle[candles=2,lit=true]");
        set(server, -6, 0, -1, "amethyst_block");
        set(server, -6, 1, -1, "amethyst_cluster[facing=up]");
        set(server, 6, 0, -1, "amethyst_block");
        set(server, 6, 1, -1, "large_amethyst_bud[facing=up]");
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.BOOK, 3));
        player.getInventory().setItem(1, new ItemStack(Items.LAPIS_LAZULI, 32));
        player.setExperienceLevels(60);
        player.inventoryMenu.broadcastChanges();
        look(player, 0.5, 2.8, 3.8, 0.5, 1.0, -3.0);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        find(target.getClass(), name).set(target, value);
    }

    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void advance(int next) {
        step = next;
        action = 0;
        entered = Util.getMillis();
    }

    private StoreScreenshots() {}
}
