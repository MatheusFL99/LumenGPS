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
import net.minecraft.network.chat.Component;

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
