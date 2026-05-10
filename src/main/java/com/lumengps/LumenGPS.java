package com.lumengps;

import com.lumengps.data.WaypointManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server + client) mod initializer.
 * Since LumenGPS is a purely client-side mod, this class only sets up
 * the mod ID constant and loads persisted waypoints from disk.
 */
public class LumenGPS implements ModInitializer {

    public static final String MOD_ID = "lumengps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[LumenGPS] Initializing…");
        // Load waypoints from config/lumengps/waypoints.json on startup.
        WaypointManager.getInstance().load();
    }
}
