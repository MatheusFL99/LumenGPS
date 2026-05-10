package com.lumengps.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Utilities for querying block walkability during pathfinding.
 *
 * <p>Uses collision shapes for accuracy. Handles:
 * <ul>
 *   <li>Full solid blocks (stone, dirt…)</li>
 *   <li>Partial/slab-height blocks (bottom slabs, snow layers…)</li>
 *   <li>Liquids (water, lava) — treated as valid floor/feet positions</li>
 * </ul>
 */
public final class BlockUtil {

    /**
     * Maximum Y fraction of a feet-level block that is still considered
     * "passable enough" for a player to occupy the upper portion.
     * Covers bottom slabs (maxY = 0.5) but excludes full blocks (maxY = 1.0).
     */
    private static final double SLAB_MAX_Y = 0.625;

    private BlockUtil() {}

    /**
     * Returns {@code true} when a player-sized entity can occupy {@code pos}.
     *
     * <p>A position is walkable when:
     * <ol>
     *   <li>The feet block ({@code pos}) is fully passable <em>or</em> is a
     *       partial block (slab/snow) that the player can stand on top of.</li>
     *   <li>The head block ({@code pos.above()}) is fully passable.</li>
     *   <li>The floor block ({@code pos.below()}) provides support — either a
     *       non-empty collision shape <em>or</em> a liquid source.</li>
     * </ol>
     */
    public static boolean isWalkable(BlockGetter world, BlockPos pos) {
        // --- Feet check ---
        VoxelShape feetShape = world.getBlockState(pos).getCollisionShape(world, pos);
        if (!feetShape.isEmpty()) {
            // Non-passable block at feet — only allow if it's a partial/slab block
            // so the player can stand in its upper half (maxY ≤ SLAB_MAX_Y).
            if (feetShape.max(Direction.Axis.Y) > SLAB_MAX_Y) return false;
        }

        // --- Head check ---
        if (!isPassable(world, pos.above())) return false;

        // --- Floor check ---
        BlockPos floorPos = pos.below();
        BlockState floorState = world.getBlockState(floorPos);

        // Liquid source (water/lava) is a valid floor — player floats.
        if (!floorState.getFluidState().isEmpty()) return true;

        // Otherwise the floor must have some collision shape.
        return !floorState.getCollisionShape(world, floorPos).isEmpty();
    }

    /**
     * Returns {@code true} when the block at {@code pos} has an empty
     * collision shape (air, torch, grass, water…).
     */
    public static boolean isPassable(BlockGetter world, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
        return shape.isEmpty();
    }

    /**
     * Returns {@code true} when the block at {@code pos} has a non-empty
     * collision shape (solid or partial block).
     */
    public static boolean isSolid(BlockGetter world, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
        return !shape.isEmpty();
    }

    /**
     * Returns {@code true} when a player can fly through {@code pos}.
     * Only checks if the space is passable (no collision).
     */
    public static boolean isFlyable(BlockGetter world, BlockPos pos) {
        return isPassable(world, pos) && isPassable(world, pos.above());
    }
}
