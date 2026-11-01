package com.unharvest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.treeshake.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates an expanded organic, non-squared wheat arena (~9,000+ crops) at world spawn
 * inside a superflat world, surrounded by endless lush grass blocks, and enforces Peaceful difficulty.
 */
public class UnharvestFieldGenerator {

    private static final String MARKER_FILE_NAME = "unharvest_field_generated.json";
    public static final int BASE_RADIUS = 55;

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
     * Ensures the field is generated at spawn and difficulty is set to Peaceful.
     */
    public static void ensureFieldGenerated(Level level) {
        if (level == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) return;

        ServerLevel serverLevel = server.overworld();
        if (serverLevel == null) return;

        Path saveDir = server.getWorldPath(LevelResource.ROOT);
        Path markerFile = saveDir.resolve(MARKER_FILE_NAME);

        // Always enforce Peaceful difficulty in Unharvest worlds
        if (server.getWorldData().getDifficulty() != Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.PEACEFUL, true);
        }

        if (Files.exists(markerFile)) {
            try {
                String content = Files.readString(markerFile);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("totalCrops")) {
                    UnharvestState.getInstance().setFieldTotalCrops(json.get("totalCrops").getAsInt());
                }
            } catch (Exception ignored) {}
            return;
        }

        generateSpawnField(server, serverLevel, markerFile);
    }

    private static void generateSpawnField(MinecraftServer server, ServerLevel serverLevel, Path markerFile) {
        Constants.LOG.info("[Unharvest] Generating expanded organic wheat field at spawn...");

        int groundY = getGroundY(serverLevel);
        int cropY = groundY + 1;

        // 1. Set World Spawn at center
        BlockPos spawnPos = new BlockPos(0, cropY, 0);
        serverLevel.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawnPos, 0.0f, 0.0f));

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.teleportTo(0.5, cropY, 0.5);
        }

        // 2. Procedural Organic Non-Squared Shape
        BlockState moistFarmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
        BlockState matureWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState fenceState = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState lanternState = Blocks.LANTERN.defaultBlockState();

        int totalPlacedCrops = 0;
        int maxScan = BASE_RADIUS + 15;

        for (int x = -maxScan; x <= maxScan; x++) {
            for (int z = -maxScan; z <= maxScan; z++) {
                double dist = Math.sqrt(x * x + z * z);
                double angle = Math.atan2(z, x);

                // Multi-harmonic undulating boundary formula for authentic natural farmland
                double shapeRadius = BASE_RADIUS * (1.0
                        + 0.25 * Math.sin(3.0 * angle + 0.8)
                        + 0.16 * Math.cos(5.0 * angle - 1.2)
                        + 0.12 * Math.sin(2.0 * angle + 2.1)
                        + 0.08 * Math.cos(7.0 * angle)
                ) + (2.0 * Math.sin(x * 0.2) * Math.cos(z * 0.2));

                BlockPos floorPos = new BlockPos(x, groundY, z);
                BlockPos cropPos = new BlockPos(x, cropY, z);

                if (dist <= shapeRadius) {
                    // Inside the wheat field
                    serverLevel.setBlock(floorPos, moistFarmland, 2);
                    serverLevel.setBlock(cropPos, matureWheat, 2);
                    totalPlacedCrops++;
                } else if (dist > shapeRadius && dist <= shapeRadius + 2.0) {
                    // Irregular coarse dirt perimeter border
                    serverLevel.setBlock(floorPos, coarseDirt, 2);
                    if (!serverLevel.getBlockState(cropPos).isAir()) {
                        serverLevel.removeBlock(cropPos, false);
                    }

                    // Place rustic fence post with lantern periodically
                    if (Math.abs(x * 31 + z * 17) % 12 == 0) {
                        serverLevel.setBlock(cropPos, fenceState, 2);
                        serverLevel.setBlock(cropPos.above(), lanternState, 2);
                    }
                }
            }
        }

        // 3. Update state and write marker
        UnharvestState state = UnharvestState.getInstance();
        state.setFieldTotalCrops(totalPlacedCrops);

        try {
            JsonObject json = new JsonObject();
            json.addProperty("centerX", 0);
            json.addProperty("centerY", cropY);
            json.addProperty("centerZ", 0);
            json.addProperty("totalCrops", totalPlacedCrops);
            Files.writeString(markerFile, json.toString());
        } catch (Exception e) {
            Constants.LOG.error("[Unharvest] Failed to save field marker", e);
        }

        Constants.LOG.info("[Unharvest] Successfully generated organic field with {} crops at spawn!", totalPlacedCrops);

        if (player != null) {
            player.sendSystemMessage(Component.literal(String.format(
                    "§5§l[Unharvest] §aGenerated expanded plague field with §e%d§a wheat crops! (§ePeaceful difficulty§a)",
                    totalPlacedCrops
            )));
        }
    }

    public static void clearCache() {
        // Cache clear hook for world reset
    }
}
