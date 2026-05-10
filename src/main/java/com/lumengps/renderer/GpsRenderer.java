package com.lumengps.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;

/**
 * Client-side GPS particle renderer.
 *
 * <p>Registered to {@code ClientTickEvents.END_CLIENT_TICK} in
 * {@link com.lumengps.LumenGPSClient}. Each tick it iterates the active
 * route and spawns {@link ParticleTypes#GLOW} particles for every route
 * point within {@value #RENDER_RADIUS} blocks of the player.</p>
 *
 * <h3>Performance optimisations</h3>
 * <ul>
 *   <li>Particles are only spawned every {@value #TICK_INTERVAL} ticks
 *       (≈ 1 per 100 ms at 20 TPS) to avoid overwhelming the particle engine.</li>
 *   <li>Only points within {@value #RENDER_RADIUS} blocks are processed,
 *       which limits the number of addParticle() calls per tick.</li>
 *   <li>Route snapping: once the player is within {@value #WAYPOINT_SNAP_DISTANCE}
 *       blocks of a route point the segment behind them is discarded so the
 *       route list shrinks over time.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class GpsRenderer {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Maximum distance (blocks) at which particles are rendered. */
    private static final double RENDER_RADIUS = 30.0;

    /** Spawn particles every N ticks (20 ticks/s → every 2 ticks = 10 Hz). */
    private static final int TICK_INTERVAL = 2;

    /**
     * When the player is closer than this distance (blocks) to the leading
     * route point, that point is removed so the trail advances forward.
     */
    private static final double WAYPOINT_SNAP_DISTANCE = 2.5;

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    private static final GpsRenderer INSTANCE = new GpsRenderer();

    public static GpsRenderer getInstance() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /** Current route as a dense list of interpolated world-space points. */
    private volatile List<Vec3> activeRoute = Collections.emptyList();

    /** Active particle style (glow, fire, soul, end, emerald). */
    private volatile String activeStyle = "glow";

    /** Tick counter used to throttle particle spawning. */
    private int tickCounter = 0;

    private String targetName = null;
    private Vec3 targetPos = null;
    private boolean isElytra = false;

    private GpsRenderer() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Sets the active route. Pass an empty list (or call {@link #clear()}) to
     * stop rendering. Must be called on the main client thread.
     *
     * @param route      Dense list of world-space positions.
     * @param targetName The name of the waypoint destination.
     * @param targetPos  The final block position of the waypoint.
     * @param isElytra   Whether this route was calculated for Elytra.
     * @param style      The visual style name (e.g., "glow", "fire").
     */
    public void setRoute(List<Vec3> route, String targetName, Vec3 targetPos, boolean isElytra, String style) {
        // Use a defensive mutable copy so we can trim the head as the player moves.
        this.activeRoute = route.isEmpty() ? Collections.emptyList() : new java.util.ArrayList<>(route);
        this.activeStyle = style;
        this.targetName = targetName;
        this.targetPos = targetPos;
        this.isElytra = isElytra;
    }

    /** Removes the active route, stopping all particle rendering immediately. */
    public void clear() {
        this.activeRoute = Collections.emptyList();
        this.tickCounter = 0;
        this.targetName = null;
        this.targetPos = null;
        this.isElytra = false;
    }

    /** Returns {@code true} if a route is currently active. */
    public boolean isActive() {
        return !activeRoute.isEmpty();
    }

    public String getTargetName() {
        return targetName;
    }

    public Vec3 getTargetPos() {
        return targetPos;
    }

    public boolean isElytra() {
        return isElytra;
    }

    public String getActiveStyle() {
        return activeStyle;
    }

    // -----------------------------------------------------------------------
    // Tick handler
    // -----------------------------------------------------------------------

    /**
     * Called every client tick by {@link net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents}.
     *
     * @param client The current {@link Minecraft} client instance.
     */
    public void onClientTick(Minecraft client) {
        if (activeRoute.isEmpty()) return;

        LocalPlayer player = client.player;
        Level world = client.level;
        if (player == null || world == null) return;

        Vec3 destination = activeRoute.get(activeRoute.size() - 1);
        if (player.position().distanceToSqr(destination) <= 25.0) { // 5 blocks radius
            player.sendSystemMessage(Component.literal("§b[LumenGPS]§r ")
                    .append(Component.translatable("lumengps.command.destination_reached")));
            clear();
            return;
        }

        // Advance snap: remove leading points the player has already passed.
        advanceRoute(player);

        // Throttle: spawn particles only every TICK_INTERVAL ticks.
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        Vec3 playerPos = player.position();
        double radiusSq = RENDER_RADIUS * RENDER_RADIUS;

        net.minecraft.core.particles.SimpleParticleType pt = switch (activeStyle) {
            case "fire" -> ParticleTypes.FLAME;
            case "soul" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "end" -> ParticleTypes.END_ROD;
            case "emerald" -> ParticleTypes.HAPPY_VILLAGER;
            default -> ParticleTypes.GLOW;
        };

        // Spawn particle at each route point within render radius.
        for (Vec3 point : activeRoute) {
            if (playerPos.distanceToSqr(point) > radiusSq) continue;

            world.addParticle(
                    pt,
                    point.x, point.y, point.z,
                    0.0, pt == ParticleTypes.GLOW ? 0.02 : 0.0, 0.0
            );
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Removes route points that the player has already walked past so the
     * active list only contains the remaining path ahead of the player.
     */
    private void advanceRoute(LocalPlayer player) {
        if (activeRoute.isEmpty()) return;
        Vec3 playerPos = player.position().add(0, 1, 0); // compare at eye-ish height
        double snapSq = WAYPOINT_SNAP_DISTANCE * WAYPOINT_SNAP_DISTANCE;

        // Remove points from the front while the player is close enough.
        while (!activeRoute.isEmpty()
                && playerPos.distanceToSqr(activeRoute.get(0)) < snapSq) {
            activeRoute.remove(0);
        }
    }
}
