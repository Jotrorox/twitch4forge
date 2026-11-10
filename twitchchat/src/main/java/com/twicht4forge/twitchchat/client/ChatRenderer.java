package com.twicht4forge.twitchchat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.twicht4forge.twitchchat.irc.ChatMessage;
import com.twicht4forge.twitchchat.irc.TwitchIrcClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Draws the chat box; shared by the HUD overlay and the preview in the settings screen. */
public final class ChatRenderer {
    public static final ChatRenderer INSTANCE = new ChatRenderer();
    private static final int HEADER_HEIGHT = 12;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;
    private static final List<ChatMessage> SAMPLE = List.of(
            new ChatMessage("", "", "Steve", 0x1E90FF, "This is what the chat will look like", false, false),
            new ChatMessage("", "", "Alex", 0xFF7F50, "Longer messages wrap onto the next line when they do not fit the widget", false, false),
            ChatMessage.system("Notices such as subs and raids look like this"),
            new ChatMessage("", "", "Creeper", 0x00C000, "sssss", true, false));

    // Wrapped lines are cached until the messages or the layout change.
    private int cachedVersion = Integer.MIN_VALUE;
    private int cachedWrap;
    private int cachedMaxLines;
    private boolean cachedSample;
    private List<FormattedCharSequence> cachedLines = List.of();

    private ChatRenderer() {
    }

    /** @param preview true in the settings screen: shows sample messages while the chat is empty */
    public void render(PoseStack poseStack, Font font, int x, int y, int w, int h, ChatConfig cfg, boolean preview) {
        int alpha = (int) Math.round(cfg.backgroundOpacity * 255);
        GuiComponent.fill(poseStack, x, y, x + w, y + h, alpha << 24);

        int top = y;
        if (cfg.showHeader) {
            GuiComponent.fill(poseStack, x, y, x + w, y + HEADER_HEIGHT, Math.min(255, alpha + 60) << 24);
            TwitchIrcClient.State state = TwitchChat.state();
            int dot = state == TwitchIrcClient.State.CONNECTED ? 0xFF40D040
                    : state == null ? 0xFF808080 : 0xFFE0B020;
            GuiComponent.fill(poseStack, x + PADDING, y + 4, x + PADDING + 4, y + 8, dot);
            String channel = TwitchChat.connectedChannel();
            String title = channel.isEmpty() ? "Twitch" : "#" + channel;
            font.drawShadow(poseStack, font.plainSubstrByWidth(title, w - 2 * PADDING - 7), x + PADDING + 7, y + 2, 0xFFFFFF);
            top += HEADER_HEIGHT;
        }

        float scale = (float) cfg.textScale;
        int wrap = Math.max(20, (int) ((w - 2 * PADDING) / scale));
        int maxLines = (int) ((y + h - top - 2 * PADDING) / (LINE_HEIGHT * scale));
        if (maxLines <= 0) {
            return;
        }

        List<FormattedCharSequence> lines = lines(font, wrap, maxLines, preview);
        if (lines.isEmpty()) {
            Component hint = TwitchChat.connectedChannel().isEmpty()
                    ? new TranslatableComponent("twitchchat.widget.nochannel", ClientInit.OPEN_CONFIG.getTranslatedKeyMessage())
                    : new TranslatableComponent("twitchchat.widget.waiting");
            lines = font.split(hint.copy().withStyle(ChatFormatting.GRAY), wrap);
            if (lines.size() > maxLines) {
                lines = lines.subList(0, maxLines);
            }
        }

        // Newest message sits at the bottom edge, like Twitch's own chat.
        poseStack.pushPose();
        poseStack.translate(x + PADDING, y + h - PADDING - lines.size() * LINE_HEIGHT * scale, 0);
        poseStack.scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) {
            font.drawShadow(poseStack, lines.get(i), 0, i * LINE_HEIGHT + 1, 0xFFFFFF);
        }
        poseStack.popPose();
    }

    private List<FormattedCharSequence> lines(Font font, int wrap, int maxLines, boolean preview) {
        int version = TwitchChat.LOG.version();
        boolean sample = preview && TwitchChat.LOG.isEmpty();
        if (version == cachedVersion && wrap == cachedWrap && maxLines == cachedMaxLines && sample == cachedSample) {
            return cachedLines;
        }
        List<ChatMessage> messages = sample ? SAMPLE : TwitchChat.LOG.snapshot();

        // Walk back from the newest message and stop as soon as the box is full.
        List<List<FormattedCharSequence>> blocks = new ArrayList<>();
        int count = 0;
        for (int i = messages.size() - 1; i >= 0 && count < maxLines; i--) {
            List<FormattedCharSequence> block = font.split(format(messages.get(i)), wrap);
            blocks.add(block);
            count += block.size();
        }
        Collections.reverse(blocks);
        List<FormattedCharSequence> lines = new ArrayList<>(count);
        for (List<FormattedCharSequence> block : blocks) {
            lines.addAll(block);
        }
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
        }

        cachedVersion = version;
        cachedWrap = wrap;
        cachedMaxLines = maxLines;
        cachedSample = sample;
        cachedLines = lines;
        return lines;
    }

    private static Component format(ChatMessage message) {
        if (message.system()) {
            return new TextComponent(message.text()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        Style nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(message.color()));
        MutableComponent line = new TextComponent(message.author()).withStyle(nameStyle);
        if (message.action()) {
            return line.append(new TextComponent(" " + message.text()).withStyle(nameStyle.withItalic(true)));
        }
        return line.append(new TextComponent(": " + message.text()).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
    }
}
