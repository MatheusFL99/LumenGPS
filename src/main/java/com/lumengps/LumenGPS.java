package com.lumengps;

import com.lumengps.command.GpsCommand;
import com.lumengps.data.WaypointManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LumenGPS implements ModInitializer {

    public static final String MOD_ID = "lumengps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[LumenGPS] Initializing Server-Side Mod…");

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GpsCommand.register(dispatcher, registryAccess);
        });

        // Server Tick for particles and distance tracking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerGpsManager.getInstance().onServerTick(server);
        });

        // Clear waypoints and routes on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            WaypointManager.unload(handler.player.getUUID());
            ServerGpsManager.getInstance().clear(handler.player.getUUID());
        });
    }
}
