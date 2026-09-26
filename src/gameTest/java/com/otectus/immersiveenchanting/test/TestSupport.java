package com.otectus.immersiveenchanting.test;

import com.mojang.authlib.GameProfile;
import com.otectus.immersiveenchanting.network.ModNetwork;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.session.SessionClock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class TestSupport {
    static final BlockPos TABLE = new BlockPos(2, 1, 2);
    private static final Map<UUID, List<Object>> SENT = new ConcurrentHashMap<>();
    private static final AtomicInteger CONTAINER_IDS = new AtomicInteger(900);

    static {
        ModNetwork.fakePlayerSink = (player, message) -> SENT.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(message);
        // The test server runs ticks faster than real time; sessions follow game time here (50 ms per tick).
        SessionClock.useSource(() -> ServerLifecycleHooks.getCurrentServer().getTickCount() * 50L);
    }

    static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RitualTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        BlockPos pos = h.absolutePos(TABLE);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5 - 1.2);
        player.experienceLevel = 30;
        return player;
    }

    /** An enchanting table with every bookshelf position filled (the full 15-shelf power). */
    static void table(GameTestHelper h) {
        h.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            BlockPos pos = TABLE.offset(offset);
            if (pos.getX() >= 0 && pos.getX() < 5 && pos.getZ() >= 0 && pos.getZ() < 5 && pos.getY() >= 1) h.setBlock(pos, Blocks.BOOKSHELF);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 1; dy++) {
            if (dx == 0 && dz == 0 && dy == 0) continue;
            h.setBlock(TABLE.offset(dx, dy, dz), Blocks.AIR);
        }
    }

    static EnchantmentMenu open(GameTestHelper h, FakePlayer player, ItemStack item, int lapis) {
        EnchantmentMenu menu = new EnchantmentMenu(CONTAINER_IDS.incrementAndGet(), player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
        player.containerMenu = menu;
        if (lapis > 0) menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, lapis));
        menu.getSlot(0).set(item);
        return menu;
    }

    static <T> List<T> sent(FakePlayer player, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Object o : SENT.getOrDefault(player.getUUID(), List.of())) if (type.isInstance(o)) out.add(type.cast(o));
        return out;
    }

    static <T> T last(FakePlayer player, Class<T> type) {
        List<T> all = sent(player, type);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** The timeline exactly as a client would generate it from the start message. */
    static RitualPattern pattern(RitualStartedS2C started) {
        return PatternGenerator.generate(started.params().sanitized(), started.patternSeed());
    }

    static Map<Enchantment, Integer> enchantments(ItemStack stack) {
        return EnchantmentHelper.getEnchantments(stack);
    }

    /** Game ticks to wait for {@code ms} of real time, with a small margin. */
    static int ticks(long ms) {
        return (int) (ms / 50) + 4;
    }

    private TestSupport() {}
}
