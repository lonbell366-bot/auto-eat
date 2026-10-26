package com.autoeat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class AutoEatScreen extends Screen {
    private final Screen parent;
    private final AutoEatConfig config = AutoEatClient.config;

    public AutoEatScreen(Screen parent) {
        super(Text.literal("Cai dat Auto Eat (DonutSMP)"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 32;

        this.addDrawableChild(new SliderWidget(centerX - 155, startY, 150, 20,
                Text.literal("Nguong doi: <= " + config.hungerThreshold), (config.hungerThreshold - 1) / 18.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Nguong doi: <= " + config.hungerThreshold + " (" + (config.hungerThreshold / 2.0) + " dui)"));
            }
            @Override
            protected void applyValue() {
                config.hungerThreshold = 1 + (int) Math.round(this.value * 18);
                config.save();
            }
        });

        this.addDrawableChild(ButtonWidget.builder(getPriorityText(), button -> {
            config.priorityMode = (config.priorityMode == AutoEatConfig.PriorityMode.HIGHEST)
                    ? AutoEatConfig.PriorityMode.LOWEST : AutoEatConfig.PriorityMode.HIGHEST;
            button.setMessage(getPriorityText());
            config.save();
        }).dimensions(centerX + 5, startY, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getSelectionModeText(), button -> {
            config.selectionMode = (config.selectionMode == AutoEatConfig.SelectionMode.AUTO)
                    ? AutoEatConfig.SelectionMode.PREFERRED_SLOT : AutoEatConfig.SelectionMode.AUTO;
            button.setMessage(getSelectionModeText());
            config.save();
        }).dimensions(centerX - 155, startY + 26, 150, 20).build());

        this.addDrawableChild(new SliderWidget(centerX + 5, startY + 26, 150, 20,
                Text.literal("O uu tien: So " + (config.preferredSlot + 1)), config.preferredSlot / 8.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("O uu tien: So " + (config.preferredSlot + 1)));
            }
            @Override
            protected void applyValue() {
                config.preferredSlot = (int) Math.round(this.value * 8);
                config.save();
            }
        });

        this.addDrawableChild(new SliderWidget(centerX - 155, startY + 52, 150, 20,
                Text.literal("Tre doi o: " + config.preEatDelay + " ticks"), config.preEatDelay / 10.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Tre doi o: " + config.preEatDelay + " ticks"));
            }
            @Override
            protected void applyValue() {
                config.preEatDelay = (int) Math.round(this.value * 10);
                config.save();
            }
        });

        this.addDrawableChild(new SliderWidget(centerX + 5, startY + 52, 150, 20,
                Text.literal("Tre quay lai: " + config.postEatDelay + " ticks"), config.postEatDelay / 10.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Tre quay lai: " + config.postEatDelay + " ticks"));
            }
            @Override
            protected void applyValue() {
                config.postEatDelay = (int) Math.round(this.value * 10);
                config.save();
            }
        });

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Hoan khi PvP: " + (config.pauseInCombat ? "§aBAT" : "§cTAT")), button -> {
                    config.pauseInCombat = !config.pauseInCombat;
                    button.setMessage(Text.literal("Hoan khi PvP: " + (config.pauseInCombat ? "§aBAT" : "§cTAT")));
                    config.save();
                }).dimensions(centerX - 155, startY + 78, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Hoan khi keo cung: " + (config.pauseWhenUsingItem ? "§aBAT" : "§cTAT")), button -> {
                    config.pauseWhenUsingItem = !config.pauseWhenUsingItem;
                    button.setMessage(Text.literal("Hoan khi keo cung: " + (config.pauseWhenUsingItem ? "§aBAT" : "§cTAT")));
                    config.save();
                }).dimensions(centerX + 5, startY + 78, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getFoodFilterText("Tao Vang", Items.GOLDEN_APPLE), button -> {
            config.toggleBlacklist(Items.GOLDEN_APPLE);
            config.toggleBlacklist(Items.ENCHANTED_GOLDEN_APPLE);
            button.setMessage(getFoodFilterText("Tao Vang", Items.GOLDEN_APPLE));
        }).dimensions(centerX - 155, startY + 115, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getFoodFilterText("Thit Thoi", Items.ROTTEN_FLESH), button -> {
            config.toggleBlacklist(Items.ROTTEN_FLESH);
            button.setMessage(getFoodFilterText("Thit Thoi", Items.ROTTEN_FLESH));
        }).dimensions(centerX + 5, startY + 115, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getFoodFilterText("Ca Noc & Mat Nhen", Items.PUFFERFISH), button -> {
            config.toggleBlacklist(Items.PUFFERFISH);
            config.toggleBlacklist(Items.SPIDER_EYE);
            button.setMessage(getFoodFilterText("Ca Noc & Mat Nhen", Items.PUFFERFISH));
        }).dimensions(centerX - 155, startY + 141, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getFoodFilterText("Qua Chorus", Items.CHORUS_FRUIT), button -> {
            config.toggleBlacklist(Items.CHORUS_FRUIT);
            button.setMessage(getFoodFilterText("Qua Chorus", Items.CHORUS_FRUIT));
        }).dimensions(centerX + 5, startY + 141, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Luu & Dong"), button -> this.close())
                .dimensions(centerX - 75, startY + 175, 150, 20).build());
    }

    private Text getPriorityText() {
        return Text.literal("Uu tien: " + (config.priorityMode == AutoEatConfig.PriorityMode.HIGHEST ? "§eHoi nhieu nhat" : "§bHoi it nhat"));
    }

    private Text getSelectionModeText() {
        return Text.literal("Che do: " + (config.selectionMode == AutoEatConfig.SelectionMode.AUTO ? "§aTu tim Hotbar" : "§dO uu tien"));
    }

    private Text getFoodFilterText(String name, Item item) {
        boolean blocked = config.isBlacklisted(item);
        return Text.literal(name + ": " + (blocked ? "§cChan" : "§aCho phep"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7--- Danh sach Cho phep / Chan do an ---"), this.width / 2, 134, 0xAAAAAA);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
                                             }
