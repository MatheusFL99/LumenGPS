package com.lumengps.data;

import net.minecraft.core.BlockPos;

/**
 * Represents a saved waypoint.
 *
 * @param pos       The world position of the waypoint.
 * @param dimension The dimension ID (e.g., "minecraft:overworld").
 * @param style     The visual particle style.
 */
public record Waypoint(BlockPos pos, String dimension, String style) {
}
