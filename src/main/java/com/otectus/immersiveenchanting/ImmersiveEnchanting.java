package com.otectus.immersiveenchanting;

import com.mojang.logging.LogUtils;
import com.otectus.immersiveenchanting.client.ClientSetup;
import com.otectus.immersiveenchanting.compat.CompatibilityBootstrap;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.network.ModNetwork;
import com.otectus.immersiveenchanting.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ImmersiveEnchanting.MOD_ID)
public class ImmersiveEnchanting {
    public static final String MOD_ID = "immersive_enchanting";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveEnchanting() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(ServerConfig::onConfigChanged);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            AdapterRegistry.registerBuiltIn();
            CompatibilityBootstrap.init();
        });
    }

    private void loadComplete(final FMLLoadCompleteEvent event) {
        AdapterRegistry.freeze();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** Diagnostic logging for adapter choice, profiles, complexity and session transitions. */
    public static void debug(String message, Object... args) {
        if (ServerConfig.verboseLogging()) LOGGER.info("[ritual] " + message, args);
        else LOGGER.debug("[ritual] " + message, args);
    }
}
