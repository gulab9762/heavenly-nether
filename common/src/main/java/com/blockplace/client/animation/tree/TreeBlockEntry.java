package com.blockplace.client.animation.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a single block belonging to a detected tree structure.
 *
 * @param pos            world block position
 * @param state          block state
 * @param isLeaf         true if leaf/canopy block, false if trunk/branch log
 * @param heightFraction relative height within tree from 0.0 (root) to 1.0 (top)
 * @param radialDistance horizontal distance from central trunk axis
 * @param randomSeed     deterministic seed for independent leaf flutter oscillations
 */
public record TreeBlockEntry(
        BlockPos pos,
        BlockState state,
        boolean isLeaf,
        float heightFraction,
        float radialDistance,
        int randomSeed
) {}
