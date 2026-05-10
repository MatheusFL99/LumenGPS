package com.lumengps.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lumengps.LumenGPS;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages named waypoint persistence using a JSON file stored at
 * {@code config/lumengps/waypoints.json}.
 *
 * <p>Uses Minecraft's bundled {@link Gson} library — no extra dependencies
 * are needed. Waypoints are held in memory in a {@link LinkedHashMap} to
 * preserve insertion order when listed.</p>
 *
 * <h3>Thread safety</h3>
 * All public methods are synchronized on {@code this} so that the async
 * pathfinder thread can read waypoints safely while the main thread writes.
 */
public final class WaypointManager {

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    private static final WaypointManager INSTANCE = new WaypointManager();

    public static WaypointManager getInstance() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------
    // Internal JSON DTO
    // -----------------------------------------------------------------------

    /** Simple serialisable DTO — BlockPos is not directly Gson-friendly. */
    private record WaypointDto(int x, int y, int z, String dimension, String style) {}

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /** Config directory: {@code .minecraft/config/lumengps/} */
    private static final Path CONFIG_DIR = Path.of("config", "lumengps");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DTO_MAP_TYPE = new TypeToken<Map<String, WaypointDto>>() {}.getType();

    /** In-memory store: name → Waypoint. */
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    private String currentWorldId = null;

    private WaypointManager() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Saves a waypoint. Overwrites any existing entry with the same name.
     * Immediately persists the change to disk.
     *
     * @param name      Case-insensitive waypoint name.
     * @param pos       Block position to save.
     * @param dimension The dimension ID where the player is.
     * @param style     The visual style to use.
     */
    public synchronized void add(String name, BlockPos pos, String dimension, String style) {
        waypoints.put(name.toLowerCase(Locale.ROOT), new Waypoint(pos, dimension, style));
        save();
    }

    /**
     * Removes a waypoint by name.
     *
     * @param name Case-insensitive waypoint name.
     * @return true if the waypoint existed and was removed, false otherwise.
     */
    public synchronized boolean remove(String name) {
        boolean removed = waypoints.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    /**
     * Returns the {@link Waypoint} for the named waypoint, or {@link Optional#empty()}
     * if no waypoint with that name exists.
     *
     * @param name Case-insensitive waypoint name.
     */
    public synchronized Optional<Waypoint> get(String name) {
        return Optional.ofNullable(waypoints.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns an unmodifiable snapshot of the current waypoint names in
     * insertion order.
     */
    public synchronized List<String> listNames() {
        return List.copyOf(waypoints.keySet());
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    /**
     * Loads waypoints for a specific world/server ID.
     *
     * @param worldId A unique identifier for the world or server.
     */
    public synchronized void load(String worldId) {
        this.currentWorldId = worldId;
        this.waypoints.clear();

        Path worldFile = CONFIG_DIR.resolve("waypoints_" + worldId + ".json");

        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(worldFile)) {
                LumenGPS.LOGGER.info("[LumenGPS] No waypoints file found for world {} — starting fresh.", worldId);
                return;
            }

            try (Reader reader = Files.newBufferedReader(worldFile, StandardCharsets.UTF_8)) {
                Map<String, WaypointDto> dtoMap = GSON.fromJson(reader, DTO_MAP_TYPE);
                if (dtoMap != null) {
                    dtoMap.forEach((name, dto) -> {
                            String style = dto.style() != null ? dto.style() : "glow";
                            String dimension = dto.dimension() != null ? dto.dimension() : "minecraft:overworld";
                            waypoints.put(name, new Waypoint(new BlockPos(dto.x(), dto.y(), dto.z()), dimension, style));
                    });
                    LumenGPS.LOGGER.info("[LumenGPS] Loaded {} waypoint(s) for world {}.", waypoints.size(), worldId);
                }
            }
        } catch (Exception e) {
            LumenGPS.LOGGER.error("[LumenGPS] Failed to load waypoints for world {}: {}", worldId, e.getMessage(), e);
        }
    }

    /**
     * Clears current waypoints from memory.
     */
    public synchronized void clear() {
        this.waypoints.clear();
        this.currentWorldId = null;
    }

    /**
     * Persists the current in-memory waypoints to a world-specific JSON file.
     */
    public synchronized void save() {
        if (currentWorldId == null) return;

        Path worldFile = CONFIG_DIR.resolve("waypoints_" + currentWorldId + ".json");

        try {
            Files.createDirectories(CONFIG_DIR);

            Map<String, WaypointDto> dtoMap = new LinkedHashMap<>();
            waypoints.forEach((name, wp) ->
                    dtoMap.put(name, new WaypointDto(wp.pos().getX(), wp.pos().getY(), wp.pos().getZ(), wp.dimension(), wp.style())));

            try (Writer writer = Files.newBufferedWriter(worldFile, StandardCharsets.UTF_8)) {
                GSON.toJson(dtoMap, writer);
            }

            LumenGPS.LOGGER.info("[LumenGPS] Saved {} waypoint(s) for world {}.", waypoints.size(), currentWorldId);
        } catch (Exception e) {
            LumenGPS.LOGGER.error("[LumenGPS] Failed to save waypoints for world {}: {}", currentWorldId, e.getMessage(), e);
        }
    }
}
