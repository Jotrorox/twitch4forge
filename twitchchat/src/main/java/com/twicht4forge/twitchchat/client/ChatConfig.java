package com.twicht4forge.twitchchat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Widget settings, stored in config/twitchchat.json and edited through {@link ConfigScreen}. */
public final class ChatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MIN_WIDTH = 80;
    public static final int MIN_HEIGHT = 40;
    public static final double MIN_TEXT_SCALE = 0.5;
    public static final double MAX_TEXT_SCALE = 2.0;

    public boolean enabled = true;
    public String channel = "";
    /** Position and size as fractions of the screen, so the layout survives window resizes. */
    public double x = 0.01;
    public double y = 0.2;
    public double width = 0.3;
    public double height = 0.45;
    public double textScale = 1.0;
    public double backgroundOpacity = 0.5;
    public boolean showHeader = true;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("twitchchat.json");
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

    private void sanitize() {
        channel = normalizeChannel(channel);
        x = clamp(x, 0, 1);
        y = clamp(y, 0, 1);
        width = clamp(width, 0.05, 1);
        height = clamp(height, 0.05, 1);
        textScale = clamp(textScale, MIN_TEXT_SCALE, MAX_TEXT_SCALE);
        backgroundOpacity = clamp(backgroundOpacity, 0, 1);
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
