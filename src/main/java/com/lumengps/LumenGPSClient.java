package com.lumengps;

import com.lumengps.command.GpsCommand;
import com.lumengps.renderer.GpsRenderer;
import com.lumengps.data.WaypointManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-only entry point.
 * Registers the /gps command tree and the per-tick particle renderer.
 */
@Environment(EnvType.CLIENT)
public class LumenGPSClient implements ClientModInitializer {

    private static float lastHealth = -1;

    @Override
    public void onInitializeClient() {
        LumenGPS.LOGGER.info("[LumenGPS] Client initializing…");

        // Register /gps commands (client-side — works on any server without server-side mod).
        ClientCommandRegistrationCallback.EVENT.register(GpsCommand::register);

        // Register the particle renderer that runs every client tick.
        ClientTickEvents.END_CLIENT_TICK.register(GpsRenderer.getInstance()::onClientTick);

        // Chat Listener to inject clickable [+ Add Waypoint] buttons for shared waypoints.
        Pattern sharePattern = Pattern.compile("\\[LumenGPS\\] Shared Waypoint: '(.*?)' at (-?\\d+), (-?\\d+), (-?\\d+) \\(Style: (.*?)\\)");
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String text = message.getString();
            Matcher m = sharePattern.matcher(text);
            if (m.find()) {
                String name = m.group(1);
                String x = m.group(2);
                String y = m.group(3);
                String z = m.group(4);
                String style = m.group(5);

                Minecraft.getInstance().execute(() -> {
                    String addCmd = "/gps addpos " + name + " " + x + " " + y + " " + z + " " + style;
                    MutableComponent addBtn = Component.literal("§a[+ Add Waypoint " + name + "]§r")
                            .withStyle(s -> s
                                    .withClickEvent(new ClickEvent.SuggestCommand(addCmd))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to pre-fill command to save '" + name + "'"))));

                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("  ↳ ").append(addBtn));
                });
            }
        });

        // Auto-Death Waypoint Tracker
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                float health = client.player.getHealth();
                if (health <= 0 && lastHealth > 0) {
                    BlockPos pos = BlockPos.containing(client.player.position());
                    WaypointManager.getInstance().add("death", pos, "soul");
                    client.player.sendSystemMessage(Component.literal("§b[LumenGPS]§r ")
                            .append(Component.translatable("lumengps.command.death_waypoint_saved")));
                }
                lastHealth = health;
            } else {
                lastHealth = -1;
            }
        });
    }
}
