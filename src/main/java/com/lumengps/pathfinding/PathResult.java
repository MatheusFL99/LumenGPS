package com.lumengps.pathfinding;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Result of a pathfinding computation.
 *
 * @param points     Dense list of world-space Vec3 points along the route.
 * @param isFallback {@code true} when the route is a crow-fly straight line
 *                   (A* could not find a valid walkable path).
 */
public record PathResult(List<Vec3> points, boolean isFallback) {

    /** {@code true} when no route points are available at all. */
    public boolean isEmpty() {
        return points.isEmpty();
    }
}
