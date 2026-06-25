package com.lumengps;

import com.lumengps.data.GpsConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side GPS manager that handles routes and particles for multiple players.
 */
public final class ServerGpsManager {

    private static final ServerGpsManager INSTANCE = new ServerGpsManager();

    public static ServerGpsManager getInstance() {
        return INSTANCE;
    }

    private static final double RENDER_RADIUS = 30.0;
    private static final int TICK_INTERVAL = 2;
    private static final double WAYPOINT_SNAP_DISTANCE = 2.5;

    public record ActiveRoute(List<Vec3> route, String targetName, Vec3 targetPos, boolean isElytra, String style) {}

    private final Map<UUID, ActiveRoute> activeRoutes = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    private ServerGpsManager() {}

    public void setRoute(UUID playerId, List<Vec3> route, String targetName, Vec3 targetPos, boolean isElytra, String style) {
        if (route.isEmpty()) {
            clear(playerId);
        } else {
            activeRoutes.put(playerId, new ActiveRoute(new ArrayList<>(route), targetName, targetPos, isElytra, style));
        }
    }

    public void clear(UUID playerId) {
        activeRoutes.remove(playerId);
    }

    public boolean isActive(UUID playerId) {
        return activeRoutes.containsKey(playerId);
    }

    public void onServerTick(MinecraftServer server) {
        if (activeRoutes.isEmpty()) return;

        tickCounter++;
        boolean shouldSpawnParticles = tickCounter >= TICK_INTERVAL;
        if (shouldSpawnParticles) {
            tickCounter = 0;
        }

        Iterator<Map.Entry<UUID, ActiveRoute>> it = activeRoutes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveRoute> entry = it.next();
            UUID playerId = entry.getKey();
            ActiveRoute routeData = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                // Player is offline, keep route or clear? Better to keep it until they reconnect.
                continue;
            }

            ServerLevel world = (ServerLevel) player.level();
            Vec3 playerPos = player.position();
            Vec3 targetPos = routeData.targetPos();

            // 1. Check Arrival
            if (targetPos != null && playerPos.distanceToSqr(targetPos) <= 6.25) { // 2.5 blocks radius
                player.sendSystemMessage(Component.literal("§b[LumenGPS]§r ").append(Component.translatable("lumengps.command.destination_reached")));
                world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
                it.remove();
                continue;
            }

            // 2. HUD / Actionbar
            if (GpsConfig.getInstance().showHud && targetPos != null) {
                int dist = (int) Math.round(Math.sqrt(playerPos.distanceToSqr(targetPos)));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(Component.literal("§bLumenGPS: §e" + dist + "m §7(" + routeData.targetName() + ")")));
            }

            // 3. Advance route
            advanceRoute(player, routeData.route());

            // 4. Spawn Particles
            if (shouldSpawnParticles) {
                SimpleParticleType pt = switch (routeData.style()) {
                    case "fire" -> ParticleTypes.FLAME;
                    case "soul" -> ParticleTypes.SOUL_FIRE_FLAME;
                    case "end" -> ParticleTypes.END_ROD;
                    case "emerald" -> ParticleTypes.HAPPY_VILLAGER;
                    default -> ParticleTypes.GLOW;
                };

                double radiusSq = RENDER_RADIUS * RENDER_RADIUS;
                for (Vec3 point : routeData.route()) {
                    if (playerPos.distanceToSqr(point) > radiusSq) continue;
                    world.sendParticles(player, pt, true, true, point.x, point.y, point.z, 1, 0.0, pt == ParticleTypes.GLOW ? 0.02 : 0.0, 0.0, 0.0);
                }

                // Pillar
                if (GpsConfig.getInstance().enableLightPillar && targetPos != null) {
                    if (playerPos.distanceToSqr(targetPos) <= radiusSq * 4) {
                        for (int yOffset = 0; yOffset < 80; yOffset += 2) {
                            world.sendParticles(player, ParticleTypes.END_ROD, true, true, targetPos.x, targetPos.y + yOffset, targetPos.z, 1, 0.0, 0.05, 0.0, 0.0);
                        }
                    }
                }
            }
        }
    }

    private void advanceRoute(ServerPlayer player, List<Vec3> route) {
        if (route.isEmpty()) return;
        Vec3 playerPos = player.position().add(0, 1, 0); // compare at eye-ish height
        double snapSq = WAYPOINT_SNAP_DISTANCE * WAYPOINT_SNAP_DISTANCE;

        while (!route.isEmpty() && playerPos.distanceToSqr(route.get(0)) < snapSq) {
            route.remove(0);
        }
    }
}
