package com.unharvest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedurally generates the bounded Unharvest wheat arena (~4,096 wheat crops, 64x64 blocks)
 * mirroring the Steam demo scale, enclosed by rustic fences and lanterns.
 */
public class UnharvestFieldGenerator {

    public static final int MIN_X = -32;
    public static final int MAX_X = 31;
    public static final int MIN_Z = -32;
    public static final int MAX_Z = 31;

    private static final Set<String> GENERATED_LEVELS = ConcurrentHashMap.newKeySet();

    /**
     * Finds the ground Y coordinate dynamically by scanning at (0, 0).
     */
    public static int getGroundY(Level level) {
        if (level == null) return -60;

        int maxY = Math.min(level.getMaxY(), 100);
        int minY = level.getMinY();

        for (int y = maxY; y >= minY; y--) {
            BlockPos p = new BlockPos(0, y, 0);
            BlockState state = level.getBlockState(p);
            if (!state.isAir()) {
                if (state.is(Blocks.WHEAT) || state.is(Blocks.WITHER_ROSE)) {
                    return y - 1;
                }
                return y;
            }
        }
        return level.getMinY() + 4;
    }

    public static int getWheatY(Level level) {
        return getGroundY(level) + 1;
    }

    /**
     * Checks if a level's Unharvest field has been populated and builds it if needed.
     */
    public static void ensureFieldGenerated(Level level) {
        if (level == null) return;

        String dimensionKey = level.dimension().identifier().toString();
        if (GENERATED_LEVELS.contains(dimensionKey)) {
            return;
        }

        int groundY = getGroundY(level);
        int wheatY = groundY + 1;

        // Check if wheat is already present at center
        BlockPos checkPos = new BlockPos(0, wheatY, 0);
        if (level.getBlockState(checkPos).is(Blocks.WHEAT) || level.getBlockState(checkPos).is(Blocks.WITHER_ROSE)) {
            GENERATED_LEVELS.add(dimensionKey);
            return;
        }

        // Only generate in flat or plains environments with farmland base
        BlockState groundState = level.getBlockState(new BlockPos(0, groundY, 0));
        if (!groundState.is(Blocks.FARMLAND) && !groundState.is(Blocks.DIRT) && !groundState.is(Blocks.GRASS_BLOCK)) {
            return;
        }

        generateField(level, groundY, wheatY);
        GENERATED_LEVELS.add(dimensionKey);
    }

    /**
     * Forces full generation or regeneration of the 64x64 wheat arena.
     */
    public static void generateField(Level level) {
        int groundY = getGroundY(level);
        int wheatY = groundY + 1;
        generateField(level, groundY, wheatY);
    }

    public static void generateField(Level level, int groundY, int wheatY) {
        BlockState moistFarmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
        BlockState matureWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState fenceState = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState lanternState = Blocks.LANTERN.defaultBlockState();

        // 1. Fill 64x64 Wheat Field
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                BlockPos floorPos = new BlockPos(x, groundY, z);
                BlockPos cropPos = new BlockPos(x, wheatY, z);

                level.setBlock(floorPos, moistFarmland, 2);
                level.setBlock(cropPos, matureWheat, 2);
            }
        }

        // 2. Build Perimeter Border & Lanterns (Outer ring around 64x64 arena)
        int borderMinX = MIN_X - 1;
        int borderMaxX = MAX_X + 1;
        int borderMinZ = MIN_Z - 1;
        int borderMaxZ = MAX_Z + 1;

        for (int x = borderMinX - 2; x <= borderMaxX + 2; x++) {
            for (int z = borderMinZ - 2; z <= borderMaxZ + 2; z++) {
                boolean isBorderWall = (x == borderMinX || x == borderMaxX || z == borderMinZ || z == borderMaxZ);
                boolean isOuterGround = (x < borderMinX || x > borderMaxX || z < borderMinZ || z > borderMaxZ);

                BlockPos floorPos = new BlockPos(x, groundY, z);
                BlockPos abovePos = new BlockPos(x, wheatY, z);

                if (isBorderWall) {
                    level.setBlock(floorPos, coarseDirt, 2);
                    level.setBlock(abovePos, fenceState, 2);

                    // Place a light post with lantern every 8 blocks along fence
                    if ((Math.abs(x) % 8 == 0 || Math.abs(z) % 8 == 0) && (x == borderMinX || x == borderMaxX || z == borderMinZ || z == borderMaxZ)) {
                        BlockPos post2 = new BlockPos(x, wheatY + 1, z);
                        BlockPos lanternPos = new BlockPos(x, wheatY + 2, z);
                        level.setBlock(post2, fenceState, 2);
                        level.setBlock(lanternPos, lanternState, 2);
                    }
                } else if (isOuterGround) {
                    level.setBlock(floorPos, coarseDirt, 2);
                    if (!level.getBlockState(abovePos).isAir()) {
                        level.removeBlock(abovePos, false);
                    }
                }
            }
        }
    }

    public static void clearCache() {
        GENERATED_LEVELS.clear();
    }
}
