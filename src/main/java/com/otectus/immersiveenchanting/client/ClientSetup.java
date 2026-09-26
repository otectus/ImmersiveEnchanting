package com.otectus.immersiveenchanting.client;

import net.minecraftforge.eventbus.api.IEventBus;

/** Client-only wiring, reached through {@code DistExecutor} from the mod constructor. */
public final class ClientSetup {
    public static void init(IEventBus modBus) {
        modBus.addListener(RitualKeyMappings::register);
    }

    private ClientSetup() {}
}
