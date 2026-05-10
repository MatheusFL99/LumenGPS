package com.lumengps;

import com.lumengps.command.GpsCommand;
import com.lumengps.renderer.GpsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-only entry point.
 * Registers the /gps command tree and the per-tick particle renderer.
 */
@Environment(EnvType.CLIENT)
public class LumenGPSClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LumenGPS.LOGGER.info("[LumenGPS] Client initializing…");

        // Register /gps commands (client-side — works on any server without server-side mod).
        ClientCommandRegistrationCallback.EVENT.register(GpsCommand::register);

        // Register the particle renderer that runs every client tick.
        ClientTickEvents.END_CLIENT_TICK.register(GpsRenderer.getInstance()::onClientTick);
    }
}
