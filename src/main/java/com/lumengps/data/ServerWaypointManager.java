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
 * Manages global server-wide waypoints stored at
 * {@code config/lumengps/server_waypoints.json}.
 */
public final class ServerWaypointManager {

    private static final Path FILE_PATH = Path.of("config", "lumengps", "server_waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DTO_MAP_TYPE = new TypeToken<Map<String, WaypointDto>>() {}.getType();

    private static final ServerWaypointManager INSTANCE = new ServerWaypointManager();

    public static ServerWaypointManager getInstance() {
        return INSTANCE;
    }

    private record WaypointDto(int x, int y, int z, String dimension, String style) {}

    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    private ServerWaypointManager() {
        load();
    }

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

    private synchronized void load() {
        this.waypoints.clear();
        try {
            Files.createDirectories(FILE_PATH.getParent());
            if (!Files.exists(FILE_PATH)) return;

            try (Reader reader = Files.newBufferedReader(FILE_PATH, StandardCharsets.UTF_8)) {
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
            LumenGPS.LOGGER.error("[LumenGPS] Failed to load server waypoints: {}", e.getMessage(), e);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
                Map<String, WaypointDto> dtoMap = new LinkedHashMap<>();
                waypoints.forEach((name, wp) -> 
                    dtoMap.put(name, new WaypointDto(wp.pos().getX(), wp.pos().getY(), wp.pos().getZ(), wp.dimension(), wp.style()))
                );
                GSON.toJson(dtoMap, writer);
            }
        } catch (Exception e) {
            LumenGPS.LOGGER.error("[LumenGPS] Failed to save server waypoints: {}", e.getMessage(), e);
        }
    }
}