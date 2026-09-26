package com.otectus.immersiveenchanting.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The four binding inputs, remappable in Options > Controls: left, down, up and right, A S W D by default. They are only
 * read while a ritual is running, so they use their own conflict context: sharing a key with movement or hotbar keys is
 * fine and is not flagged.
 */
public final class RitualKeyMappings {
    public static final String CATEGORY = "key.categories.immersive_enchanting";

    public static final IKeyConflictContext RITUAL_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return ClientRitualController.isPlaying() || RitualPracticeScreen.isOpen();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    /** Left, down, up, right: the four-anchor lanes, which sit west, south, north and east. */
    private static final KeyMapping[] FOUR_ANCHORS = {
            binding(1, GLFW.GLFW_KEY_A),
            binding(2, GLFW.GLFW_KEY_S),
            binding(3, GLFW.GLFW_KEY_W),
            binding(4, GLFW.GLFW_KEY_D)};
    /** Three anchors arch west, north and east, so they skip down. */
    private static final KeyMapping[] THREE_ANCHORS = {FOUR_ANCHORS[0], FOUR_ANCHORS[2], FOUR_ANCHORS[3]};

    private static KeyMapping binding(int number, int key) {
        return new KeyMapping("key.immersive_enchanting.bind_rune_" + number, RITUAL_CONTEXT, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : FOUR_ANCHORS) event.register(mapping);
    }

    /** The binding for each lane of a ritual with this many anchors, in lane order. */
    public static KeyMapping[] bindings(int anchors) {
        return anchors >= 4 ? FOUR_ANCHORS : THREE_ANCHORS;
    }

    /** Lane bound to a keyboard key, or -1. */
    public static int laneForKey(int keyCode, int scanCode, int anchors) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        KeyMapping[] lanes = bindings(anchors);
        for (int i = 0; i < lanes.length; i++) {
            if (lanes[i].getKey().equals(key)) return i;
        }
        return -1;
    }

    /** Lane bound to a mouse button, or -1. */
    public static int laneForMouse(int button, int anchors) {
        KeyMapping[] lanes = bindings(anchors);
        for (int i = 0; i < lanes.length; i++) {
            if (lanes[i].matchesMouse(button)) return i;
        }
        return -1;
    }

    /** The player's actual binding, for on-screen labels. */
    public static Component label(int lane, int anchors) {
        return bindings(anchors)[lane].getTranslatedKeyMessage();
    }

    private RitualKeyMappings() {}
}
