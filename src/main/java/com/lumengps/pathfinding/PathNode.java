package com.lumengps.pathfinding;

import net.minecraft.core.BlockPos;

/**
 * A single node used by the A* pathfinder.
 *
 * <ul>
 *   <li>{@code pos}    – World position of this node.</li>
 *   <li>{@code g}      – Cost from the start node to this node.</li>
 *   <li>{@code h}      – Heuristic estimate from this node to the goal (Manhattan distance).</li>
 *   <li>{@code parent} – Previous node in the cheapest path found so far (null for start).</li>
 * </ul>
 *
 * The natural ordering sorts by {@code f = g + h} so that a {@link java.util.PriorityQueue}
 * always pops the most promising node first.
 */
public final class PathNode implements Comparable<PathNode> {

    public final BlockPos pos;
    public final double g;
    public final double h;
    public final PathNode parent;
    public final int fallDistance;

    public PathNode(BlockPos pos, double g, double h, PathNode parent, int fallDistance) {
        this.pos = pos;
        this.g = g;
        this.h = h;
        this.parent = parent;
        this.fallDistance = fallDistance;
    }

    /** f-cost = g + h */
    public double f() {
        return g + h;
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.f(), other.f());
    }
}
