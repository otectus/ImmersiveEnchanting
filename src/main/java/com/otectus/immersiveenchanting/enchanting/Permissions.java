package com.otectus.immersiveenchanting.enchanting;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

@Mod.EventBusSubscriber(modid = ImmersiveEnchanting.MOD_ID)
public final class Permissions {
    /** Players with this node enchant instantly when {@code allowServerBypassPermission} is on. Default: nobody. */
    public static final PermissionNode<Boolean> BYPASS_RITUAL = new PermissionNode<>(ImmersiveEnchanting.MOD_ID, "bypass_ritual",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> false);

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BYPASS_RITUAL);
    }

    private Permissions() {}
}
