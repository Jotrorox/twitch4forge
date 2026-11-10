package com.twitch4forge.client;

import com.twitch4forge.irc.ChatLog;
import com.twitch4forge.irc.TwitchIrcClient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/** Owns the settings, the message history and the connection that matches the settings. */
public final class TwitchChat {
    public static final ChatLog LOG = new ChatLog();
    private static ChatConfig config;
    private static TwitchIrcClient client;

    private TwitchChat() {
    }

    public static ChatConfig config() {
        if (config == null) {
            config = ChatConfig.load();
        }
        return config;
    }

    /** Connects, reconnects or disconnects so the connection matches the current settings. */
    public static synchronized void apply() {
        ChatConfig cfg = config();
        String wanted = cfg.enabled ? cfg.channel : "";
        if (client != null && client.channel().equals(wanted)) {
            return;
        }
        if (client != null) {
            client.stop();
            client = null;
        }
        LOG.clear();
        if (!wanted.isEmpty()) {
            client = new TwitchIrcClient(wanted, LOG);
            client.start();
        }
    }

    /** Channel the widget is currently connected to, or "" if none. */
    public static synchronized String connectedChannel() {
        return client == null ? "" : client.channel();
    }

    public static synchronized TwitchIrcClient.State state() {
        return client == null ? null : client.state();
    }

    /** Opaque colour of the connection dot: green when live, amber while connecting, grey when off. */
    public static synchronized int stateColor() {
        if (client == null) {
            return 0xFF808080;
        }
        return client.state() == TwitchIrcClient.State.CONNECTED ? 0xFF40D040 : 0xFFE0B020;
    }

    public static synchronized Component status() {
        ChatConfig cfg = config();
        if (!cfg.enabled) {
            return new TranslatableComponent("twitch4forge.status.disabled");
        }
        if (client == null) {
            return new TranslatableComponent("twitch4forge.status.nochannel");
        }
        return switch (client.state()) {
            case CONNECTING -> new TranslatableComponent("twitch4forge.status.connecting");
            case JOINING -> new TranslatableComponent("twitch4forge.status.joining", client.channel());
            case CONNECTED -> new TranslatableComponent("twitch4forge.status.connected", client.channel());
            case RECONNECTING -> new TranslatableComponent("twitch4forge.status.reconnecting");
        };
    }
}
