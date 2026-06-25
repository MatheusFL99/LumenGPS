package com.lumengps;

import com.lumengps.command.GpsCommand;
import com.lumengps.gui.GpsConfigScreen;
import com.lumengps.renderer.GpsRenderer;
import com.lumengps.renderer.GpsHud;
import com.lumengps.data.WaypointManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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
    private static int calcCooldown = 0;

    @Override
    public void onInitializeClient() {
        LumenGPS.LOGGER.info("[LumenGPS] Client initializing…");

        // Register HUD rendering
        GpsHud.register();

        // Shift + right-click with a compass in either hand opens the config screen.
        // Fires on both sides, so we filter by isClientSide() and only react when not already in a GUI.
        UseItemCallback.EVENT.register((Player player, Level world, InteractionHand hand) -> {
            if (!world.isClientSide()) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || !client.player.isShiftKeyDown()) return InteractionResult.PASS;
            if (client.gui.screen() != null) return InteractionResult.PASS;
            if (!isHoldingCompass(client.player)) return InteractionResult.PASS;

            client.execute(() -> client.gui.setScreen(new GpsConfigScreen(null)));
            return InteractionResult.FAIL;
        });

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

        // Auto-Death Waypoint Tracker, Armor Detection & Auto-Recalculation
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // Cooldown logic
                if (calcCooldown > 0) calcCooldown--;

                // 1. Health check for death waypoint
                float health = client.player.getHealth();
                if (health <= 0 && lastHealth > 0 && com.lumengps.data.GpsConfig.getInstance().enableDeathWaypoint) {
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
                        client.player.sendSystemMessage(Component.literal("§b[LumenGPS]§r ")
                                .append(Component.translatable(isCurrentlyElytra ? "lumengps.command.flight_mode_active" : "lumengps.command.walk_mode_active")));
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
     * Recalculates the active route to account for change in movement mode (walking vs flight)
     * or to update the trail as the player moves.
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

        // Reset tracking state
        calcCooldown = 40; // 2 second cooldown to prevent thread spam

        com.lumengps.pathfinding.Pathfinder.computeAsync(client.level, start, goal, isElytraMode, result -> {
            if (!result.isEmpty()) {
                GpsRenderer.getInstance().setRoute(result.points(), name, target, isElytraMode, finalStyle);
            }
        });
    }

    private static boolean isHoldingCompass(Player player) {
        return player.getMainHandItem().is(Items.COMPASS) || player.getOffhandItem().is(Items.COMPASS);
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
