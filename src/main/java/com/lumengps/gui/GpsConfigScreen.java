package com.lumengps.gui;

import com.lumengps.data.GpsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom configuration screen for LumenGPS settings.
 */
public class GpsConfigScreen extends Screen {
    private final Screen parent;

    public GpsConfigScreen(Screen parent) {
        super(Component.literal("Configurações LumenGPS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        GpsConfig config = GpsConfig.getInstance();
        int centerX = this.width / 2;
        int y = 40;
        int spacing = 24;

        // Toggle HUD
        this.addRenderableWidget(CycleButton.onOffBuilder(config.showHud)
                .create(centerX - 100, y, 200, 20, Component.literal("Mostrar HUD"), (btn, value) -> {
                    config.showHud = value;
                    config.save();
                }));

        y += spacing;
        // Toggle Intelligent Mode
        this.addRenderableWidget(CycleButton.onOffBuilder(config.intelligentMode)
                .create(centerX - 100, y, 200, 20, Component.literal("Modo Inteligente"), (btn, value) -> {
                    config.intelligentMode = value;
                    config.save();
                }));

        y += spacing;
        // Allow Water
        this.addRenderableWidget(CycleButton.onOffBuilder(config.allowWater)
                .create(centerX - 100, y, 200, 20, Component.literal("Andar na Água"), (btn, value) -> {
                    config.allowWater = value;
                    config.save();
                }));

        y += spacing;
        // Allow Lava
        this.addRenderableWidget(CycleButton.onOffBuilder(config.allowLava)
                .create(centerX - 100, y, 200, 20, Component.literal("Andar na Lava"), (btn, value) -> {
                    config.allowLava = value;
                    config.save();
                }));

        y += spacing;
        // Death Waypoint
        this.addRenderableWidget(CycleButton.onOffBuilder(config.enableDeathWaypoint)
                .create(centerX - 100, y, 200, 20, Component.literal("Waypoint de Morte"), (btn, value) -> {
                    config.enableDeathWaypoint = value;
                    config.save();
                }));

        y += spacing;
        // Light Pillar
        this.addRenderableWidget(CycleButton.onOffBuilder(config.enableLightPillar)
                .create(centerX - 100, y, 200, 20, Component.literal("Pilar de Luz"), (btn, value) -> {
                    config.enableLightPillar = value;
                    config.save();
                }));

        y += spacing;
        // Require Compass
        this.addRenderableWidget(CycleButton.onOffBuilder(config.requireCompass)
                .create(centerX - 100, y, 200, 20, Component.literal("Apenas com Bússola"), (btn, value) -> {
                    config.requireCompass = value;
                    config.save();
                }));

        y += spacing;
        // Confirm Overwrite
        this.addRenderableWidget(CycleButton.onOffBuilder(config.confirmOverwrite)
                .create(centerX - 100, y, 200, 20, Component.literal("Confirmar Sobrescrita"), (btn, value) -> {
                    config.confirmOverwrite = value;
                    config.save();
                }));

        y += 30;
        // Back Button
        this.addRenderableWidget(Button.builder(Component.literal("Pronto"), (btn) -> {
            this.minecraft.gui.setScreen(this.parent);
        }).bounds(centerX - 100, y, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.extractMenuBackground(context);
        context.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
