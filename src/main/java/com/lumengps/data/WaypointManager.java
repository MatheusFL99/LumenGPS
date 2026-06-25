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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages named waypoint persistence using a JSON file stored at
 * {@code config/lumengps/waypoints/<player_uuid>.json}.
 */
public final class WaypointManager {

    private static final Map<UUID, WaypointManager> MANAGERS = new ConcurrentHashMap<>();

    public static WaypointManager get(UUID playerId) {
        return MANAGERS.computeIfAbsent(playerId, WaypointManager::new);
    }

    public static void unload(UUID playerId) {
        MANAGERS.remove(playerId);
    }

    // -----------------------------------------------------------------------
    // Internal JSON DTO
    // -----------------------------------------------------------------------

    private record WaypointDto(int x, int y, int z, String dimension, String style) {}

    public record PendingAdd(String name, BlockPos pos, String dimension, String style) {}

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private static final Path CONFIG_DIR = Path.of("config", "lumengps", "waypoints");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DTO_MAP_TYPE = new TypeToken<Map<String, WaypointDto>>() {}.getType();

    private final UUID playerId;
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    private final Map<String, PendingAdd> pendingAdds = new ConcurrentHashMap<>();

    private WaypointManager(UUID playerId) {
        this.playerId = playerId;
        load();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public synchronized void add(String name, BlockPos pos, String dimension, String style) {
        waypoints.put(name.toLowerCase(Locale.ROOT), new Waypoint(pos, dimension, style));
        save();
    }

    public synchronized boolean remove(String name) {
        boolean removed = waypoints.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    public synchronized Optional<Waypoint> getWaypoint(String name) {
        return Optional.ofNullable(waypoints.get(name.toLowerCase(Locale.ROOT)));
    }

    public synchronized List<String> listNames() {
        return List.copyOf(waypoints.keySet());
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private synchronized void load() {
        this.waypoints.clear();
        Path file = CONFIG_DIR.resolve(playerId.toString() + ".json");

        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(file)) return;

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, WaypointDto> dtoMap = GSON.fromJson(reader, DTO_MAP_TYPE);
                if (dtoMap != null) {
                    dtoMap.forEach((name, dto) -> {
                            String style = dto.style() != null ? dto.style() : "glow";
                            String dimension = dto.dimension() != null ? dto.dimension() : "minecraft:overworld";
                            waypoints.put(name, new Waypoint(new BlockPos(dto.x(), dto.y(), dto.z()), dimension, style));
                    });
                }
            }
        } catch (Exception e) {
            LumenGPS.LOGGER.error("[LumenGPS] Failed to load waypoints for player {}: {}", playerId, e.getMessage(), e);
        }
    }

    private synchronized void save() {
        Path file = CONFIG_DIR.resolve(playerId.toString() + ".json");

        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                Map<String, WaypointDto> dtoMap = new LinkedHashMap<>();
                waypoints.forEach((name, wp) -> 
                    dtoMap.put(name, new WaypointDto(wp.pos().getX(), wp.pos().getY(), wp.pos().getZ(), wp.dimension(), wp.style()))
                );
                GSON.toJson(dtoMap, writer);
            }
        } catch (Exception e) {
            LumenGPS.LOGGER.error("[LumenGPS] Failed to save waypoints for player {}: {}", playerId, e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Pending overwrite state
    // -----------------------------------------------------------------------

    public String putPending(PendingAdd pending) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        pendingAdds.put(id, pending);
        return id;
    }

    public Optional<PendingAdd> consumePending(String id) {
        return Optional.ofNullable(pendingAdds.remove(id));
    }

    public void removePending(String id) {
        pendingAdds.remove(id);
    }
}
