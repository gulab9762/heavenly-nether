package com.unharvest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Generates an expanded organic wheat arena (~9,000+ crops) at world spawn
 * with atmospheric farm structures (Dutch Windmill and rustic Farmer's Barn/House),
 * walking dirt paths, coarse dirt perimeter, and Peaceful difficulty.
 *
 * Expands progressively in concentric radial rings from the center outward across ticks
 * so the world update and rendering pipeline handle every block effortlessly without lag spikes.
 */
public class UnharvestFieldGenerator {

    private static final String MARKER_FILE_NAME = "unharvest_field_generated.json";
    public static final int BASE_RADIUS = 55;

    // Structure Centers
    public static final int WINDMILL_X = 26;
    public static final int WINDMILL_Z = -24;
    public static final int HOUSE_X = -26;
    public static final int HOUSE_Z = 24;

    // Concentric Radial Point Representation
    public static class FieldPoint {
        public final int x;
        public final int z;
        public final double dist;

        public FieldPoint(int x, int z, double dist) {
            this.x = x;
            this.z = z;
            this.dist = dist;
        }
    }

    private static boolean isGenerating = false;
    private static int totalPlacedCrops = 0;
    private static final List<FieldPoint> pendingPoints = new ArrayList<>();
    private static int totalPointsToProcess = 0;
    private static int processedPointsCount = 0;
    private static int currentGroundY = 0;
    private static int currentCropY = 0;
    private static Path currentMarkerFile = null;
    private static final int BLOCKS_PER_TICK = 250; // Smooth radial block rate (~3.5s total)

    public static class RescanResult {
        public final int total;
        public final int withered;
        public final int remaining;

        public RescanResult(int total, int withered, int remaining) {
            this.total = total;
            this.withered = withered;
            this.remaining = remaining;
        }
    }

    public static boolean isGenerating() {
        return isGenerating;
    }

    public static float getGenerationProgress() {
        if (!isGenerating || totalPointsToProcess <= 0) return 1.0f;
        return (float) processedPointsCount / (float) totalPointsToProcess;
    }

    public static int getGenerationPercent() {
        return Math.min(100, (int) (getGenerationProgress() * 100));
    }

    public static void stopGeneration() {
        isGenerating = false;
        pendingPoints.clear();
        processedPointsCount = 0;
        totalPointsToProcess = 0;
    }

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

    private static double distToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double l2 = dx * dx + dz * dz;
        if (l2 == 0) return Math.hypot(px - x1, pz - z1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / l2));
        double projX = x1 + t * dx;
        double projZ = z1 + t * dz;
        return Math.hypot(px - projX, pz - projZ);
    }

    public static boolean isPath(int x, int z) {
        if (x * x + z * z <= 10) return true; // Spawn clearing
        if (distToSegment(x, z, 0, 0, WINDMILL_X - 2, WINDMILL_Z + 4) <= 1.5) return true;
        if (distToSegment(x, z, 0, 0, HOUSE_X + 2, HOUSE_Z - 4) <= 1.5) return true;
        return false;
    }

    /**
     * Bounding footprints for the structures and their aprons.
     * Guaranteed to protect structures from air-clearing or crop replacement.
     */
    public static boolean isInStructureFootprint(int x, int z) {
        // Windmill footprint
        if (x >= WINDMILL_X - 7 && x <= WINDMILL_X + 7 && z >= WINDMILL_Z - 6 && z <= WINDMILL_Z + 6) {
            return true;
        }
        // Farmer's House footprint (building, porch, chimney, roof overhangs, apron)
        if (x >= HOUSE_X - 6 && x <= HOUSE_X + 6 && z >= HOUSE_Z - 6 && z <= HOUSE_Z + 6) {
            return true;
        }
        return false;
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

        if (!isGenerating) {
            startGeneration(server, serverLevel, markerFile, minecraft.player);
        }
    }

    /**
     * Initializes non-blocking radial generation:
     * Teleports the player, constructs the complete structures, and enqueues all field coordinates
     * sorted in exact radial distance order from (0, 0) to expand outward smoothly without lag spikes.
     */
    public static synchronized void startGeneration(MinecraftServer server, ServerLevel serverLevel, Path markerFile, Player player) {
        if (isGenerating) {
            if (player != null) {
                player.sendSystemMessage(Component.literal(String.format(
                        Locale.US,
                        "§5§l[Unharvest] §eGeneration already in progress: §6%d%%", getGenerationPercent()
                )));
            }
            return;
        }

        Constants.LOG.info("[Unharvest] Starting concentric radial field generation...");
        isGenerating = true;
        totalPlacedCrops = 0;
        processedPointsCount = 0;
        currentMarkerFile = markerFile;

        // Reset withered count so clearance begins at 0%
        UnharvestState.getInstance().setTotalWithered(0);

        currentGroundY = getGroundY(serverLevel);
        currentCropY = currentGroundY + 1;

        // 1. Set spawn and teleport player
        BlockPos spawnPos = new BlockPos(0, currentCropY, 0);
        serverLevel.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawnPos, 0.0f, 0.0f));

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.teleportTo(0.5, currentCropY, 0.5);
        }

        if (player != null) {
            player.sendSystemMessage(Component.literal("§5§l[Unharvest] §6Starting radial field generation from center... expanding outward smoothly."));
            player.sendOverlayMessage(Component.literal("§5§l[Unharvest] §ePopulating plague field... §f0%"));
        }

        // 2. Generate Atmospheric Structures immediately
        generateWindmill(serverLevel, currentGroundY);
        generateFarmerHouse(serverLevel, currentGroundY);

        // 3. Enqueue and sort all field coordinates in exact distance order from center (0, 0)
        int maxScan = BASE_RADIUS + 18;
        pendingPoints.clear();

        for (int x = -maxScan; x <= maxScan; x++) {
            for (int z = -maxScan; z <= maxScan; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= maxScan + 2) {
                    pendingPoints.add(new FieldPoint(x, z, dist));
                }
            }
        }

        // Sort in exact ascending order of distance from (0, 0)
        pendingPoints.sort(Comparator.comparingDouble(p -> p.dist));
        totalPointsToProcess = pendingPoints.size();
    }

    /**
     * Ticks concentric radial field population across game ticks.
     * Each tick processes a batch of coordinates expanding outward in concentric rings.
     */
    public static synchronized void tickGeneration(MinecraftServer server, ServerLevel serverLevel, Player player) {
        if (!isGenerating) return;

        BlockState moistFarmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
        BlockState matureWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState dirtPath = Blocks.DIRT_PATH.defaultBlockState();
        BlockState fenceState = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState lanternState = Blocks.LANTERN.defaultBlockState();

        int pointsThisTick = Math.min(BLOCKS_PER_TICK, pendingPoints.size());

        for (int i = 0; i < pointsThisTick; i++) {
            FieldPoint pt = pendingPoints.remove(0);
            processedPointsCount++;

            int x = pt.x;
            int z = pt.z;
            double dist = pt.dist;

            // CRITICAL FIX: Skip structure footprints IMMEDIATELY before any air-clearing or modifications!
            // This prevents the air clearing loop from slicing off the farmhouse roof or windmill tower!
            if (isInStructureFootprint(x, z)) {
                continue;
            }

            // Path handling
            if (isPath(x, z)) {
                BlockPos floorPos = new BlockPos(x, currentGroundY, z);
                BlockPos cropPos = new BlockPos(x, currentCropY, z);
                serverLevel.setBlock(floorPos, ((x + z) % 3 == 0) ? coarseDirt : dirtPath, 2);
                serverLevel.setBlock(cropPos, Blocks.AIR.defaultBlockState(), 2);
                continue;
            }

            // Clear any debris or floating structures above the field
            for (int clearY = currentCropY + 1; clearY <= currentCropY + 35; clearY++) {
                BlockPos abovePos = new BlockPos(x, clearY, z);
                if (!serverLevel.getBlockState(abovePos).isAir()) {
                    serverLevel.setBlock(abovePos, Blocks.AIR.defaultBlockState(), 2);
                }
            }

            double angle = Math.atan2(z, x);
            double shapeRadius = BASE_RADIUS * (1.0
                    + 0.25 * Math.sin(3.0 * angle + 0.8)
                    + 0.16 * Math.cos(5.0 * angle - 1.2)
                    + 0.12 * Math.sin(2.0 * angle + 2.1)
                    + 0.08 * Math.cos(7.0 * angle)
            ) + (2.0 * Math.sin(x * 0.2) * Math.cos(z * 0.2));

            BlockPos floorPos = new BlockPos(x, currentGroundY, z);
            BlockPos cropPos = new BlockPos(x, currentCropY, z);

            if (dist <= shapeRadius) {
                // Inside the wheat field: place farmland and mature wheat (replaces any old wither roses!)
                serverLevel.setBlock(floorPos, moistFarmland, 2);
                serverLevel.setBlock(cropPos, matureWheat, 2);
                totalPlacedCrops++;
            } else if (dist > shapeRadius && dist <= shapeRadius + 2.0) {
                serverLevel.setBlock(floorPos, coarseDirt, 2);
                if (!serverLevel.getBlockState(cropPos).isAir()) {
                    serverLevel.removeBlock(cropPos, false);
                }

                if (Math.abs(x * 31 + z * 17) % 12 == 0) {
                    serverLevel.setBlock(cropPos, fenceState, 2);
                    serverLevel.setBlock(cropPos.above(), lanternState, 2);
                }
            }
        }

        int percent = getGenerationPercent();
        if (player != null) {
            player.sendOverlayMessage(Component.literal(String.format(
                    Locale.US,
                    "§5§l[Unharvest] §ePopulating plague field... §f%d%% §7(%,d crops placed)",
                    percent, totalPlacedCrops
            )));
        }

        // Completion check
        if (pendingPoints.isEmpty()) {
            isGenerating = false;
            finishGeneration(server, serverLevel, player);
        }
    }

    private static void finishGeneration(MinecraftServer server, ServerLevel serverLevel, Player player) {
        // Ensure structures are pristine
        generateWindmill(serverLevel, currentGroundY);
        generateFarmerHouse(serverLevel, currentGroundY);

        UnharvestState state = UnharvestState.getInstance();
        state.setFieldStats(totalPlacedCrops, 0);

        if (currentMarkerFile != null) {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("centerX", 0);
                json.addProperty("centerY", currentCropY);
                json.addProperty("centerZ", 0);
                json.addProperty("totalCrops", totalPlacedCrops);
                Files.writeString(currentMarkerFile, json.toString());
            } catch (Exception e) {
                Constants.LOG.error("[Unharvest] Failed to save field marker", e);
            }
        }

        Constants.LOG.info("[Unharvest] Finished radial field generation with {} crops!", totalPlacedCrops);

        if (player != null) {
            player.sendSystemMessage(Component.literal(String.format(
                    Locale.US,
                    "§5§l[Unharvest] §aPlague field population complete! §e%,d§a wheat crops placed. (§ePeaceful difficulty§a)",
                    totalPlacedCrops
            )));
            player.sendOverlayMessage(Component.literal(String.format(
                    Locale.US,
                    "§5§l[Unharvest] §aField Ready! §f%,d wheat crops",
                    totalPlacedCrops
            )));
        }
    }

    /**
     * Generates a Dutch Windmill with stone base, spruce tower, dark oak cap,
     * and 4 large cross sails with white wool and fences.
     */
    private static void generateWindmill(ServerLevel level, int groundY) {
        int cx = WINDMILL_X;
        int cz = WINDMILL_Z;

        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossyBricks = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState cobblestone = Blocks.COBBLESTONE.defaultBlockState();
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState darkOakPlanks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState darkOakLog = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState spruceFence = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState whiteWool = Blocks.WOOL.white().defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();

        // 1. Foundation apron and air clearing
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz <= 24) {
                    level.setBlock(new BlockPos(cx + dx, groundY, cz + dz), ((dx + dz) % 2 == 0) ? coarseDirt : gravel, 2);
                    for (int y = groundY + 1; y <= groundY + 4; y++) {
                        level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 2. Base Floor and Walls (Y = groundY to groundY + 3) - 7x7 octagonal cylinder
        for (int y = groundY; y <= groundY + 3; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq > 10) continue;

                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (y == groundY) {
                        level.setBlock(p, cobblestone, 2);
                    } else if (distSq >= 6) {
                        // South doorway at (cx, y, cz + 3)
                        if (dx == 0 && dz == 3 && y <= groundY + 2) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                        } else {
                            BlockState stone = ((dx + dz + y) % 3 == 0) ? mossyBricks : stoneBricks;
                            level.setBlock(p, stone, 2);
                        }
                    } else {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 3. Mid Tower (Y = groundY + 4 to groundY + 10) - 5x5 octagonal cylinder
        for (int y = groundY + 4; y <= groundY + 10; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq > 6) continue;

                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (distSq >= 4) {
                        // Window slits
                        if ((dx == 0 || dz == 0) && (y == groundY + 6 || y == groundY + 9)) {
                            level.setBlock(p, spruceFence, 2);
                        } else {
                            level.setBlock(p, sprucePlanks, 2);
                        }
                    } else {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 4. Observation Balcony (Y = groundY + 11) - 7x7 platform with fence railing
        int balcY = groundY + 11;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 10) {
                    BlockPos p = new BlockPos(cx + dx, balcY, cz + dz);
                    level.setBlock(p, darkOakPlanks, 2);
                    if (distSq >= 7) {
                        level.setBlock(p.above(), spruceFence, 2);
                    }
                }
            }
        }

        // 5. Peaked Conical Cap (Y = groundY + 12 to groundY + 16)
        for (int dy = 0; dy <= 4; dy++) {
            int y = groundY + 12 + dy;
            int rad = Math.max(0, 3 - dy);
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz <= rad * rad + 1) {
                        level.setBlock(new BlockPos(cx + dx, y, cz + dz), darkOakPlanks, 2);
                    }
                }
            }
        }
        level.setBlock(new BlockPos(cx, groundY + 17, cz), spruceFence, 2);

        // 6. Windmill Axle & 4 Cross Blades (Sails)
        // Hub at (cx, groundY + 9, cz + 3), facing South
        int hubY = groundY + 9;
        int sailZ = cz + 3;
        level.setBlock(new BlockPos(cx, hubY, sailZ), darkOakLog, 2);
        level.setBlock(new BlockPos(cx, hubY, sailZ + 1), darkOakLog, 2);

        int rotorZ = sailZ + 1;
        // Blade 1: UP (dy: 1..6)
        for (int dy = 1; dy <= 6; dy++) {
            int by = hubY + dy;
            level.setBlock(new BlockPos(cx, by, rotorZ), spruceFence, 2);
            level.setBlock(new BlockPos(cx + 1, by, rotorZ), whiteWool, 2);
        }
        // Blade 2: DOWN (dy: 1..6)
        for (int dy = 1; dy <= 6; dy++) {
            int by = hubY - dy;
            level.setBlock(new BlockPos(cx, by, rotorZ), spruceFence, 2);
            level.setBlock(new BlockPos(cx - 1, by, rotorZ), whiteWool, 2);
        }
        // Blade 3: RIGHT (+X: 1..6)
        for (int dx = 1; dx <= 6; dx++) {
            int bx = cx + dx;
            level.setBlock(new BlockPos(bx, hubY, rotorZ), spruceFence, 2);
            level.setBlock(new BlockPos(bx, hubY - 1, rotorZ), whiteWool, 2);
        }
        // Blade 4: LEFT (-X: 1..6)
        for (int dx = 1; dx <= 6; dx++) {
            int bx = cx - dx;
            level.setBlock(new BlockPos(bx, hubY, rotorZ), spruceFence, 2);
            level.setBlock(new BlockPos(bx, hubY + 1, rotorZ), whiteWool, 2);
        }

        // 7. Interior furnishings (Hay bales, barrels, hanging lantern)
        level.setBlock(new BlockPos(cx - 1, groundY + 1, cz - 1), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx - 1, groundY + 2, cz - 1), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx + 1, groundY + 1, cz - 1), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx, groundY + 3, cz), lantern.setValue(LanternBlock.HANGING, true), 2);
    }

    /**
     * Generates a rustic Farmer's House / Barn with cobblestone foundation,
     * spruce wood walls, dark oak gable roof, cozy interior, and a working smoking chimney!
     */
    private static void generateFarmerHouse(ServerLevel level, int groundY) {
        int hx = HOUSE_X;
        int hz = HOUSE_Z;

        BlockState cobblestone = Blocks.COBBLESTONE.defaultBlockState();
        BlockState mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState spruceLog = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState darkOakPlanks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState spruceFence = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState spruceSlab = Blocks.SPRUCE_SLAB.defaultBlockState();
        BlockState glassPane = Blocks.GLASS_PANE.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState dirtPath = Blocks.DIRT_PATH.defaultBlockState();
        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();

        int minX = hx - 4;
        int maxX = hx + 4;
        int minZ = hz - 3;
        int maxZ = hz + 4;

        // 1. Surrounding apron and yard (set walking path and clear all air above!)
        for (int x = minX - 3; x <= maxX + 3; x++) {
            for (int z = minZ - 3; z <= maxZ + 3; z++) {
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
                    level.setBlock(new BlockPos(x, groundY, z), ((x + z) % 2 == 0) ? coarseDirt : dirtPath, 2);
                    for (int y = groundY + 1; y <= groundY + 4; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 2. Floor at groundY
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, groundY, z);
                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    level.setBlock(p, cobblestone, 2);
                } else {
                    level.setBlock(p, sprucePlanks, 2);
                }
            }
        }

        // 3. Walls (Y = groundY + 1 to groundY + 4)
        for (int y = groundY + 1; y <= groundY + 4; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isCorner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                    boolean isWall = (x == minX || x == maxX || z == minZ || z == maxZ);
                    BlockPos p = new BlockPos(x, y, z);

                    if (isCorner) {
                        level.setBlock(p, spruceLog, 2);
                    } else if (isWall) {
                        // Front Doorway at (hx, y, minZ)
                        if (x == hx && z == minZ && y <= groundY + 2) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                        } else if (y == groundY + 2 && (x == hx - 2 || x == hx + 2) && z == minZ) {
                            // Front windows
                            level.setBlock(p, glassPane, 2);
                        } else if (y == groundY + 2 && x == hx && z == maxZ) {
                            // Back window
                            level.setBlock(p, glassPane, 2);
                        } else if (y == groundY + 2 && (z == hz) && (x == minX || x == maxX)) {
                            // Side windows
                            level.setBlock(p, glassPane, 2);
                        } else if (y == groundY + 1) {
                            level.setBlock(p, ((x + z) % 3 == 0) ? mossyCobble : cobblestone, 2);
                        } else {
                            level.setBlock(p, sprucePlanks, 2);
                        }
                    } else {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 4. Front Porch (Z = minZ - 1)
        int porchZ = minZ - 1;
        level.setBlock(new BlockPos(hx - 2, groundY + 1, porchZ), spruceFence, 2);
        level.setBlock(new BlockPos(hx - 2, groundY + 2, porchZ), spruceFence, 2);
        level.setBlock(new BlockPos(hx + 2, groundY + 1, porchZ), spruceFence, 2);
        level.setBlock(new BlockPos(hx + 2, groundY + 2, porchZ), spruceFence, 2);
        for (int x = hx - 2; x <= hx + 2; x++) {
            level.setBlock(new BlockPos(x, groundY + 3, porchZ), spruceSlab, 2);
        }
        // Lanterns hanging directly beneath the porch roof slabs
        BlockState hangingLantern = lantern.setValue(LanternBlock.HANGING, true);
        level.setBlock(new BlockPos(hx - 1, groundY + 2, porchZ), hangingLantern, 2);
        level.setBlock(new BlockPos(hx + 1, groundY + 2, porchZ), hangingLantern, 2);

        // 5. Barn Gable Roof (Y = groundY + 5 to groundY + 8)
        // Sloping up along X axis towards hx
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            // Eaves at groundY + 5
            level.setBlock(new BlockPos(minX - 1, groundY + 5, z), darkOakPlanks, 2);
            level.setBlock(new BlockPos(maxX + 1, groundY + 5, z), darkOakPlanks, 2);

            // Slope 1 at groundY + 6
            level.setBlock(new BlockPos(minX, groundY + 6, z), darkOakPlanks, 2);
            level.setBlock(new BlockPos(minX + 1, groundY + 6, z), darkOakPlanks, 2);
            level.setBlock(new BlockPos(maxX - 1, groundY + 6, z), darkOakPlanks, 2);
            level.setBlock(new BlockPos(maxX, groundY + 6, z), darkOakPlanks, 2);

            // Slope 2 at groundY + 7
            level.setBlock(new BlockPos(minX + 2, groundY + 7, z), darkOakPlanks, 2);
            level.setBlock(new BlockPos(maxX - 2, groundY + 7, z), darkOakPlanks, 2);

            // Peak at groundY + 8
            for (int x = minX + 3; x <= maxX - 3; x++) {
                level.setBlock(new BlockPos(x, groundY + 8, z), darkOakPlanks, 2);
            }
        }

        // Fill gable ends
        for (int y = groundY + 5; y <= groundY + 7; y++) {
            int off = y - (groundY + 4);
            for (int x = minX + off; x <= maxX - off; x++) {
                level.setBlock(new BlockPos(x, y, minZ), sprucePlanks, 2);
                level.setBlock(new BlockPos(x, y, maxZ), sprucePlanks, 2);
            }
        }

        // 6. Cobblestone Chimney with Working Campfire Smoke!
        int chimX = maxX - 1;
        int chimZ = maxZ - 1;
        for (int y = groundY + 1; y <= groundY + 9; y++) {
            level.setBlock(new BlockPos(chimX, y, chimZ), cobblestone, 2);
        }
        level.setBlock(new BlockPos(chimX, groundY + 9, chimZ), campfire, 2);

        // 7. Cozy Interior Furnishings
        // Fully formed red bed (Head with pillow at minZ+1 against back wall, Foot with blanket at minZ+2)
        BlockPos headPos = new BlockPos(minX + 1, groundY + 1, minZ + 1);
        BlockPos footPos = new BlockPos(minX + 1, groundY + 1, minZ + 2);
        BlockState bedBlock = Blocks.BED.red().defaultBlockState();
        BlockState headState = bedBlock
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD);
        BlockState footState = bedBlock
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(headPos, headState, 2);
        level.setBlock(footPos, footState, 2);

        level.setBlock(new BlockPos(minX + 1, groundY + 1, maxZ - 1), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(minX + 1, groundY + 1, maxZ - 2), Blocks.FURNACE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(maxX - 1, groundY + 1, minZ + 1), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(new BlockPos(maxX - 1, groundY + 1, minZ + 2), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(hx, groundY + 4, hz), hangingLantern, 2);
    }

    /**
     * Rescans the arena bounding box to count exact withered roses and remaining crops,
     * re-syncing total crops, withered count, and clearance % to 100% accuracy.
     */
    public static RescanResult rescanField(Level level) {
        if (level == null) return new RescanResult(0, 0, 0);

        int groundY = getGroundY(level);
        int cropY = groundY + 1;
        int maxScan = BASE_RADIUS + 25;

        int witheredCount = 0;
        int remainingCropCount = 0;

        for (int x = -maxScan; x <= maxScan; x++) {
            for (int z = -maxScan; z <= maxScan; z++) {
                for (int y = cropY - 1; y <= cropY + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(Blocks.WITHER_ROSE)) {
                        witheredCount++;
                        break;
                    } else if (UnharvestManager.isCrop(s)) {
                        remainingCropCount++;
                        break;
                    }
                }
            }
        }

        int total = witheredCount + remainingCropCount;
        UnharvestState state = UnharvestState.getInstance();
        state.setFieldStats(total, witheredCount);

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            try {
                Path saveDir = server.getWorldPath(LevelResource.ROOT);
                Path markerFile = saveDir.resolve(MARKER_FILE_NAME);
                JsonObject json = new JsonObject();
                json.addProperty("centerX", 0);
                json.addProperty("centerY", cropY);
                json.addProperty("centerZ", 0);
                json.addProperty("totalCrops", total);
                Files.writeString(markerFile, json.toString());
            } catch (Exception ignored) {}
        }

        return new RescanResult(total, witheredCount, remainingCropCount);
    }

    public static void forceRegenerateField(MinecraftServer server, ServerLevel serverLevel, Player player) {
        Path saveDir = server.getWorldPath(LevelResource.ROOT);
        Path markerFile = saveDir.resolve(MARKER_FILE_NAME);
        try {
            Files.deleteIfExists(markerFile);
        } catch (Exception ignored) {}
        stopGeneration();
        startGeneration(server, serverLevel, markerFile, player);
    }

    public static void clearCache() {
        stopGeneration();
    }
}
