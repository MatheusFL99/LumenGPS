package com.lumengps.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Utilities for querying block walkability during pathfinding.
 * Uses collision shapes — more accurate than the deprecated {@code BlockState#isSolid()}.
 */
public final class BlockUtil {

    private BlockUtil() {}

    /**
     * Returns {@code true} when a player-sized entity can occupy {@code pos}:
     * <ol>
     *   <li>The block at {@code pos} must have no (or negligible) collision shape (air, torch, grass…).</li>
     *   <li>The block at {@code pos} must also be passable at head level ({@code pos.above()}).</li>
     *   <li>The block directly below {@code pos} must be solid (has a full collision shape).</li>
     * </ol>
     *
     * @param world Any block-readable world view (client or server).
     * @param pos   The block position to test.
     * @return {@code true} if the position is walkable.
     */
    public static boolean isWalkable(BlockGetter world, BlockPos pos) {
        // Feet-level block must be passable (no collision).
        if (!isPassable(world, pos)) return false;

        // Head-level block must also be passable (avoid walking into ceiling).
        if (!isPassable(world, pos.above())) return false;

        // Floor must be solid (entity needs something to stand on).
        return isSolid(world, pos.below());
    }

    /**
     * Returns {@code true} when the block at {@code pos} has an empty collision shape
     * (i.e., an entity can pass through it).
     */
    public static boolean isPassable(BlockGetter world, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
        return shape.isEmpty();
    }

    /**
     * Returns {@code true} when the block at {@code pos} has a non-empty collision shape
     * (i.e., an entity cannot pass through it — it acts as a floor/wall).
     */
    public static boolean isSolid(BlockGetter world, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
        return !shape.isEmpty();
    }
}
