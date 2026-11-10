package com.twicht4forge.twitchchat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The only place the mod is configured: a tabbed panel for the settings, plus a live copy
 * of the widget that is dragged to move and resize it. The panel docks to whichever side
 * of the screen the widget is not on, so the two stay out of each other's way.
 */
public final class ConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 224;
    private static final int PANEL_HEIGHT = 224;
    private static final int MARGIN = 8;
    private static final int ROW = 22;
    private static final int ROWS_TOP = 46;
    private static final int HANDLE = 8;
    private static final int ACCENT = 0xFF9146FF;

    private enum Drag { NONE, MOVE, RESIZE }

    private enum Tab { GENERAL, LOOK, MESSAGES }

    @Nullable
    private final Screen parent;
    private final ChatConfig cfg = TwitchChat.config();
    private final Map<AbstractWidget, Component> tooltips = new LinkedHashMap<>();
    private Tab tab = Tab.GENERAL;
    private String typedChannel = cfg.channel;
    @Nullable
    private EditBox channelBox;
    private boolean dockedRight;
    private Drag drag = Drag.NONE;
    private double grabX;
    private double grabY;

    public ConfigScreen(@Nullable Screen parent) {
        super(new TranslatableComponent("twitchchat.config.title"));
        this.parent = parent;
    }

    private boolean widgetOnLeft() {
        int[] r = cfg.rect(width, height);
        return r[0] + r[2] / 2 < width / 2;
    }

    private int panelLeft() {
        return dockedRight ? Math.max(0, width - PANEL_WIDTH - MARGIN) : Math.min(MARGIN, Math.max(0, width - PANEL_WIDTH));
    }

    private int panelTop() {
        return Math.max(0, (height - PANEL_HEIGHT) / 2);
    }

    private int rowY(int row) {
        return panelTop() + ROWS_TOP + row * ROW;
    }

    private void rebuild() {
        clearWidgets();
        setFocused(null);
        init();
    }

    @Override
    protected void init() {
        tooltips.clear();
        channelBox = null;
        dockedRight = widgetOnLeft();

        int left = panelLeft() + MARGIN;
        int top = panelTop();
        int full = PANEL_WIDTH - 2 * MARGIN;
        int half = (full - 4) / 2;
        int right = left + half + 4;

        Tab[] tabs = Tab.values();
        int tabWidth = full / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            addRenderableWidget(new TabButton(left + i * tabWidth, top + 22, tabWidth, tabs[i]));
        }

        switch (tab) {
            case GENERAL -> {
                channelBox = new EditBox(font, left + 1, rowY(1) + 1, full - 66, 18,
                        new TranslatableComponent("twitchchat.config.channel"));
                channelBox.setMaxLength(80);
                channelBox.setValue(typedChannel);
                channelBox.setResponder(value -> {
                    typedChannel = value;
                    cfg.channel = ChatConfig.normalizeChannel(value);
                });
                addRenderableWidget(channelBox);
                if (typedChannel.isEmpty()) {
                    setInitialFocus(channelBox);
                }
                tip(addRenderableWidget(new Button(left + full - 60, rowY(1), 60, 20,
                        new TranslatableComponent("twitchchat.config.connect"), button -> connect())),
                        "twitchchat.config.connect");
                toggle(left, rowY(2), full, "twitchchat.config.enabled", () -> cfg.enabled, value -> {
                    cfg.enabled = value;
                    TwitchChat.apply();
                });
                action(left, rowY(3), half, "twitchchat.config.reset", cfg::resetLayout);
                action(right, rowY(3), half, "twitchchat.config.resetAll", cfg::resetAll);
            }
            case LOOK -> {
                boolean minimal = cfg.style == ChatConfig.Style.MINIMAL;
                boolean panel = cfg.style == ChatConfig.Style.PANEL;
                cycle(left, rowY(0), full, "twitchchat.config.style", cfg.style, ChatConfig.Style.values(),
                        value -> cfg.style = value);
                // Without a background there is nothing for a theme to colour.
                cycle(left, rowY(1), full, "twitchchat.config.theme", cfg.theme, ChatConfig.Theme.values(),
                        value -> cfg.theme = value).active = !minimal;
                slider(left, rowY(2), half, "twitchchat.config.textScale",
                        ChatConfig.MIN_TEXT_SCALE, ChatConfig.MAX_TEXT_SCALE, cfg.textScale,
                        value -> cfg.textScale = Math.round(value * 20) / 20.0,
                        value -> Math.round(value * 20) * 5 + "%");
                slider(right, rowY(2), half, "twitchchat.config.spacing",
                        0, ChatConfig.MAX_SPACING, cfg.messageSpacing,
                        value -> cfg.messageSpacing = (int) Math.round(value),
                        value -> Math.round(value) + "px");
                slider(left, rowY(3), full, "twitchchat.config.opacity",
                        0, 1, cfg.backgroundOpacity,
                        value -> cfg.backgroundOpacity = value,
                        value -> Math.round(value * 100) + "%").active = !minimal;
                toggle(left, rowY(4), half, "twitchchat.config.header", () -> cfg.showHeader,
                        value -> cfg.showHeader = value).active = panel;
                // Dark text on a light background only gets blurry with a shadow.
                toggle(right, rowY(4), half, "twitchchat.config.shadow", () -> cfg.textShadow,
                        value -> cfg.textShadow = value).active = minimal || !cfg.theme.light;
                toggle(left, rowY(5), full, "twitchchat.config.nameColors", () -> cfg.coloredNames,
                        value -> cfg.coloredNames = value);
            }
            case MESSAGES -> {
                slider(left, rowY(0), full, "twitchchat.config.lifetime",
                        0, ChatConfig.MAX_LIFETIME, cfg.messageLifetime,
                        value -> cfg.messageLifetime = (int) Math.round(value / 5) * 5,
                        value -> Math.round(value / 5) == 0 ? I18n.get("twitchchat.config.lifetime.never")
                                : I18n.get("twitchchat.config.lifetime.seconds", Math.round(value / 5) * 5));
                toggle(left, rowY(1), full, "twitchchat.config.hideWhenEmpty", () -> cfg.hideWhenEmpty,
                        value -> cfg.hideWhenEmpty = value);
                toggle(left, rowY(2), full, "twitchchat.config.showAllInChat", () -> cfg.showAllInChat,
                        value -> cfg.showAllInChat = value);
                toggle(left, rowY(3), half, "twitchchat.config.timestamps", () -> cfg.showTimestamps,
                        value -> cfg.showTimestamps = value);
                toggle(right, rowY(3), half, "twitchchat.config.notices", () -> cfg.showNotices,
                        value -> cfg.showNotices = value);
                toggle(left, rowY(4), full, "twitchchat.config.hideCommands", () -> cfg.hideCommands,
                        value -> cfg.hideCommands = value);
            }
        }

        addRenderableWidget(new Button(left, top + PANEL_HEIGHT - 28, full, 20,
                CommonComponents.GUI_DONE, button -> onClose()));
    }

    private <T extends AbstractWidget> T tip(T widget, String key) {
        tooltips.put(widget, new TranslatableComponent(key + ".tip"));
        return widget;
    }

    private Button toggle(int x, int y, int w, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        Component label = new TranslatableComponent(key,
                getter.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
        return tip(addRenderableWidget(new Button(x, y, w, 20, label, button -> {
            setter.accept(!getter.getAsBoolean());
            rebuild();
        })), key);
    }

    private <E extends Enum<E>> Button cycle(int x, int y, int w, String key, E current, E[] values, Consumer<E> setter) {
        Component label = new TranslatableComponent(key,
                new TranslatableComponent(key + "." + current.name().toLowerCase(Locale.ROOT)));
        return tip(addRenderableWidget(new Button(x, y, w, 20, label, button -> {
            setter.accept(values[(current.ordinal() + 1) % values.length]);
            rebuild();
        })), key);
    }

    private void action(int x, int y, int w, String key, Runnable run) {
        tip(addRenderableWidget(new Button(x, y, w, 20, new TranslatableComponent(key), button -> {
            run.run();
            rebuild();
        })), key);
    }

    private Slider slider(int x, int y, int w, String key, double min, double max, double current,
                          DoubleConsumer setter, DoubleFunction<String> format) {
        return tip(addRenderableWidget(new Slider(x, y, w, key, min, max, current, setter, format)), key);
    }

    /** Connects right away so the result is visible without leaving the screen. */
    private void connect() {
        cfg.save();
        TwitchChat.apply();
    }

    @Override
    public void tick() {
        if (channelBox != null) {
            channelBox.tick();
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderDirtBackground(0);
        }

        // Widget preview first, so the panel stays usable if the two overlap.
        int[] r = cfg.rect(width, height);
        ChatRenderer.INSTANCE.render(poseStack, font, r[0], r[1], r[2], r[3], cfg, true, true);
        int outline = !cfg.enabled ? 0xFF808080 : drag != Drag.NONE ? ACCENT : 0xFFFFFFFF;
        fill(poseStack, r[0], r[1], r[0] + r[2], r[1] + 1, outline);
        fill(poseStack, r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], outline);
        fill(poseStack, r[0], r[1], r[0] + 1, r[1] + r[3], outline);
        fill(poseStack, r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], outline);
        fill(poseStack, r[0] + r[2] - HANDLE, r[1] + r[3] - HANDLE, r[0] + r[2], r[1] + r[3], outline);

        int left = panelLeft();
        int top = panelTop();
        int inner = PANEL_WIDTH - 2 * MARGIN;
        fill(poseStack, left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF2E2E38);
        fill(poseStack, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0101014);
        fill(poseStack, left, top, left + PANEL_WIDTH, top + 2, ACCENT);
        drawString(poseStack, font, title, left + MARGIN, top + 9, 0xFFFFFF);
        fill(poseStack, left + MARGIN, top + 38, left + PANEL_WIDTH - MARGIN, top + 39, 0xFF2E2E38);

        if (tab == Tab.GENERAL) {
            drawString(poseStack, font, new TranslatableComponent("twitchchat.config.channel"),
                    left + MARGIN, rowY(0) + 10, 0xA0A0A0);
            int y = rowY(4) + 4;
            for (FormattedCharSequence line : font.split(new TranslatableComponent("twitchchat.config.drag",
                    ClientInit.OPEN_CONFIG.getTranslatedKeyMessage()), inner)) {
                font.draw(poseStack, line, left + MARGIN, y, 0xA0A0A0);
                y += 10;
            }
        }

        super.render(poseStack, mouseX, mouseY, partialTick);

        if (channelBox != null) {
            if (channelBox.getValue().isEmpty() && !channelBox.isFocused()) {
                drawString(poseStack, font, font.plainSubstrByWidth(
                        I18n.get("twitchchat.config.channel.hint"), channelBox.getWidth() - 8),
                        channelBox.x + 4, channelBox.y + 5, 0x707070);
            }
            boolean invalid = !channelBox.getValue().trim().isEmpty() && cfg.channel.isEmpty();
            channelBox.setTextColor(invalid ? 0xFF5555 : 0xE0E0E0);
        }

        // Connection state stays visible on every tab.
        int statusY = top + PANEL_HEIGHT - 42;
        fill(poseStack, left + MARGIN, statusY + 2, left + MARGIN + 4, statusY + 6, TwitchChat.stateColor());
        drawString(poseStack, font, font.plainSubstrByWidth(TwitchChat.status().getString(), inner - 8),
                left + MARGIN + 8, statusY, 0xD0D0D0);

        if (drag == Drag.NONE) {
            for (Map.Entry<AbstractWidget, Component> entry : tooltips.entrySet()) {
                AbstractWidget w = entry.getKey();
                if (mouseX >= w.x && mouseX < w.x + w.getWidth() && mouseY >= w.y && mouseY < w.y + w.getHeight()) {
                    List<FormattedCharSequence> lines = font.split(entry.getValue(), 180);
                    renderTooltip(poseStack, lines, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && channelBox != null && channelBox.isFocused()) {
            connect();
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
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            // Only move the panel once the drag is over, so it never jumps under the cursor.
            if (dockedRight != widgetOnLeft()) {
                rebuild();
            }
            return true;
        }
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

    private final class TabButton extends Button {
        private final Tab target;

        TabButton(int x, int y, int width, Tab target) {
            super(x, y, width, 16, new TranslatableComponent("twitchchat.config.tab." + target.name().toLowerCase(Locale.ROOT)),
                    button -> {
                        tab = target;
                        rebuild();
                    });
            this.target = target;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            boolean selected = tab == target;
            fill(poseStack, x, y, x + width, y + height, selected ? 0xFF2A2A34 : isHovered ? 0xFF1E1E26 : 0x00000000);
            if (selected) {
                fill(poseStack, x, y + height - 2, x + width, y + height, ACCENT);
            }
            drawCenteredString(poseStack, font, getMessage(), x + width / 2, y + 4,
                    selected ? 0xFFFFFF : isHovered ? 0xD0D0D0 : 0xA0A0A0);
        }
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
