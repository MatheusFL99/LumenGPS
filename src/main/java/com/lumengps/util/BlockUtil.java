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
     * Covers bottom slabs, snow, and dirt paths/farmlands (maxY = 0.9375), 
     * but strictly excludes full solid blocks (maxY = 1.0).
     */
    private static final double SLAB_MAX_Y = 0.99;

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
        return isWalkable(world, pos, false, false);
    }

    public static boolean isWalkable(BlockGetter world, BlockPos pos, boolean allowWater, boolean allowLava) {
        // --- Feet check ---
        VoxelShape feetShape = world.getBlockState(pos).getCollisionShape(world, pos);
        if (!feetShape.isEmpty()) {
            if (feetShape.max(Direction.Axis.Y) > SLAB_MAX_Y) return false;
        }

        // --- Head check ---
        if (!isPassable(world, pos.above())) return false;

        // --- Floor check ---
        BlockPos floorPos = pos.below();
        net.minecraft.world.level.material.FluidState fluid = world.getBlockState(floorPos).getFluidState();

        if (!fluid.isEmpty()) {
            // Check specific fluids
            boolean isWater = fluid.is(net.minecraft.tags.FluidTags.WATER);
            boolean isLava = fluid.is(net.minecraft.tags.FluidTags.LAVA);
            
            if (isWater && allowWater) return true;
            if (isLava && allowLava) return true;
            
            // If it's a fluid but not allowed, it's not walkable
            return false;
        }

        // Otherwise the floor must have some collision shape.
        return !world.getBlockState(floorPos).getCollisionShape(world, floorPos).isEmpty();
    }

    /**
     * Returns {@code true} when the block at {@code pos} has an empty
     * collision shape (air, torch, grass, water…), or is a manually openable block.
     */
    public static boolean isPassable(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.FENCE_GATES)) return true;

        VoxelShape shape = state.getCollisionShape(world, pos);
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

    /**
     * Returns {@code true} if the block is water.
     */
    public static boolean isWater(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * Returns {@code true} if the block provides support (solid collision or fluid).
     */
    public static boolean hasSolidFloor(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return true; // fluids provide "support" (floating)
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    /**
     * Returns {@code true} if the block can be occupied by a player's feet.
     * Allows partial blocks like slabs, stairs, snow, and climbables.
     */
    public static boolean canOccupy(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return true;
        if (state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.FENCE_GATES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.STAIRS)) return true;

        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) return true;
        return shape.max(Direction.Axis.Y) <= SLAB_MAX_Y;
    }


    /**
     * Returns {@code true} if the block is climbable (ladders, vines, scaffolding, water).
     */
    public static boolean isClimbable(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return true;
        return !state.getFluidState().isEmpty(); // Water/Lava can be swam up
    }
}

