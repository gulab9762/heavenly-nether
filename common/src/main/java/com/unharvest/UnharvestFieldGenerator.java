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
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Procedurally locates the nearest village, sets world spawn at the village,
 * and generates an organic, non-squared, naturally shaped wheat field (~3,500 crops)
 * conforming to terrain elevations around the village.
 */
public class UnharvestFieldGenerator {

    private static final String MARKER_FILE_NAME = "unharvest_field_generated.json";
    private static BlockPos cachedVillageCenter = null;

    /**
     * Ensures the village is found, world spawn is set, and the organic field is generated.
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

        if (Files.exists(markerFile)) {
            // Already generated in this world, load cached center if needed
            if (cachedVillageCenter == null) {
                try {
                    String content = Files.readString(markerFile);
                    JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                    int vx = json.get("x").getAsInt();
                    int vy = json.get("y").getAsInt();
                    int vz = json.get("z").getAsInt();
                    cachedVillageCenter = new BlockPos(vx, vy, vz);
                    if (json.has("totalCrops")) {
                        UnharvestState.getInstance().setFieldTotalCrops(json.get("totalCrops").getAsInt());
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        // Generate field and set spawn in the village
        generateVillageField(server, serverLevel, markerFile);
    }

    private static void generateVillageField(MinecraftServer server, ServerLevel serverLevel, Path markerFile) {
        Constants.LOG.info("[Unharvest] Locating village for world spawn and field generation...");

        // 1. Locate nearest village
        BlockPos searchOrigin = new BlockPos(0, 64, 0);
        BlockPos villagePos = serverLevel.findNearestMapStructure(StructureTags.VILLAGE, searchOrigin, 100, false);

        if (villagePos == null) {
            Constants.LOG.warn("[Unharvest] No village structure found nearby; falling back to world spawn.");
            villagePos = serverLevel.getRespawnData() != null ? serverLevel.getRespawnData().pos() : new BlockPos(0, 64, 0);
        }

        int centerX = villagePos.getX();
        int centerZ = villagePos.getZ();
        int centerSurfaceY = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
        BlockPos spawnCenter = new BlockPos(centerX, centerSurfaceY, centerZ);
        cachedVillageCenter = spawnCenter;

        // 2. Set World Spawn at village center
        serverLevel.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawnCenter, 0.0f, 0.0f));

        // Teleport local player to village spawn
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.teleportTo(centerX + 0.5, centerSurfaceY, centerZ + 0.5);
        }

        // 3. Procedurally generate organic, non-squared wheat field
        BlockState moistFarmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
        BlockState matureWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState fenceState = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState lanternState = Blocks.LANTERN.defaultBlockState();

        int totalPlacedCrops = 0;
        int maxRadius = 55;

        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                double dist = Math.sqrt(dx * dx + dz * dz);
                double angle = Math.atan2(dz, dx);

                // Multi-harmonic radial perturbation for organic, non-square boundaries
                double shapeRadius = 35.0 * (1.0
                        + 0.28 * Math.sin(3.0 * angle + 0.7)
                        + 0.18 * Math.cos(5.0 * angle - 1.1)
                        + 0.12 * Math.sin(2.0 * angle + 2.3)
                        + 0.08 * Math.cos(7.0 * angle)
                ) + (1.8 * Math.sin(x * 0.2) * Math.cos(z * 0.2));

                if (dist <= shapeRadius) {
                    // Inside the organic field
                    int surfaceY = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    BlockPos floorPos = new BlockPos(x, surfaceY - 1, z);
                    BlockPos cropPos = new BlockPos(x, surfaceY, z);

                    BlockState floorState = serverLevel.getBlockState(floorPos);
                    BlockState cropState = serverLevel.getBlockState(cropPos);

                    // Only convert natural ground terrain; preserve village houses, paths, and structures
                    if (isNaturalGround(floorState) && (cropState.isAir() || isReplaceableVegetation(cropState))) {
                        serverLevel.setBlock(floorPos, moistFarmland, 2);
                        serverLevel.setBlock(cropPos, matureWheat, 2);
                        totalPlacedCrops++;
                    }
                } else if (dist > shapeRadius && dist <= shapeRadius + 1.8) {
                    // Irregular perimeter edge
                    int surfaceY = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    BlockPos floorPos = new BlockPos(x, surfaceY - 1, z);
                    BlockPos abovePos = new BlockPos(x, surfaceY, z);

                    BlockState floorState = serverLevel.getBlockState(floorPos);
                    if (isNaturalGround(floorState)) {
                        serverLevel.setBlock(floorPos, coarseDirt, 2);

                        // Place occasional rustic fence posts with lanterns along border
                        if (Math.abs(x * 31 + z * 17) % 13 == 0) {
                            serverLevel.setBlock(abovePos, fenceState, 2);
                            serverLevel.setBlock(abovePos.above(), lanternState, 2);
                        }
                    }
                }
            }
        }

        // 4. Update state and save marker
        UnharvestState state = UnharvestState.getInstance();
        state.setFieldTotalCrops(totalPlacedCrops);

        try {
            JsonObject json = new JsonObject();
            json.addProperty("x", centerX);
            json.addProperty("y", centerSurfaceY);
            json.addProperty("z", centerZ);
            json.addProperty("totalCrops", totalPlacedCrops);
            Files.writeString(markerFile, json.toString());
        } catch (Exception e) {
            Constants.LOG.error("[Unharvest] Failed to write field generated marker", e);
        }

        Constants.LOG.info("[Unharvest] Generated organic wheat field with {} crops at village center ({}, {}, {})",
                totalPlacedCrops, centerX, centerSurfaceY, centerZ);

        if (player != null) {
            player.sendSystemMessage(Component.literal(String.format(
                    "§5§l[Unharvest] §aGenerated plague field at village center (§e%d, %d, %d§a) with §e%d§a wheat crops!",
                    centerX, centerSurfaceY, centerZ, totalPlacedCrops
            )));
        }
    }

    public static BlockPos getVillageCenter() {
        return cachedVillageCenter;
    }

    private static boolean isNaturalGround(BlockState state) {
        if (state == null) return false;
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD)
                || state.is(Blocks.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.FARMLAND);
    }

    private static boolean isReplaceableVegetation(BlockState state) {
        if (state == null) return false;
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || (state.getBlock() instanceof FlowerBlock)
                || (state.getBlock() instanceof CropBlock);
    }

    public static void clearCache() {
        cachedVillageCenter = null;
    }
}
