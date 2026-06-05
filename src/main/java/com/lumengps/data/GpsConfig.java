package com.lumengps.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Handles mod configuration and persistence.
 */
public class GpsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lumengps.json");
    private static GpsConfig instance;

    // --- Configuration Options ---
    public boolean intelligentMode = true;
    public boolean allowWater = false;
    public boolean allowLava = false;
    public boolean enableDeathWaypoint = true;
    public boolean enableLightPillar = false;
    public boolean requireCompass = false;
    public boolean showHud = true;

    private GpsConfig() {}

    public static GpsConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, GpsConfig.class);
            } catch (IOException e) {
                instance = new GpsConfig();
            }
        } else {
            instance = new GpsConfig();
        }
        if (instance == null) instance = new GpsConfig();
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
