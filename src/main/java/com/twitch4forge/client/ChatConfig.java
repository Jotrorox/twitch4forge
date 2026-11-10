package com.twitch4forge.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Widget settings, stored in config/twitch4forge.json and edited through {@link ConfigScreen}. */
public final class ChatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MIN_WIDTH = 80;
    public static final int MIN_HEIGHT = 40;
    public static final double MIN_TEXT_SCALE = 0.5;
    public static final double MAX_TEXT_SCALE = 2.0;
    public static final int MAX_LIFETIME = 120;
    public static final int MAX_SPACING = 6;

    /** How much chrome is drawn around the messages. */
    public enum Style {
        /** One box behind everything, with an optional header. */
        PANEL,
        /** A strip behind each message only, like vanilla chat. */
        LINES,
        /** Nothing but the text. */
        MINIMAL
    }

    public enum Theme {
        DARK(0x000000, 0x000000, 0xFFFFFF, 0xAAAAAA, false),
        LIGHT(0xFFFFFF, 0xD8D8E0, 0x18181B, 0x60606C, true),
        TWITCH(0x18181B, 0x772CE8, 0xEFEFF1, 0xADADB8, false);

        public final int background;
        public final int header;
        public final int text;
        /** Timestamps, notices and hints. */
        public final int muted;
        public final boolean light;

        Theme(int background, int header, int text, int muted, boolean light) {
            this.background = background;
            this.header = header;
            this.text = text;
            this.muted = muted;
            this.light = light;
        }
    }

    public boolean enabled = true;
    public String channel = "";
    /** Position and size as fractions of the screen, so the layout survives window resizes. */
    public double x = 0.01;
    public double y = 0.2;
    public double width = 0.3;
    public double height = 0.45;

    public Style style = Style.PANEL;
    public Theme theme = Theme.DARK;
    public double textScale = 1.0;
    public double backgroundOpacity = 0.5;
    public boolean showHeader = true;
    public boolean textShadow = true;
    public boolean coloredNames = true;
    /** Extra pixels between two messages. */
    public int messageSpacing = 1;

    /** Seconds a message stays visible, 0 keeps messages forever. */
    public int messageLifetime = 15;
    /** Hide the whole widget while no message is visible. */
    public boolean hideWhenEmpty = true;
    /** Bring faded messages back while the vanilla chat is open. */
    public boolean showAllInChat = true;
    public boolean showTimestamps = false;
    /** Subs, raids and other notices from Twitch. */
    public boolean showNotices = true;
    /** Drop messages starting with "!", which are usually bot commands. */
    public boolean hideCommands = false;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("twitch4forge.json");
    }

    public static ChatConfig load() {
        ChatConfig config = null;
        Path file = file();
        if (Files.exists(file)) {
            try {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ChatConfig.class);
            } catch (IOException | JsonParseException e) {
                ClientInit.LOGGER.warn("Could not read {}, using defaults", file, e);
            }
        }
        if (config == null) {
            config = new ChatConfig();
        }
        config.sanitize();
        return config;
    }

    public void save() {
        sanitize();
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ClientInit.LOGGER.warn("Could not save Twitch chat settings", e);
        }
    }

    public void resetLayout() {
        ChatConfig defaults = new ChatConfig();
        x = defaults.x;
        y = defaults.y;
        width = defaults.width;
        height = defaults.height;
    }

    /** Restores every default except the channel and whether the widget is on. */
    public void resetAll() {
        ChatConfig defaults = new ChatConfig();
        resetLayout();
        style = defaults.style;
        theme = defaults.theme;
        textScale = defaults.textScale;
        backgroundOpacity = defaults.backgroundOpacity;
        showHeader = defaults.showHeader;
        textShadow = defaults.textShadow;
        coloredNames = defaults.coloredNames;
        messageSpacing = defaults.messageSpacing;
        messageLifetime = defaults.messageLifetime;
        hideWhenEmpty = defaults.hideWhenEmpty;
        showAllInChat = defaults.showAllInChat;
        showTimestamps = defaults.showTimestamps;
        showNotices = defaults.showNotices;
        hideCommands = defaults.hideCommands;
    }

    private void sanitize() {
        channel = normalizeChannel(channel);
        x = clamp(x, 0, 1);
        y = clamp(y, 0, 1);
        width = clamp(width, 0.05, 1);
        height = clamp(height, 0.05, 1);
        // Gson leaves enums it does not recognise as null.
        if (style == null) {
            style = Style.PANEL;
        }
        if (theme == null) {
            theme = Theme.DARK;
        }
        textScale = clamp(textScale, MIN_TEXT_SCALE, MAX_TEXT_SCALE);
        backgroundOpacity = clamp(backgroundOpacity, 0, 1);
        messageSpacing = (int) clamp(messageSpacing, 0, MAX_SPACING);
        messageLifetime = (int) clamp(messageLifetime, 0, MAX_LIFETIME);
    }

    /** Widget rectangle in GUI pixels as {x, y, width, height}, always fully on screen. */
    public int[] rect(int screenWidth, int screenHeight) {
        int w = Math.min(screenWidth, Math.max(MIN_WIDTH, (int) Math.round(width * screenWidth)));
        int h = Math.min(screenHeight, Math.max(MIN_HEIGHT, (int) Math.round(height * screenHeight)));
        int px = Math.max(0, Math.min(screenWidth - w, (int) Math.round(x * screenWidth)));
        int py = Math.max(0, Math.min(screenHeight - h, (int) Math.round(y * screenHeight)));
        return new int[]{px, py, w, h};
    }

    /** Accepts "Name", "#name" or a twitch.tv link; returns the lowercase login or "" if invalid. */
    public static String normalizeChannel(String input) {
        if (input == null) {
            return "";
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        int slash = s.indexOf("twitch.tv/");
        if (slash >= 0) {
            s = s.substring(slash + "twitch.tv/".length());
        }
        int end = 0;
        if (s.startsWith("#") || s.startsWith("@")) {
            s = s.substring(1);
        }
        while (end < s.length() && (Character.isLetterOrDigit(s.charAt(end)) && s.charAt(end) < 128 || s.charAt(end) == '_')) {
            end++;
        }
        // Anything after the name must be a URL remainder, otherwise the input is not a channel.
        if (end < s.length() && "/?#".indexOf(s.charAt(end)) < 0) {
            return "";
        }
        s = s.substring(0, end);
        return s.length() <= 25 ? s : "";
    }

    private static double clamp(double v, double min, double max) {
        return Double.isNaN(v) ? min : Math.max(min, Math.min(max, v));
    }
}
