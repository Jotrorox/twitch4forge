package com.twicht4forge.twitchchat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The only place the mod is configured: a small panel for the channel and look,
 * plus a live copy of the widget that is dragged to move and resize it.
 */
public final class ConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 164;
    private static final int HANDLE = 8;

    private enum Drag { NONE, MOVE, RESIZE }

    @Nullable
    private final Screen parent;
    private final ChatConfig cfg = TwitchChat.config();
    private EditBox channelBox;
    private Drag drag = Drag.NONE;
    private double grabX;
    private double grabY;

    public ConfigScreen(@Nullable Screen parent) {
        super(new TranslatableComponent("twitchchat.config.title"));
        this.parent = parent;
    }

    private int panelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return Math.max(0, (height - PANEL_HEIGHT) / 2);
    }

    @Override
    protected void init() {
        int left = panelLeft() + 10;
        int top = panelTop();
        int full = PANEL_WIDTH - 20;
        int half = (full - 4) / 2;

        String typed = channelBox == null ? cfg.channel : channelBox.getValue();
        channelBox = new EditBox(font, left, top + 34, full, 18, new TranslatableComponent("twitchchat.config.channel"));
        channelBox.setMaxLength(80);
        channelBox.setValue(typed);
        channelBox.setResponder(value -> cfg.channel = ChatConfig.normalizeChannel(value));
        addRenderableWidget(channelBox);

        addRenderableWidget(new Button(left, top + 58, half, 20, enabledLabel(), button -> {
            cfg.enabled = !cfg.enabled;
            button.setMessage(enabledLabel());
            TwitchChat.apply();
        }));
        addRenderableWidget(new Button(left + half + 4, top + 58, half, 20, headerLabel(), button -> {
            cfg.showHeader = !cfg.showHeader;
            button.setMessage(headerLabel());
        }));

        addRenderableWidget(new Slider(left, top + 82, half, "twitchchat.config.textScale",
                ChatConfig.MIN_TEXT_SCALE, ChatConfig.MAX_TEXT_SCALE, cfg.textScale,
                value -> cfg.textScale = Math.round(value * 20) / 20.0,
                value -> Math.round(value * 20) * 5 + "%"));
        addRenderableWidget(new Slider(left + half + 4, top + 82, half, "twitchchat.config.opacity",
                0, 1, cfg.backgroundOpacity,
                value -> cfg.backgroundOpacity = value,
                value -> Math.round(value * 100) + "%"));

        addRenderableWidget(new Button(left, top + 106, half, 20,
                new TranslatableComponent("twitchchat.config.reset"), button -> cfg.resetLayout()));
        addRenderableWidget(new Button(left + half + 4, top + 106, half, 20,
                CommonComponents.GUI_DONE, button -> onClose()));
    }

    private Component enabledLabel() {
        return new TranslatableComponent("twitchchat.config.enabled",
                cfg.enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private Component headerLabel() {
        return new TranslatableComponent("twitchchat.config.header",
                cfg.showHeader ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Override
    public void tick() {
        channelBox.tick();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderDirtBackground(0);
        }

        // Widget preview first, so the panel stays usable if the two overlap.
        int[] r = cfg.rect(width, height);
        ChatRenderer.INSTANCE.render(poseStack, font, r[0], r[1], r[2], r[3], cfg, true);
        int outline = cfg.enabled ? 0xFFFFFFFF : 0xFF808080;
        fill(poseStack, r[0], r[1], r[0] + r[2], r[1] + 1, outline);
        fill(poseStack, r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], outline);
        fill(poseStack, r[0], r[1], r[0] + 1, r[1] + r[3], outline);
        fill(poseStack, r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], outline);
        fill(poseStack, r[0] + r[2] - HANDLE, r[1] + r[3] - HANDLE, r[0] + r[2], r[1] + r[3], outline);

        int left = panelLeft();
        int top = panelTop();
        fill(poseStack, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101010);
        drawCenteredString(poseStack, font, title, width / 2, top + 8, 0xFFFFFF);
        drawString(poseStack, font, new TranslatableComponent("twitchchat.config.channel"), left + 10, top + 23, 0xA0A0A0);
        super.render(poseStack, mouseX, mouseY, partialTick);
        if (channelBox.getValue().isEmpty() && !channelBox.isFocused()) {
            drawString(poseStack, font, font.plainSubstrByWidth(
                    new TranslatableComponent("twitchchat.config.channel.hint").getString(), PANEL_WIDTH - 30),
                    left + 14, top + 39, 0x707070);
        }
        boolean invalid = !channelBox.getValue().trim().isEmpty() && cfg.channel.isEmpty();
        channelBox.setTextColor(invalid ? 0xFF5555 : 0xE0E0E0);

        drawCenteredString(poseStack, font, TwitchChat.status(), width / 2, top + 132, 0xFFFFFF);
        drawCenteredString(poseStack, font, new TranslatableComponent("twitchchat.config.drag"), width / 2, top + 146, 0xA0A0A0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && channelBox.isFocused()) {
            // Connect right away so the result is visible without leaving the screen.
            cfg.save();
            TwitchChat.apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int left = panelLeft();
        int top = panelTop();
        if (button != 0 || mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= top && mouseY < top + PANEL_HEIGHT) {
            return false;
        }
        int[] r = cfg.rect(width, height);
        if (mouseX < r[0] || mouseX >= r[0] + r[2] || mouseY < r[1] || mouseY >= r[1] + r[3]) {
            return false;
        }
        if (mouseX >= r[0] + r[2] - HANDLE && mouseY >= r[1] + r[3] - HANDLE) {
            drag = Drag.RESIZE;
            grabX = r[0] + r[2] - mouseX;
            grabY = r[1] + r[3] - mouseY;
        } else {
            drag = Drag.MOVE;
            grabX = mouseX - r[0];
            grabY = mouseY - r[1];
        }
        // Pin the stored values to what is on screen so the drag starts without a jump.
        cfg.x = r[0] / (double) width;
        cfg.y = r[1] / (double) height;
        cfg.width = r[2] / (double) width;
        cfg.height = r[3] / (double) height;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drag == Drag.NONE) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        int[] r = cfg.rect(width, height);
        if (drag == Drag.MOVE) {
            cfg.x = clamp(mouseX - grabX, 0, width - r[2]) / width;
            cfg.y = clamp(mouseY - grabY, 0, height - r[3]) / height;
        } else {
            cfg.width = clamp(mouseX + grabX - r[0], ChatConfig.MIN_WIDTH, width - r[0]) / width;
            cfg.height = clamp(mouseY + grabY - r[1], ChatConfig.MIN_HEIGHT, height - r[1]) / height;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        drag = Drag.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        cfg.save();
        TwitchChat.apply();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    private static final class Slider extends AbstractSliderButton {
        private final String key;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;
        private final DoubleFunction<String> format;

        Slider(int x, int y, int width, String key, double min, double max, double current,
               DoubleConsumer setter, DoubleFunction<String> format) {
            super(x, y, width, 20, Component.nullToEmpty(""), (current - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.format = format;
            updateMessage();
        }

        private double real() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(new TranslatableComponent(key, format.apply(real())));
        }

        @Override
        protected void applyValue() {
            setter.accept(real());
        }
    }
}
