package com.lumengps;

import com.lumengps.command.GpsCommand;
import com.lumengps.data.WaypointManager;
import com.lumengps.data.GpsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
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

        // Death Waypoint Listener
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                if (GpsConfig.getInstance().enableDeathWaypoint) {
                    try {
                        String name = "death";
                        BlockPos pos = player.blockPosition();
                        String dim = player.level().dimension().identifier().toString();
                        WaypointManager.get(player.getUUID()).add(name, pos, dim, "soul");
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[LumenGPS]§r Local da morte salvo como waypoint §e'death'§r em " + pos.toShortString()));
                    } catch (Exception e) {
                        LOGGER.error("Failed to save death waypoint", e);
                    }
                }
            }
        });
    }
}
