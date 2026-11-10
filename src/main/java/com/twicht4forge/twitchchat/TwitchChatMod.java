package com.twicht4forge.twitchchat;

import com.twicht4forge.twitchchat.client.ClientInit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;

@Mod(TwitchChatMod.MOD_ID)
public final class TwitchChatMod {
    public static final String MOD_ID = "twitchchat";

    public TwitchChatMod() {
        // Client-only: servers and other players do not need this mod.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init();
        }
    }
}
