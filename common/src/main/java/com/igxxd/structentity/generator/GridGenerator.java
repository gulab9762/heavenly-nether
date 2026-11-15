package com.igxxd.structentity.generator;

import com.igxxd.structentity.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class GridGenerator {

    public enum Mode {
        GLASS,
        LANTERNS,
        RAINBOW,
        RAINBOW_LANTERNS
    }

    public static final Block[] RAINBOW_GLASS = new Block[] {
            Blocks.STAINED_GLASS.red(),
            Blocks.STAINED_GLASS.orange(),
            Blocks.STAINED_GLASS.yellow(),
            Blocks.STAINED_GLASS.lime(),
            Blocks.STAINED_GLASS.green(),
            Blocks.STAINED_GLASS.cyan(),
            Blocks.STAINED_GLASS.lightBlue(),
            Blocks.STAINED_GLASS.blue(),
            Blocks.STAINED_GLASS.purple(),
            Blocks.STAINED_GLASS.magenta(),
            Blocks.STAINED_GLASS.pink()
    };

    public static class Task {
        public final ServerLevel world;
        public final BlockPos origin;
        public final int width;
        public final int spacing;
        public final int stride;
        public final int startY;
        public final int maxY;
        public final Mode mode;
        public final ServerPlayer initiator;

        public int currentX = 0;
        public int currentZ = 0;
        public int currentY;
        public long blocksPlaced = 0;
        public long totalBlocks;
        public int ticksRunning = 0;
        public boolean completed = false;

        public Task(ServerLevel world, BlockPos origin, int width, int spacing, Mode mode, ServerPlayer initiator) {
            this.world = world;
            this.origin = origin;
            this.width = width;
            this.spacing = spacing;
            this.stride = Math.max(1, spacing + 1);
            this.startY = origin.getY();
            this.maxY = world.getMaxY();
            this.currentY = startY;
            this.mode = mode;
            this.initiator = initiator;

            long countX = (width / stride) + 1L;
            long countZ = (width / stride) + 1L;
            long countY = Math.max(1L, ((maxY - startY) / stride) + 1L);
            this.totalBlocks = countX * countZ * countY;
        }
    }

    private static Task activeTask = null;
    private static int blocksPerTick = 2500;

    public static void setSpeed(int speed) {
        blocksPerTick = Math.clamp(speed, 100, 20000);
    }

    public static int getSpeed() {
        return blocksPerTick;
    }

    public static Task getActiveTask() {
        return activeTask;
    }

    public static boolean start(ServerLevel world, BlockPos origin, int width, int spacing, Mode mode, ServerPlayer player) {
        if (activeTask != null && !activeTask.completed) {
            return false;
        }
        activeTask = new Task(world, origin, width, spacing, mode, player);
        Constants.LOG.info("Started grid generation: origin={}, width={}, spacing={}, totalBlocks={}",
                origin, width, spacing, activeTask.totalBlocks);
        return true;
    }

    public static Task stop() {
        Task task = activeTask;
        activeTask = null;
        return task;
    }

    public static void onServerTick(MinecraftServer server) {
        if (activeTask == null || activeTask.completed) {
            return;
        }

        Task task = activeTask;
        task.ticksRunning++;

        int placedThisTick = 0;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        while (placedThisTick < blocksPerTick && !task.completed) {
            int posX = task.origin.getX() + task.currentX;
            int posY = task.currentY;
            int posZ = task.origin.getZ() + task.currentZ;

            int chunkX = posX >> 4;
            int chunkZ = posZ >> 4;

            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                task.world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }

            BlockPos targetPos = new BlockPos(posX, posY, posZ);

            Block glassBlock = Blocks.GLASS;
            if (task.mode == Mode.RAINBOW || task.mode == Mode.RAINBOW_LANTERNS) {
                int stepIndex = (task.currentX / task.stride) + (task.currentZ / task.stride) + (task.currentY / task.stride);
                int colorIdx = Math.floorMod(stepIndex, RAINBOW_GLASS.length);
                glassBlock = RAINBOW_GLASS[colorIdx];
            }

            task.world.setBlock(targetPos, glassBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
            task.blocksPlaced++;
            placedThisTick++;

            if ((task.mode == Mode.LANTERNS || task.mode == Mode.RAINBOW_LANTERNS) && posY - 1 >= task.world.getMinY()) {
                BlockPos below = targetPos.below();
                task.world.setBlock(below, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), Block.UPDATE_CLIENTS);
            }

            // Advance vertical Y
            task.currentY += task.stride;
            if (task.currentY > task.maxY) {
                task.currentY = task.startY;
                // Advance Z
                task.currentZ += task.stride;
                if (task.currentZ > task.width) {
                    task.currentZ = 0;
                    // Advance X
                    task.currentX += task.stride;
                    if (task.currentX > task.width) {
                        task.completed = true;
                        break;
                    }
                }
            }
        }

        // Notification every 20 ticks (1 sec)
        if (task.ticksRunning % 20 == 0 || task.completed) {
            double percent = (task.blocksPlaced * 100.0) / Math.max(1L, task.totalBlocks);
            Component msg = Component.literal(String.format(
                    java.util.Locale.US,
                    "§b[GridGen] §eGenerating: §a%.1f%% §7(%,d / %,d blocks) §8| §c/grid stop",
                    percent, task.blocksPlaced, task.totalBlocks
            ));

            if (task.initiator != null && task.initiator.isAlive()) {
                task.initiator.sendSystemMessage(msg, true);
            }

            if (task.completed) {
                Component finished = Component.literal(String.format(
                        java.util.Locale.US,
                        "§a[GridGen] ★ Grid generation complete! Placed %,d glass blocks in %d seconds.",
                        task.blocksPlaced, task.ticksRunning / 20
                ));
                if (task.initiator != null && task.initiator.isAlive()) {
                    task.initiator.sendSystemMessage(finished, false);
                }
                server.getPlayerList().broadcastSystemMessage(finished, false);
                activeTask = null;
            }
        }
    }
}
