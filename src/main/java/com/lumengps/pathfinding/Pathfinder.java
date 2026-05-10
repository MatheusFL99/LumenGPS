package com.lumengps.pathfinding;

import com.lumengps.LumenGPS;
import com.lumengps.util.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * Asynchronous A* pathfinder for LumenGPS.
 *
 * <p>The algorithm runs on a dedicated daemon thread to avoid freezing the
 * Minecraft client during expensive searches. When finished (or aborted), the
 * result is posted back onto the main client thread via
 * {@link Minecraft#execute(Runnable)}, making it safe to hand off to
 * {@link com.lumengps.renderer.GpsRenderer}.</p>
 *
 * <h3>Movement rules</h3>
 * <ul>
 *   <li>4 cardinal directions (N/S/E/W) plus 1-block step-up and step-down.</li>
 *   <li>Walkability is checked via {@link BlockUtil#isWalkable}.</li>
 *   <li>Maximum nodes explored: {@value #MAX_NODES} (prevents freezes on impossible routes).</li>
 * </ul>
 */
public final class Pathfinder {

    /** Safety cap: abort search after this many expanded nodes. */
    private static final int MAX_NODES = 10_000;

    /** Spacing between interpolated waypoints (in blocks). */
    private static final double POINT_SPACING = 0.5;

    private Pathfinder() {}

    /**
     * Starts an asynchronous A* computation from {@code start} to {@code goal}.
     *
     * @param world    The client world used for block queries. Must not be null.
     * @param start    Starting block position (player's current block).
     * @param goal     Target block position (saved waypoint).
     * @param callback Called on the <em>main client thread</em> with the list of
     *                 interpolated {@link Vec3} points along the path, or an empty
     *                 list if no path was found within {@link #MAX_NODES} expansions.
     */
    public static void computeAsync(BlockGetter world,
                                    BlockPos start,
                                    BlockPos goal,
                                    Consumer<List<Vec3>> callback) {

        // Capture a snapshot reference — world access from a background thread
        // is read-only and generally safe for client worlds since we are only
        // calling getBlockState() which does not mutate state.
        Thread worker = Thread.ofVirtual()
                .name("lumengps-pathfinder")
                .start(() -> {
                    List<Vec3> result = runAStar(world, start, goal);
                    // Post result back to the main (render) thread.
                    Minecraft.getInstance().execute(() -> callback.accept(result));
                });

        worker.setDaemon(true); // ensure thread doesn't prevent JVM shutdown
    }

    // -----------------------------------------------------------------------
    // Core A* implementation
    // -----------------------------------------------------------------------

    private static List<Vec3> runAStar(BlockGetter world, BlockPos start, BlockPos goal) {
        // Open set: min-heap by f-cost.
        PriorityQueue<PathNode> open = new PriorityQueue<>();
        // Closed set: positions we have already expanded.
        Set<BlockPos> closed = new HashSet<>();
        // Best g-cost seen so far for each position (to skip stale entries).
        Map<BlockPos, Double> bestG = new HashMap<>();

        PathNode startNode = new PathNode(start, 0.0, heuristic(start, goal), null);
        open.add(startNode);
        bestG.put(start, 0.0);

        int explored = 0;

        while (!open.isEmpty() && explored < MAX_NODES) {
            PathNode current = open.poll();

            // Skip if we already found a cheaper route to this pos.
            if (closed.contains(current.pos)) continue;
            closed.add(current.pos);
            explored++;

            // Goal reached — reconstruct path.
            if (current.pos.equals(goal)) {
                return interpolate(reconstructPath(current));
            }

            // Expand neighbours (4 cardinal + ±1 elevation).
            for (BlockPos neighbourPos : getNeighbours(current.pos)) {
                if (closed.contains(neighbourPos)) continue;
                if (!BlockUtil.isWalkable(world, neighbourPos)) continue;

                double newG = current.g + distance(current.pos, neighbourPos);
                if (newG < bestG.getOrDefault(neighbourPos, Double.MAX_VALUE)) {
                    bestG.put(neighbourPos, newG);
                    PathNode neighbourNode = new PathNode(
                            neighbourPos, newG, heuristic(neighbourPos, goal), current);
                    open.add(neighbourNode);
                }
            }
        }

        if (explored >= MAX_NODES) {
            LumenGPS.LOGGER.warn("[LumenGPS] A* reached node cap ({}) — path may be incomplete.", MAX_NODES);
        }

        // No path found or cap hit — return empty list.
        return Collections.emptyList();
    }

    /** Manhattan-distance heuristic (admissible for grid movement). */
    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    /** Euclidean distance between two positions (actual movement cost). */
    private static double distance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns the candidate neighbour positions for {@code pos}.
     * Considers 4 cardinal directions, with optional ±1 Y step for stairs/hills.
     */
    private static List<BlockPos> getNeighbours(BlockPos pos) {
        List<BlockPos> neighbours = new ArrayList<>(12);
        int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] d : cardinals) {
            int nx = pos.getX() + d[0];
            int nz = pos.getZ() + d[1];

            // Same Y (flat move).
            neighbours.add(new BlockPos(nx, pos.getY(), nz));
            // Step up (+1 Y — climbing a stair/block).
            neighbours.add(new BlockPos(nx, pos.getY() + 1, nz));
            // Step down (-1 Y — descending a stair/ledge).
            neighbours.add(new BlockPos(nx, pos.getY() - 1, nz));
        }

        return neighbours;
    }

    /** Walks parent pointers to build the ordered path from start → goal. */
    private static List<BlockPos> reconstructPath(PathNode node) {
        List<BlockPos> path = new ArrayList<>();
        for (PathNode n = node; n != null; n = n.parent) {
            path.add(n.pos);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Converts a list of {@link BlockPos} nodes into dense {@link Vec3} points
     * spaced {@link #POINT_SPACING} blocks apart, offset by +0.5 on X/Z and
     * +1.0 on Y so particles float just above the block surface.
     */
    private static List<Vec3> interpolate(List<BlockPos> nodes) {
        if (nodes.size() < 2) {
            // Single node or empty — just return the center of the goal block.
            if (nodes.size() == 1) {
                BlockPos p = nodes.get(0);
                return List.of(new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5));
            }
            return Collections.emptyList();
        }

        List<Vec3> points = new ArrayList<>();

        for (int i = 0; i < nodes.size() - 1; i++) {
            BlockPos a = nodes.get(i);
            BlockPos b = nodes.get(i + 1);

            double ax = a.getX() + 0.5;
            double ay = a.getY() + 1.0; // above block surface
            double az = a.getZ() + 0.5;
            double bx = b.getX() + 0.5;
            double by = b.getY() + 1.0;
            double bz = b.getZ() + 0.5;

            double segLen = Math.sqrt(
                    Math.pow(bx - ax, 2) + Math.pow(by - ay, 2) + Math.pow(bz - az, 2));
            int steps = Math.max(1, (int) Math.ceil(segLen / POINT_SPACING));

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                points.add(new Vec3(
                        ax + (bx - ax) * t,
                        ay + (by - ay) * t,
                        az + (bz - az) * t));
            }
        }

        // Add the final destination point.
        BlockPos last = nodes.get(nodes.size() - 1);
        points.add(new Vec3(last.getX() + 0.5, last.getY() + 1.0, last.getZ() + 0.5));

        return points;
    }
}
