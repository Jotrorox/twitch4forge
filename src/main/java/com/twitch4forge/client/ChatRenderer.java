package com.twitch4forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.twitch4forge.irc.ChatMessage;
import com.twitch4forge.irc.TwitchIrcClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Draws the chat widget; shared by the HUD overlay and the preview in the settings screen. */
public final class ChatRenderer {
    public static final ChatRenderer INSTANCE = new ChatRenderer();
    private static final int HEADER_HEIGHT = 12;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;
    /** How long a message takes to fade out once its lifetime is over. */
    private static final float FADE_MS = 700;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final List<ChatMessage> SAMPLE = sample();

    /**
     * One wrapped line of a message.
     *
     * @param offset distance from the bottom of the text area to the top of this line, in unscaled pixels
     */
    private record Line(FormattedCharSequence text, long time, int offset) {
    }

    // Wrapped lines are cached until the messages, the layout or the look change.
    private int cachedVersion = Integer.MIN_VALUE;
    private int cachedWrap;
    private int cachedSpace;
    private int cachedLook;
    private boolean cachedSample;
    private List<Line> cachedLines = List.of();

    private ChatRenderer() {
    }

    private static List<ChatMessage> sample() {
        long now = System.currentTimeMillis();
        return List.of(
                new ChatMessage("", "", "Steve", 0x1E90FF, "This is what the chat will look like", false, false, now),
                new ChatMessage("", "", "Alex", 0xFF7F50, "Longer messages wrap onto the next line when they do not fit the widget", false, false, now),
                new ChatMessage("", "", "", 0xAAAAAA, "Notices such as subs and raids look like this", false, true, now),
                new ChatMessage("", "", "Enderman", 0x0000FF, "!commands can be hidden", false, false, now),
                new ChatMessage("", "", "Creeper", 0x00C000, "sssss", true, false, now));
    }

    /**
     * @param preview true in the settings screen: shows sample messages while the chat is empty and never hides anything
     * @param showAll true to ignore the message lifetime, e.g. while the vanilla chat is open
     */
    public void render(PoseStack poseStack, Font font, int x, int y, int w, int h, ChatConfig cfg,
                       boolean preview, boolean showAll) {
        ChatConfig.Style style = cfg.style;
        boolean minimal = style == ChatConfig.Style.MINIMAL;
        // Without a background the text sits on the world, where only light text with a shadow is readable.
        ChatConfig.Theme theme = minimal ? ChatConfig.Theme.DARK : cfg.theme;
        boolean shadow = cfg.textShadow && !theme.light;
        int header = style == ChatConfig.Style.PANEL && cfg.showHeader ? HEADER_HEIGHT : 0;

        float scale = (float) cfg.textScale;
        int wrap = Math.max(20, (int) ((w - 2 * PADDING) / scale));
        int space = (int) ((h - header - 2 * PADDING) / scale);
        List<Line> lines = lines(font, wrap, space, cfg, theme, preview);

        long now = System.currentTimeMillis();
        long lifetime = preview || showAll ? 0 : cfg.messageLifetime * 1000L;
        float strongest = 0;
        for (Line line : lines) {
            strongest = Math.max(strongest, alpha(line, now, lifetime));
        }

        Component hint = null;
        if (lines.isEmpty() && !preview) {
            if (TwitchChat.connectedChannel().isEmpty()) {
                hint = new TranslatableComponent("twitch4forge.widget.nochannel", ClientInit.OPEN_CONFIG.getTranslatedKeyMessage());
            } else if (!cfg.hideWhenEmpty) {
                hint = TwitchChat.state() == TwitchIrcClient.State.CONNECTED
                        ? new TranslatableComponent("twitch4forge.widget.waiting") : TwitchChat.status();
            }
        }

        // The frame follows the last message out, unless it is meant to stay.
        float frame = preview || hint != null || !cfg.hideWhenEmpty ? 1 : strongest;
        if (frame <= 0) {
            return;
        }

        if (style == ChatConfig.Style.PANEL) {
            GuiComponent.fill(poseStack, x, y, x + w, y + h, argb(theme.background, cfg.backgroundOpacity * frame));
            if (header > 0) {
                GuiComponent.fill(poseStack, x, y, x + w, y + header,
                        argb(theme.header, Math.min(1, cfg.backgroundOpacity + 0.25) * frame));
                GuiComponent.fill(poseStack, x + PADDING, y + 4, x + PADDING + 4, y + 8,
                        argb(TwitchChat.stateColor(), frame));
                String channel = TwitchChat.connectedChannel();
                String title = font.plainSubstrByWidth(channel.isEmpty() ? "Twitch" : "#" + channel, w - 2 * PADDING - 7);
                draw(poseStack, font, FormattedCharSequence.forward(title, Style.EMPTY), x + PADDING + 7, y + 2,
                        theme.text, frame, shadow);
            }
        }

        RenderSystem.enableBlend();
        poseStack.pushPose();
        // Newest message sits at the bottom edge, like Twitch's own chat.
        poseStack.translate(x + PADDING, y + h - PADDING, 0);
        poseStack.scale(scale, scale, 1);
        if (hint != null) {
            List<FormattedCharSequence> wrapped = font.split(hint, wrap);
            int count = Math.min(wrapped.size(), space / LINE_HEIGHT);
            for (int i = 0; i < count; i++) {
                int top = (i - count) * LINE_HEIGHT;
                strip(poseStack, style, theme, cfg, wrap, top, 1);
                draw(poseStack, font, wrapped.get(i), 0, top + 1, theme.muted, 1, shadow);
            }
        } else {
            for (Line line : lines) {
                float alpha = alpha(line, now, lifetime);
                if (alpha > 0) {
                    strip(poseStack, style, theme, cfg, wrap, -line.offset, alpha);
                    draw(poseStack, font, line.text, 0, 1 - line.offset, theme.text, alpha, shadow);
                }
            }
        }
        poseStack.popPose();
    }

    private static float alpha(Line line, long now, long lifetime) {
        long age = now - line.time;
        if (lifetime <= 0 || age <= lifetime) {
            return 1;
        }
        return Math.max(0, 1 - (age - lifetime) / FADE_MS);
    }

    /** Background behind a single line, for the style that has no box. */
    private static void strip(PoseStack poseStack, ChatConfig.Style style, ChatConfig.Theme theme, ChatConfig cfg,
                              int wrap, int top, float alpha) {
        if (style == ChatConfig.Style.LINES) {
            GuiComponent.fill(poseStack, -2, top, wrap + 2, top + LINE_HEIGHT,
                    argb(theme.background, cfg.backgroundOpacity * alpha));
        }
    }

    private static void draw(PoseStack poseStack, Font font, FormattedCharSequence text, int x, int y,
                             int rgb, float alpha, boolean shadow) {
        int a = Math.round(alpha * 255);
        // The font treats an alpha this low as "no alpha given" and would draw the text fully opaque.
        if (a < 4) {
            return;
        }
        if (shadow) {
            font.drawShadow(poseStack, text, x, y, a << 24 | rgb);
        } else {
            font.draw(poseStack, text, x, y, a << 24 | rgb);
        }
    }

    private static int argb(int rgb, double alpha) {
        return (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | rgb & 0xFFFFFF;
    }

    /** Newest first, limited to what fits into {@code space} unscaled pixels. */
    private List<Line> lines(Font font, int wrap, int space, ChatConfig cfg, ChatConfig.Theme theme, boolean preview) {
        int version = TwitchChat.LOG.version();
        boolean sample = preview && TwitchChat.LOG.isEmpty();
        int look = Objects.hash(theme, cfg.showTimestamps, cfg.showNotices, cfg.hideCommands, cfg.coloredNames,
                cfg.messageSpacing);
        if (version == cachedVersion && wrap == cachedWrap && space == cachedSpace && look == cachedLook
                && sample == cachedSample) {
            return cachedLines;
        }
        List<ChatMessage> messages = sample ? SAMPLE : TwitchChat.LOG.snapshot();

        // Walk back from the newest message and stop as soon as the box is full.
        List<Line> lines = new ArrayList<>();
        int used = 0;
        fill:
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.system() ? !cfg.showNotices : cfg.hideCommands && message.text().startsWith("!")) {
                continue;
            }
            List<FormattedCharSequence> block = font.split(format(message, cfg, theme), wrap);
            for (int j = block.size() - 1; j >= 0; j--) {
                used += LINE_HEIGHT;
                if (used > space) {
                    break fill;
                }
                lines.add(new Line(block.get(j), message.time(), used));
            }
            used += cfg.messageSpacing;
        }

        cachedVersion = version;
        cachedWrap = wrap;
        cachedSpace = space;
        cachedLook = look;
        cachedSample = sample;
        cachedLines = lines;
        return lines;
    }

    private static Component format(ChatMessage message, ChatConfig cfg, ChatConfig.Theme theme) {
        Style muted = Style.EMPTY.withColor(TextColor.fromRgb(theme.muted));
        MutableComponent line = new TextComponent("");
        if (cfg.showTimestamps) {
            line.append(new TextComponent(TIME.format(Instant.ofEpochMilli(message.time())) + " ").withStyle(muted));
        }
        if (message.system()) {
            return line.append(new TextComponent(message.text()).withStyle(muted.withItalic(true)));
        }
        Style name = cfg.coloredNames
                ? Style.EMPTY.withColor(TextColor.fromRgb(readable(message.color(), theme.light)))
                : Style.EMPTY;
        line.append(new TextComponent(message.author()).withStyle(name));
        if (message.action()) {
            return line.append(new TextComponent(" " + message.text()).withStyle(name.withItalic(true)));
        }
        return line.append(new TextComponent(": " + message.text()));
    }

    /** Pulls name colours that would vanish into the background towards white, or towards black on a light theme. */
    private static int readable(int rgb, boolean lightBackground) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        if (lightBackground) {
            if (luminance > 120) {
                double keep = 120 / luminance;
                r = (int) (r * keep);
                g = (int) (g * keep);
                b = (int) (b * keep);
            }
        } else if (luminance < 100) {
            double mix = (100 - luminance) / (255 - luminance);
            r += (int) ((255 - r) * mix);
            g += (int) ((255 - g) * mix);
            b += (int) ((255 - b) * mix);
        }
        return r << 16 | g << 8 | b;
    }
}
