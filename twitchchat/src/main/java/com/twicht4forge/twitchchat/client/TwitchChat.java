package com.twicht4forge.twitchchat.client;

import com.twicht4forge.twitchchat.irc.ChatLog;
import com.twicht4forge.twitchchat.irc.TwitchIrcClient;
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

    public static synchronized Component status() {
        ChatConfig cfg = config();
        if (!cfg.enabled) {
            return new TranslatableComponent("twitchchat.status.disabled");
        }
        if (client == null) {
            return new TranslatableComponent("twitchchat.status.nochannel");
        }
        return switch (client.state()) {
            case CONNECTING -> new TranslatableComponent("twitchchat.status.connecting");
            case JOINING -> new TranslatableComponent("twitchchat.status.joining", client.channel());
            case CONNECTED -> new TranslatableComponent("twitchchat.status.connected", client.channel());
            case RECONNECTING -> new TranslatableComponent("twitchchat.status.reconnecting");
        };
    }
}
