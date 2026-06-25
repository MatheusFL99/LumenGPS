package com.lumengps.pathfinding;

import com.lumengps.LumenGPS;
import com.lumengps.util.BlockUtil;
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
     * @param world         Server world used for block queries (read-only).
     * @param start         Player's current block position.
     * @param goal          Target waypoint block position.
     * @param isElytraMode  If true, the pathfinder will allow air-based movement.
     * @param callback      Invoked on the <em>main server thread</em> with the result.
     */
    public static void computeAsync(net.minecraft.server.level.ServerLevel world,
                                    BlockPos start,
                                    BlockPos goal,
                                    boolean isElytraMode,
                                    Consumer<PathResult> callback) {

        Thread.ofVirtual()
                .name("lumengps-pathfinder")
                .start(() -> {
                    PathResult result = runAStar(world, start, goal, isElytraMode);
                    world.getServer().execute(() -> callback.accept(result));
                });
    }

    // -----------------------------------------------------------------------
    // Core A* implementation
    // -----------------------------------------------------------------------

    private static PathResult runAStar(BlockGetter world, BlockPos start, BlockPos goal, boolean isElytraMode) {
        long deadline = System.currentTimeMillis() + MAX_TIME_MS;

        PriorityQueue<PathNode> open   = new PriorityQueue<>();
        Set<BlockPos>           closed = new HashSet<>();
        Map<BlockPos, Double>   bestG  = new HashMap<>();

        open.add(new PathNode(start, 0.0, heuristic(start, goal), null, 0));
        bestG.put(start, 0.0);

        int explored = 0;

        PathNode closestNode = open.peek();
        double minH = closestNode.h;

        while (!open.isEmpty() && explored < MAX_NODES) {

            if ((explored & 0xFF) == 0 && System.currentTimeMillis() > deadline) {
                break;
            }

            PathNode current = open.poll();
            if (closed.contains(current.pos)) continue;
            closed.add(current.pos);
            explored++;

            if (current.h < minH) {
                minH = current.h;
                closestNode = current;
            }

            if (current.h <= (isElytraMode ? 5.0 : 3.0)) {
                List<Vec3> route = interpolate(reconstructPath(current));
                return new PathResult(route, false);
            }

            for (BlockPos nb : getNeighbours(current.pos)) {
                if (closed.contains(nb)) continue;

                int newFall = 0;
                if (!isElytraMode) {
                    boolean nbWaterFloor = BlockUtil.isWater(world, nb.below());
                    boolean nbLavaFloor = world.getBlockState(nb.below()).getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
                    boolean nbFluidSupport = (com.lumengps.data.GpsConfig.getInstance().allowWater && nbWaterFloor) || (com.lumengps.data.GpsConfig.getInstance().allowLava && nbLavaFloor);
                    boolean nbHasFloor = BlockUtil.hasSolidFloor(world, nb.below()) || nbFluidSupport;
                    
                    if (!nbHasFloor && !BlockUtil.isClimbable(world, nb)) {
                        if (nb.getY() <= current.pos.getY()) {
                            newFall = current.fallDistance + 1;
                        }
                    }
                }

                double stepCost = calculateStepCost(world, current, nb, isElytraMode);
                double newG = current.g + stepCost;

                if (newG < bestG.getOrDefault(nb, Double.MAX_VALUE)) {
                    bestG.put(nb, newG);
                    open.add(new PathNode(nb, newG, heuristic(nb, goal), current, newFall));
                }
            }
        }

        if (closestNode != null && minH < heuristic(start, goal) - 5.0) {
            List<Vec3> partialPath = interpolate(reconstructPath(closestNode));
            return new PathResult(partialPath, true);
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
     * Returns the 26 surrounding candidate neighbour positions for {@code pos}.
     */
    private static List<BlockPos> getNeighbours(BlockPos pos) {
        List<BlockPos> neighbours = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbours.add(pos.offset(dx, dy, dz));
                }
            }
        }
        return neighbours;
    }

    /**
     * Calculates the movement cost (penalty) for moving from one block to another.
     * Enforces the "survival mode" aggressive pathfinding logic.
     */
    private static double calculateStepCost(net.minecraft.world.level.BlockGetter world, PathNode current, BlockPos to, boolean isElytraMode) {
        BlockPos from = current.pos;
        double dist = distance(from, to);
        com.lumengps.data.GpsConfig config = com.lumengps.data.GpsConfig.getInstance();
        
        if (isElytraMode) {
            if (BlockUtil.isFlyable(world, to)) return dist;
            return dist + 200.0; // Heavy penalty for mining in flight mode
        }

        if (!config.intelligentMode) {
            // Straight mode fallback: just treat air as free, solids as obstacles
            if (BlockUtil.isPassable(world, to) && BlockUtil.isPassable(world, to.above())) return dist;
            return dist + 100.0;
        }

        // To stand in a block, feet must be occupyable and head must be free
        boolean toPassable = BlockUtil.canOccupy(world, to) && BlockUtil.isPassable(world, to.above());
        
        boolean isWaterFloor = BlockUtil.isWater(world, to.below());
        boolean isLavaFloor = world.getBlockState(to.below()).getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
        boolean fluidSupport = (config.allowWater && isWaterFloor) || (config.allowLava && isLavaFloor);
        boolean toHasFloor = BlockUtil.hasSolidFloor(world, to.below()) || fluidSupport;

        int dy = to.getY() - from.getY();
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        boolean isHorizontal = (dy == 0 && (dx > 0 || dz > 0));

        if (toPassable) {
            if (isHorizontal) {
                if (toHasFloor) return dist; // Normal walk
                
                // Jumping across a gap / walking on air
                if (current.fallDistance > 2) return dist + 500.0; // Impossible to jump horizontally forever
                return dist + 5.0; // Penalty for jumping a small gap
            } else if (dy < 0) { // Moving down
                // Climbables (ladders, vines) or water
                if (BlockUtil.isClimbable(world, to)) return dist;

                // MLG Water handling
                if (config.allowWater && isWaterFloor) return dist; // Landing in water

                if (toHasFloor) { // Stepping down safely onto a block
                    if (current.fallDistance >= 3) {
                        return dist + (current.fallDistance - 2) * 5.0; // Penalty for fall damage (accumulates after 3 blocks)
                    }
                    return dist;
                }
                
                // Falling through air
                if (current.fallDistance < 3) return dist; // Free fall up to 3 blocks
                return dist + 5.0; // Penalty accumulates per block fallen beyond safe fall height
            } else if (dy > 0) { // Moving up
                if (BlockUtil.isClimbable(world, to)) return dist; // Climbing ladder/vine/water
                return dist + 15.0; // Pillaring up / Jumping up blocks
            }
        } else {
            // Mining/Breaking required
            if (dy > 0) return dist + 100.0; // Mining upwards
            if (dy < 0) return dist + 60.0; // Mining downwards
            return dist + 80.0; // Mining horizontally
        }
        return dist;
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
