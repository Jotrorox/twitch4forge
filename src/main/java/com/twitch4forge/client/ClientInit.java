package com.twitch4forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public final class ClientInit {
    public static final Logger LOGGER = LogManager.getLogger("twitch4forge");
    public static final KeyMapping OPEN_CONFIG = new KeyMapping("key.twitch4forge.config",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.twitch4forge");
    /** Unbound by default so it cannot clash with another mod. */
    public static final KeyMapping TOGGLE = new KeyMapping("key.twitch4forge.toggle",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.twitch4forge");

    private ClientInit() {
    }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientInit::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onClientTick);
        // Makes the "Config" button in the Mods list open the same screen as the key.
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(OPEN_CONFIG);
        ClientRegistry.registerKeyBinding(TOGGLE);
        OverlayRegistry.registerOverlayTop("Twitch Chat", (gui, poseStack, partialTick, width, height) -> {
            Minecraft mc = Minecraft.getInstance();
            ChatConfig cfg = TwitchChat.config();
            // The settings screen draws its own copy so it can be dragged around.
            if (!cfg.enabled || mc.options.hideGui || mc.options.renderDebug || mc.screen instanceof ConfigScreen) {
                return;
            }
            int[] r = cfg.rect(width, height);
            boolean showAll = cfg.showAllInChat && mc.screen instanceof ChatScreen;
            ChatRenderer.INSTANCE.render(poseStack, mc.font, r[0], r[1], r[2], r[3], cfg, false, showAll);
        });
        TwitchChat.apply();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_CONFIG.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new ConfigScreen(null));
            }
        }
        while (TOGGLE.consumeClick()) {
            ChatConfig cfg = TwitchChat.config();
            cfg.enabled = !cfg.enabled;
            cfg.save();
            TwitchChat.apply();
            mc.gui.setOverlayMessage(new TranslatableComponent(cfg.enabled ? "twitch4forge.toggle.on" : "twitch4forge.toggle.off"), false);
        }
    }
}
