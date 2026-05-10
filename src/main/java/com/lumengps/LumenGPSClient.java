package com.lumengps;

import com.lumengps.command.GpsCommand;
import com.lumengps.renderer.GpsRenderer;
import com.lumengps.renderer.GpsHud;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-only entry point.
 * Registers the /gps command tree and the per-tick particle renderer.
 */
@Environment(EnvType.CLIENT)
public class LumenGPSClient implements ClientModInitializer {

    private static float lastHealth = -1;
    private static boolean lastWasElytra = false;

    @Override
    public void onInitializeClient() {
        LumenGPS.LOGGER.info("[LumenGPS] Client initializing…");

        // Register HUD rendering
        GpsHud.register();

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

        // Handle world join/leave to switch waypoint storage
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldId = getWorldId(client);
            WaypointManager.getInstance().load(worldId);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WaypointManager.getInstance().clear();
            GpsRenderer.getInstance().clear();
        });

        // Auto-Death Waypoint Tracker & Armor Change Detection
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // 1. Health check for death waypoint
                float health = client.player.getHealth();
                if (health <= 0 && lastHealth > 0) {
                    BlockPos pos = BlockPos.containing(client.player.position());
                    String dimension = client.player.level().dimension().identifier().toString();
                    WaypointManager.getInstance().add("death", pos, dimension, "soul");
                    client.player.sendSystemMessage(Component.literal("§b[LumenGPS]§r ")
                            .append(Component.translatable("lumengps.command.death_waypoint_saved")));
                }
                lastHealth = health;

                // 2. Armor check for Elytra mode hot-swapping
                boolean isCurrentlyElytra = client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA);
                if (isCurrentlyElytra != lastWasElytra) {
                    lastWasElytra = isCurrentlyElytra;
                    if (GpsRenderer.getInstance().isActive()) {
                        recalculateRoute(client, isCurrentlyElytra);
                    }
                }
            } else {
                lastHealth = -1;
                lastWasElytra = false;
            }
        });
    }

    /**
     * Recalculates the active route to account for change in movement mode (walking vs flight).
     */
    private void recalculateRoute(Minecraft client, boolean isElytraMode) {
        GpsRenderer renderer = GpsRenderer.getInstance();
        String name = renderer.getTargetName();
        net.minecraft.world.phys.Vec3 target = renderer.getTargetPos();
        String style = renderer.getActiveStyle();

        if (name == null || target == null) return;

        BlockPos goal = BlockPos.containing(target);
        BlockPos start = BlockPos.containing(client.player.position());

        // Update style automatically if using defaults
        String styleToUse = style;
        if (isElytraMode && style.equals("glow")) styleToUse = "end";
        if (!isElytraMode && style.equals("end")) styleToUse = "glow";
        final String finalStyle = styleToUse;

        com.lumengps.pathfinding.Pathfinder.computeAsync(client.level, start, goal, isElytraMode, result -> {
            if (!result.isEmpty()) {
                GpsRenderer.getInstance().setRoute(result.points(), name, target, isElytraMode, finalStyle);
                client.player.sendSystemMessage(Component.literal("§b[LumenGPS]§r ")
                        .append(Component.translatable(isElytraMode ? "lumengps.command.flight_mode_active" : "lumengps.command.walk_mode_active")));
            }
        });
    }

    private String getWorldId(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        if (serverData != null) {
            return "multiplayer_" + serverData.ip.replace(":", "_").replaceAll("[^a-zA-Z0-9_\\-]", "");
        } else if (client.getSingleplayerServer() != null) {
            return "singleplayer_" + client.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9_\\-]", "");
        }
        return "unknown";
    }
}
