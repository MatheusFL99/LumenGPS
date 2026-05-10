package com.lumengps.data;

import net.minecraft.core.BlockPos;

/**
 * Represents a saved waypoint.
 *
 * @param pos   The world position of the waypoint.
 * @param style The visual particle style (e.g., "glow", "fire", "soul", "end", "emerald").
 */
public record Waypoint(BlockPos pos, String style) {
}
