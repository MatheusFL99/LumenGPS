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
 * <h3>Improvements over v1</h3>
 * <ul>
 *   <li>{@value #MAX_NODES} node cap (10× larger) + {@value #MAX_TIME_MS} ms wall-clock guard.</li>
 *   <li>Neighbours include up to +2 Y step-up and −3 Y step-down, handling hills and ledges.</li>
 *   <li>Crow-fly fallback: if A* fails, returns a straight elevated line so the player
 *       always gets some directional guidance.</li>
 * </ul>
 */
public final class Pathfinder {

    /** Safety cap: abort search after this many expanded nodes. */
    private static final int MAX_NODES = 100_000;

    /** Wall-clock time limit per search (milliseconds). */
    private static final long MAX_TIME_MS = 3_000;

    /** Spacing between interpolated waypoints (in blocks). */
    private static final double POINT_SPACING = 0.5;

    /**
     * Elevation added above the higher endpoint when generating a crow-fly
     * fallback trail (blocks). High enough to clear most terrain.
     */
    private static final double CROW_FLY_ELEVATION = 8.0;

    private Pathfinder() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Starts an asynchronous A* computation from {@code start} to {@code goal}.
     *
     * <p>If a walkable path is found within the node/time limits, {@code callback}
     * receives a {@link PathResult} with {@code isFallback = false}.
     * Otherwise a straight crow-fly route is returned with {@code isFallback = true}
     * so the player always gets <em>some</em> directional guidance.
     *
     * @param world    Client world used for block queries (read-only).
     * @param start    Player's current block position.
     * @param goal     Target waypoint block position.
     * @param callback Invoked on the <em>main client thread</em> with the result.
     */
    public static void computeAsync(BlockGetter world,
                                    BlockPos start,
                                    BlockPos goal,
                                    Consumer<PathResult> callback) {

        Thread.ofVirtual()
                .name("lumengps-pathfinder")
                .start(() -> {
                    PathResult result = runAStar(world, start, goal);
                    Minecraft.getInstance().execute(() -> callback.accept(result));
                });
    }

    // -----------------------------------------------------------------------
    // Core A* implementation
    // -----------------------------------------------------------------------

    private static PathResult runAStar(BlockGetter world, BlockPos start, BlockPos goal) {
        long deadline = System.currentTimeMillis() + MAX_TIME_MS;

        PriorityQueue<PathNode> open   = new PriorityQueue<>();
        Set<BlockPos>           closed = new HashSet<>();
        Map<BlockPos, Double>   bestG  = new HashMap<>();

        open.add(new PathNode(start, 0.0, heuristic(start, goal), null));
        bestG.put(start, 0.0);

        int explored = 0;

        PathNode closestNode = open.peek();
        double minH = closestNode.h;

        while (!open.isEmpty() && explored < MAX_NODES) {

            // Check wall-clock limit every 256 iterations (cheap modulo-free check).
            if ((explored & 0xFF) == 0 && System.currentTimeMillis() > deadline) {
                LumenGPS.LOGGER.warn("[LumenGPS] A* timed out after {}ms — crow-fly fallback.", MAX_TIME_MS);
                break; // break instead of return, so we can use closestNode
            }

            PathNode current = open.poll();
            if (closed.contains(current.pos)) continue;
            closed.add(current.pos);
            explored++;

            if (current.h < minH) {
                minH = current.h;
                closestNode = current;
            }

            // Goal reached (or close enough) — reconstruct and return the real path.
            if (current.h <= 3.0) {
                List<Vec3> route = interpolate(reconstructPath(current));
                LumenGPS.LOGGER.info("[LumenGPS] A* found path in {} nodes, {} points.", explored, route.size());
                return new PathResult(route, false);
            }

            for (BlockPos nb : getNeighbours(current.pos)) {
                if (closed.contains(nb)) continue;
                if (!BlockUtil.isWalkable(world, nb)) continue;

                double newG = current.g + distance(current.pos, nb);
                if (newG < bestG.getOrDefault(nb, Double.MAX_VALUE)) {
                    bestG.put(nb, newG);
                    open.add(new PathNode(nb, newG, heuristic(nb, goal), current));
                }
            }
        }

        LumenGPS.LOGGER.warn("[LumenGPS] A* exhausted/timed out {} nodes.", explored);

        // If we got reasonably close or made progress, return the partial path so the user isn't totally lost.
        // If the closest node is just the start, fallback to crow-fly.
        if (closestNode != null && minH < heuristic(start, goal) - 5.0) {
            List<Vec3> partialPath = interpolate(reconstructPath(closestNode));
            return new PathResult(partialPath, true); // true = fallback message will be shown
        }

        return crowFlyFallback(start, goal);
    }

    // -----------------------------------------------------------------------
    // Crow-fly fallback
    // -----------------------------------------------------------------------

    /**
     * Builds a straight elevated trail from {@code start} to {@code goal}.
     * Floats {@value #CROW_FLY_ELEVATION} blocks above the higher endpoint so it
     * clears most terrain and acts as a visual compass for the player.
     */
    private static PathResult crowFlyFallback(BlockPos start, BlockPos goal) {
        double y     = Math.max(start.getY(), goal.getY()) + CROW_FLY_ELEVATION;
        Vec3   from  = new Vec3(start.getX() + 0.5, y, start.getZ() + 0.5);
        Vec3   to    = new Vec3(goal.getX()  + 0.5, y, goal.getZ()  + 0.5);
        double len   = from.distanceTo(to);
        int    steps = Math.max(1, (int) Math.ceil(len / POINT_SPACING));

        List<Vec3> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            points.add(new Vec3(
                    from.x + (to.x - from.x) * t,
                    from.y,
                    from.z + (to.z - from.z) * t
            ));
        }
        return new PathResult(Collections.unmodifiableList(points), true);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
     *
     * <ul>
     *   <li>4 cardinal directions at the same Y (flat).</li>
     *   <li>+1 and +2 Y step-up per direction (climbs hills/slabs).</li>
     *   <li>−1, −2 and −3 Y step-down per direction (descends ledges/falls).</li>
     * </ul>
     * Total: 4 × 6 = 24 candidates.
     */
    private static List<BlockPos> getNeighbours(BlockPos pos) {
        List<BlockPos> neighbours = new ArrayList<>(24);
        int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] d : cardinals) {
            int nx = pos.getX() + d[0];
            int nz = pos.getZ() + d[1];
            int y  = pos.getY();

            neighbours.add(new BlockPos(nx, y,     nz)); // flat
            neighbours.add(new BlockPos(nx, y + 1, nz)); // step up 1
            neighbours.add(new BlockPos(nx, y + 2, nz)); // step up 2
            neighbours.add(new BlockPos(nx, y - 1, nz)); // step down 1
            neighbours.add(new BlockPos(nx, y - 2, nz)); // step down 2
            neighbours.add(new BlockPos(nx, y - 3, nz)); // step down 3 (safe fall)
        }

        return neighbours;
    }

    /** Walks parent pointers to build the ordered path from start → goal. */
    private static List<BlockPos> reconstructPath(PathNode node) {
        List<BlockPos> path = new ArrayList<>();
        for (PathNode n = node; n != null; n = n.parent) path.add(n.pos);
        Collections.reverse(path);
        return path;
    }

    /**
     * Converts a list of {@link BlockPos} nodes into dense {@link Vec3} points
     * spaced {@link #POINT_SPACING} blocks apart, offset +0.5 on X/Z and
     * +1.0 on Y so particles float just above the block surface.
     */
    private static List<Vec3> interpolate(List<BlockPos> nodes) {
        if (nodes.isEmpty()) return Collections.emptyList();
        if (nodes.size() == 1) {
            BlockPos p = nodes.get(0);
            return List.of(new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5));
        }

        List<Vec3> points = new ArrayList<>();

        for (int i = 0; i < nodes.size() - 1; i++) {
            BlockPos a = nodes.get(i);
            BlockPos b = nodes.get(i + 1);

            double ax = a.getX() + 0.5, ay = a.getY() + 1.0, az = a.getZ() + 0.5;
            double bx = b.getX() + 0.5, by = b.getY() + 1.0, bz = b.getZ() + 0.5;

            double segLen = Math.sqrt(Math.pow(bx-ax,2) + Math.pow(by-ay,2) + Math.pow(bz-az,2));
            int steps = Math.max(1, (int) Math.ceil(segLen / POINT_SPACING));

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                points.add(new Vec3(ax + (bx-ax)*t, ay + (by-ay)*t, az + (bz-az)*t));
            }
        }

        BlockPos last = nodes.get(nodes.size() - 1);
        points.add(new Vec3(last.getX() + 0.5, last.getY() + 1.0, last.getZ() + 0.5));
        return points;
    }
}
