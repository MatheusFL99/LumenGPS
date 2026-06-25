package com.lumengps.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Handles the on-screen HUD rendering for the active waypoint.
 * Implementation for Minecraft 26.1.x using the new HudElement system.
 */
public class GpsHud implements HudElement {

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("lumengps", "hud"), new GpsHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;

        GpsRenderer renderer = GpsRenderer.getInstance();
        if (!renderer.isActive() || !com.lumengps.data.GpsConfig.getInstance().showHud) return;

        String name = renderer.getTargetName();
        Vec3 target = renderer.getTargetPos();
        if (name == null || target == null) return;

        double distance = client.player.position().distanceTo(target);
        
        // Don't show HUD if we are practically there
        if (distance < 5.0) return;

        // In 26.1.x, rotation is retrieved via getVisualRotationYInDegrees()
        String arrow = getArrow(client.player.getVisualRotationYInDegrees(), client.player.position(), target);
        String elytraTag = renderer.isElytra() ? " §d[ELYTRA]§r" : "";
        Component text = Component.literal("§b" + name + "§r [" + (int) distance + "m] §e" + arrow + "§r" + elytraTag);
        
        Font font = client.font;
        int y = 10;
        int screenWidth = client.getWindow().getGuiScaledWidth();

        // Use textWithBackdrop for the semi-transparent background + shadow text
        context.textWithBackdrop(font, text, (screenWidth - font.width(text)) / 2, y, 0xFFFFFF, 0x90000000);
    }

    private static String getArrow(float playerYaw, Vec3 playerPos, Vec3 targetPos) {
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        
        double relativeAngle = (angleToTarget - playerYaw) % 360;
        if (relativeAngle < 0) relativeAngle += 360;

        if (relativeAngle <= 22.5 || relativeAngle > 337.5) return "↑";
        if (relativeAngle <= 67.5) return "↗";
        if (relativeAngle <= 112.5) return "→";
        if (relativeAngle <= 157.5) return "↘";
        if (relativeAngle <= 202.5) return "↓";
        if (relativeAngle <= 247.5) return "↙";
        if (relativeAngle <= 292.5) return "←";
        return "↖";
    }
}
