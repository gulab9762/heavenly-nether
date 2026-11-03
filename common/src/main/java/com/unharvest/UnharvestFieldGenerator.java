package com.unharvest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Concentric Radial Field Generator & Natural Terraformer for Unharvest.
 * Clears and flattens a wide circular plateau around spawn in normal worlds,
 * blending smoothly into the surrounding natural hills with a gentle slope.
 * Expands progressively in concentric radial rings from the center outward across ticks
 * so the world update and rendering pipeline handle every block effortlessly without lag spikes.
 */
public class UnharvestFieldGenerator {

    private static final String MARKER_FILE_NAME = "unharvest_field_generated.json";
    public static final int BASE_RADIUS = 55;
    public static final int FLAT_RADIUS = 58;
    public static final int TRANSITION_RADIUS = 76;

    // Relative structure offsets from field center
    public static final int WINDMILL_OFFSET_X = 26;
    public static final int WINDMILL_OFFSET_Z = -24;
    public static final int HOUSE_OFFSET_X = -26;
    public static final int HOUSE_OFFSET_Z = 24;

    // Backwards-compatible aliases
    public static final int WINDMILL_X = WINDMILL_OFFSET_X;
    public static final int WINDMILL_Z = WINDMILL_OFFSET_Z;
    public static final int HOUSE_X = HOUSE_OFFSET_X;
    public static final int HOUSE_Z = HOUSE_OFFSET_Z;

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
    private static int currentOriginX = 0;
    private static int currentOriginZ = 0;
    private static int currentGroundY = 64;
    private static int currentCropY = 65;
    private static Path currentMarkerFile = null;
    private static final int BLOCKS_PER_TICK = 150; // Smooth radial batch rate (150 crops per tick, zero freeze)

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
     * Determines natural solid terrain surface at (x, z), ignoring foliage, flowers, logs, leaves, and snow.
     */
    public static int getNaturalGroundY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // Force chunk to load so heightmap is populated!
        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = height; y >= Math.max(level.getMinY(), 50); y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
                return y;
            }
            if (!state.isAir() && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.TALL_GRASS)
                    && !(state.getBlock() instanceof FlowerBlock) && !state.is(Blocks.SNOW)
                    && !state.is(Blocks.FERN) && !state.is(Blocks.LARGE_FERN)
                    && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                    && !state.is(Blocks.VINE) && !state.is(Blocks.HANGING_ROOTS)) {
                return y;
            }
        }
        return 64;
    }

    /**
     * Calculates the ideal baseline plateau ground Y by sampling natural heights around the center.
     */
    public static int getGroundY(Level level, int originX, int originZ) {
        if (level == null) return 64;

        if (level instanceof ServerLevel serverLevel) {
            int y0 = getNaturalGroundY(serverLevel, originX, originZ);
            int y1 = getNaturalGroundY(serverLevel, originX + 16, originZ);
            int y2 = getNaturalGroundY(serverLevel, originX - 16, originZ);
            int y3 = getNaturalGroundY(serverLevel, originX, originZ + 16);
            int y4 = getNaturalGroundY(serverLevel, originX, originZ - 16);
            int avg = (y0 + y1 + y2 + y3 + y4) / 5;
            return Math.max(64, avg);
        }

        return currentGroundY >= 64 ? currentGroundY : 64;
    }

    public static int getGroundY(Level level) {
        return currentGroundY >= 64 ? currentGroundY : 64;
    }

    public static int getWheatY(Level level) {
        return getGroundY(level) + 1;
    }

    public static int getOriginX() {
        return currentOriginX;
    }

    public static int getOriginZ() {
        return currentOriginZ;
    }

    /**
     * Identifies structure blocks belonging to the Windmill or Farmer's House.
     * Prevents terrain clearing from accidentally slicing through roofs, balconies, and sails.
     */
    public static boolean isStructureBlock(BlockState state) {
        return state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.SPRUCE_PLANKS) || state.is(Blocks.DARK_OAK_PLANKS)
                || state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.SPRUCE_LOG)
                || state.is(Blocks.SPRUCE_FENCE) || state.is(Blocks.SPRUCE_SLAB)
                || state.is(BlockTags.WOOL) || state.is(Blocks.LANTERN)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.BARREL) || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE)
                || state.getBlock() instanceof BedBlock;
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

    public static boolean isPath(int dx, int dz) {
        if (dx * dx + dz * dz <= 18) return true; // Spawn clearing
        if (distToSegment(dx, dz, 0, 0, WINDMILL_OFFSET_X - 2, WINDMILL_OFFSET_Z + 4) <= 1.5) return true;
        if (distToSegment(dx, dz, 0, 0, HOUSE_OFFSET_X + 2, HOUSE_OFFSET_Z - 4) <= 1.5) return true;
        return false;
    }

    /**
     * Exact solid structure footprint using relative center offsets.
     */
    public static boolean isSolidStructure(int dx, int dz) {
        int minX = HOUSE_OFFSET_X - 4;
        int maxX = HOUSE_OFFSET_X + 4;
        int minZ = HOUSE_OFFSET_Z - 3;
        int maxZ = HOUSE_OFFSET_Z + 4;
        if (dx >= minX && dx <= maxX && dz >= minZ && dz <= maxZ) {
            return true;
        }

        // Porch floor
        int porchZ = minZ - 1;
        if (dx >= HOUSE_OFFSET_X - 2 && dx <= HOUSE_OFFSET_X + 2 && dz == porchZ) {
            return true;
        }

        // Windmill round stone base (wdx^2 + wdz^2 <= 10)
        int wdx = dx - WINDMILL_OFFSET_X;
        int wdz = dz - WINDMILL_OFFSET_Z;
        if (wdx * wdx + wdz * wdz <= 10) {
            return true;
        }

        return false;
    }

    public static boolean isNearStructure(int dx, int dz) {
        int minX = HOUSE_OFFSET_X - 6;
        int maxX = HOUSE_OFFSET_X + 6;
        int minZ = HOUSE_OFFSET_Z - 5;
        int maxZ = HOUSE_OFFSET_Z + 6;
        if (dx >= minX && dx <= maxX && dz >= minZ && dz <= maxZ) {
            return true;
        }

        int wdx = dx - WINDMILL_OFFSET_X;
        int wdz = dz - WINDMILL_OFFSET_Z;
        if (wdx * wdx + wdz * wdz <= 64) {
            return true;
        }

        return false;
    }

    /**
     * Enforces Adventure gamemode, Peaceful difficulty, sunset lighting (time 12500),
     * and freezes daylight cycle for Unharvest worlds.
     */
    public static void applyWorldRules(MinecraftServer server, ServerLevel serverLevel) {
        if (server == null || serverLevel == null) return;

        // 1. Enforce Peaceful difficulty
        if (server.getWorldData().getDifficulty() != Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.PEACEFUL, true);
        }

        // 2. Enforce Adventure mode on server default and all connected players
        server.setDefaultGameType(GameType.ADVENTURE);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }
        }

        // 3. Set sunset time (12200) to keep moon below horizon, and freeze daylight cycle
        CommandSourceStack stack = server.createCommandSourceStack().withSuppressedOutput();
        try {
            server.getCommands().performPrefixedCommand(stack, "gamerule advance_time false");
        } catch (Exception ignored) {}
        try {
            server.getCommands().performPrefixedCommand(stack, "gamerule doDaylightCycle false");
        } catch (Exception ignored) {}
        try {
            server.getCommands().performPrefixedCommand(stack, "time set 12200");
        } catch (Exception ignored) {}
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
        UnharvestState.getInstance().loadForWorld(saveDir);
        Path markerFile = saveDir.resolve(MARKER_FILE_NAME);

        if (Files.exists(markerFile)) {
            try {
                String content = Files.readString(markerFile);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("totalCrops")) {
                    UnharvestState.getInstance().setFieldTotalCrops(json.get("totalCrops").getAsInt());
                }
                if (json.has("centerX")) currentOriginX = json.get("centerX").getAsInt();
                if (json.has("centerZ")) currentOriginZ = json.get("centerZ").getAsInt();
                if (json.has("groundY")) currentGroundY = json.get("groundY").getAsInt();
                currentCropY = currentGroundY + 1;
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
     * sorted in exact radial distance order from field origin to expand outward smoothly without lag spikes.
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

        applyWorldRules(server, serverLevel);

        // Reset withered count so clearance begins at 0%
        UnharvestState.getInstance().setTotalWithered(0);
        UnharvestState.getInstance().setElapsedMillis(0);
        UnharvestState.getInstance().setVictoryShown(false);
        UnharvestTimer.reset();

        // Determine field origin directly from the player's actual location
        ServerPlayer serverPlayer = (server != null && !server.getPlayerList().getPlayers().isEmpty())
                ? server.getPlayerList().getPlayers().get(0)
                : null;

        int originX;
        int originZ;
        if (serverPlayer != null) {
            originX = serverPlayer.getBlockX();
            originZ = serverPlayer.getBlockZ();
        } else if (player != null) {
            originX = player.getBlockX();
            originZ = player.getBlockZ();
        } else {
            BlockPos spawn = serverLevel.getRespawnData() != null ? serverLevel.getRespawnData().pos() : BlockPos.ZERO;
            originX = spawn.getX();
            originZ = spawn.getZ();
        }

        currentOriginX = originX;
        currentOriginZ = originZ;
        currentGroundY = Math.max(64, getGroundY(serverLevel, currentOriginX, currentOriginZ));
        currentCropY = currentGroundY + 1;

        // 1. Instantly carve a guaranteed safe 9x9 spawn clearing at (currentOriginX, currentGroundY, currentOriginZ)
        BlockState dirtPath = Blocks.DIRT_PATH.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int wx = currentOriginX + dx;
                int wz = currentOriginZ + dz;
                // Solid foundation below
                for (int y = currentGroundY - 6; y < currentGroundY; y++) {
                    BlockPos p = new BlockPos(wx, y, wz);
                    if (!serverLevel.getBlockState(p).isSolid()) {
                        serverLevel.setBlock(p, dirt, 2);
                    }
                }
                // Surface floor
                BlockPos floor = new BlockPos(wx, currentGroundY, wz);
                serverLevel.setBlock(floor, ((dx + dz) % 3 == 0) ? coarseDirt : dirtPath, 2);
                // Clear 12 blocks of open air above player so suffocation is impossible
                for (int y = currentGroundY + 1; y <= currentGroundY + 12; y++) {
                    BlockPos airPos = new BlockPos(wx, y, wz);
                    if (!serverLevel.getBlockState(airPos).isAir()) {
                        serverLevel.setBlock(airPos, air, 2);
                    }
                }
            }
        }

        // Set spawn and authoritatively teleport the player(s) onto the safe platform
        BlockPos spawnPos = new BlockPos(currentOriginX, currentCropY, currentOriginZ);
        serverLevel.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawnPos, 0.0f, 0.0f));

        if (server != null) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                sp.teleportTo(currentOriginX + 0.5, currentCropY, currentOriginZ + 0.5);
                sp.fallDistance = 0.0f;
                sp.setDeltaMovement(0, 0, 0);
            }
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.teleportTo(currentOriginX + 0.5, currentCropY, currentOriginZ + 0.5);
            localPlayer.fallDistance = 0.0f;
            localPlayer.setDeltaMovement(0, 0, 0);
        }

        if (player != null) {
            player.sendOverlayMessage(Component.literal("§5§l[Unharvest] §ePopulating plague field... §f0%"));
        }

        // 2. Enqueue and sort all field coordinates in exact distance order from origin
        // (Atmospheric structures will be built smoothly at completion once terrain is flattened!)
        pendingPoints.clear();
        for (int dx = -TRANSITION_RADIUS; dx <= TRANSITION_RADIUS; dx++) {
            for (int dz = -TRANSITION_RADIUS; dz <= TRANSITION_RADIUS; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= TRANSITION_RADIUS) {
                    pendingPoints.add(new FieldPoint(dx, dz, dist));
                }
            }
        }

        // Sort in exact ascending order of distance from center (0, 0)
        pendingPoints.sort(Comparator.comparingDouble(p -> p.dist));
        totalPointsToProcess = pendingPoints.size();
    }

    /**
     * Ticks concentric radial field population and smooth natural terraforming across game ticks.
     * Each tick processes a batch of coordinates expanding outward in concentric rings.
     */
    public static synchronized void tickGeneration(MinecraftServer server, ServerLevel serverLevel, Player player) {
        if (!isGenerating) return;

        // Player anti-suffocation and safety guarantee
        if (server != null) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                BlockPos ppos = sp.blockPosition();
                boolean suffocating = serverLevel.getBlockState(ppos).isSolid()
                        || serverLevel.getBlockState(ppos.above()).isSolid()
                        || sp.getY() < currentGroundY;
                if (suffocating) {
                    int safeY = Math.max((int) sp.getY(), currentGroundY) + 1;
                    while (safeY < serverLevel.getMaxY() && (serverLevel.getBlockState(new BlockPos(sp.getBlockX(), safeY, sp.getBlockZ())).isSolid()
                            || serverLevel.getBlockState(new BlockPos(sp.getBlockX(), safeY + 1, sp.getBlockZ())).isSolid())) {
                        safeY++;
                    }
                    sp.teleportTo(sp.getX(), safeY, sp.getZ());
                    sp.fallDistance = 0.0f;
                    sp.setDeltaMovement(0, 0, 0);
                }
            }
        }

        BlockState moistFarmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
        BlockState matureWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState dirtPath = Blocks.DIRT_PATH.defaultBlockState();
        BlockState grassBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState cobblestone = Blocks.COBBLESTONE.defaultBlockState();
        BlockState fenceState = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState lanternState = Blocks.LANTERN.defaultBlockState();

        int pointsThisTick = Math.min(BLOCKS_PER_TICK, pendingPoints.size());

        for (int i = 0; i < pointsThisTick; i++) {
            FieldPoint pt = pendingPoints.remove(0);
            processedPointsCount++;

            int dx = pt.x;
            int dz = pt.z;
            double dist = pt.dist;

            int worldX = currentOriginX + dx;
            int worldZ = currentOriginZ + dz;

            // Target ground elevation calculation (flat core plateau + smoothstep blend into surrounding terrain)
            int natY = getNaturalGroundY(serverLevel, worldX, worldZ);
            int targetY;
            if (dist <= FLAT_RADIUS) {
                targetY = currentGroundY;
            } else {
                double t = (dist - FLAT_RADIUS) / (double) (TRANSITION_RADIUS - FLAT_RADIUS);
                double s = t * t * (3.0 - 2.0 * t); // smoothstep
                targetY = (int) Math.round((1.0 - s) * currentGroundY + s * natY);
            }

            // Player safety nudge: if any player is near this column, make sure their feet stay above targetY
            if (server != null) {
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    if (Math.abs(sp.getX() - (worldX + 0.5)) < 1.5 && Math.abs(sp.getZ() - (worldZ + 0.5)) < 1.5) {
                        if (sp.getY() < targetY + 1.0) {
                            sp.teleportTo(sp.getX(), targetY + 1.0, sp.getZ());
                            sp.fallDistance = 0.0f;
                            sp.setDeltaMovement(0, 0, 0);
                        }
                    }
                }
            }

            // Air excavation above target ground (clears trees, leaves, hills, floating debris)
            int clearTop = Math.min(serverLevel.getMaxY(), Math.max(natY + 20, targetY + 24));
            for (int y = targetY + 1; y <= clearTop; y++) {
                BlockPos p = new BlockPos(worldX, y, worldZ);
                BlockState bs = serverLevel.getBlockState(p);
                if (!bs.isAir()) {
                    if (!isNearStructure(dx, dz) || !isStructureBlock(bs)) {
                        serverLevel.setBlock(p, air, 2);
                    }
                }
            }

            // Solid foundation filling below target ground (fills ravines, water, dips)
            for (int y = targetY - 1; y >= targetY - 3; y--) {
                BlockPos p = new BlockPos(worldX, y, worldZ);
                BlockState bs = serverLevel.getBlockState(p);
                if (!bs.isSolid() || bs.is(Blocks.WATER) || bs.is(Blocks.LAVA)) {
                    serverLevel.setBlock(p, dirt, 2);
                }
            }
            if (natY < targetY) {
                for (int y = natY; y < targetY; y++) {
                    BlockPos p = new BlockPos(worldX, y, worldZ);
                    serverLevel.setBlock(p, dirt, 2);
                }
            }

            // Surface block placement at target ground elevation
            BlockPos floorPos = new BlockPos(worldX, targetY, worldZ);
            BlockPos cropPos = new BlockPos(worldX, targetY + 1, worldZ);

            if (isSolidStructure(dx, dz)) {
                for (int y = Math.min(targetY, currentGroundY - 3); y <= currentGroundY; y++) {
                    BlockPos p = new BlockPos(worldX, y, worldZ);
                    if (!serverLevel.getBlockState(p).is(Blocks.COBBLESTONE)) {
                        serverLevel.setBlock(p, cobblestone, 2);
                    }
                }
                if (!serverLevel.getBlockState(cropPos).isAir()) {
                    serverLevel.setBlock(cropPos, air, 2);
                }
                continue;
            }

            if (isPath(dx, dz)) {
                BlockState pathType = ((dx + dz) % 3 == 0) ? coarseDirt : dirtPath;
                if (!serverLevel.getBlockState(floorPos).is(pathType.getBlock())) {
                    serverLevel.setBlock(floorPos, pathType, 2);
                }
                if (!serverLevel.getBlockState(cropPos).isAir()) {
                    serverLevel.setBlock(cropPos, air, 2);
                }
                continue;
            }

            double angle = Math.atan2(dz, dx);
            double shapeRadius = BASE_RADIUS * (1.0
                    + 0.25 * Math.sin(3.0 * angle + 0.8)
                    + 0.16 * Math.cos(5.0 * angle - 1.2)
                    + 0.12 * Math.sin(2.0 * angle + 2.1)
                    + 0.08 * Math.cos(7.0 * angle)
            ) + (2.0 * Math.sin(dx * 0.2) * Math.cos(dz * 0.2));

            if (dist <= shapeRadius) {
                // Inside the wheat field: place farmland and mature wheat
                if (!serverLevel.getBlockState(floorPos).is(Blocks.FARMLAND)) {
                    serverLevel.setBlock(floorPos, moistFarmland, 2);
                }
                BlockState currentCrop = serverLevel.getBlockState(cropPos);
                if (!currentCrop.is(Blocks.WHEAT) || currentCrop.getValue(CropBlock.AGE) != 7) {
                    serverLevel.setBlock(cropPos, matureWheat, 2);
                }
                totalPlacedCrops++;
            } else if (dist > shapeRadius && dist <= shapeRadius + 2.0) {
                // Outer perimeter border: coarse dirt & spaced lantern fence posts
                if (!serverLevel.getBlockState(floorPos).is(Blocks.COARSE_DIRT)) {
                    serverLevel.setBlock(floorPos, coarseDirt, 2);
                }
                if (!serverLevel.getBlockState(cropPos).isAir()) {
                    serverLevel.removeBlock(cropPos, false);
                }

                if (Math.abs(worldX * 31 + worldZ * 17) % 12 == 0) {
                    serverLevel.setBlock(cropPos, fenceState, 2);
                    serverLevel.setBlock(cropPos.above(), lanternState, 2);
                }
            } else {
                // Outer transition slope: natural grass
                if (!serverLevel.getBlockState(floorPos).is(Blocks.GRASS_BLOCK)) {
                    serverLevel.setBlock(floorPos, grassBlock, 2);
                }
                if (!serverLevel.getBlockState(cropPos).isAir()) {
                    serverLevel.removeBlock(cropPos, false);
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
        generateWindmill(serverLevel, currentOriginX + WINDMILL_OFFSET_X, currentOriginZ + WINDMILL_OFFSET_Z, currentGroundY);
        generateFarmerHouse(serverLevel, currentOriginX + HOUSE_OFFSET_X, currentOriginZ + HOUSE_OFFSET_Z, currentGroundY);

        UnharvestState state = UnharvestState.getInstance();
        state.setFieldStats(totalPlacedCrops, 0);

        if (currentMarkerFile != null) {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("centerX", currentOriginX);
                json.addProperty("centerY", currentCropY);
                json.addProperty("centerZ", currentOriginZ);
                json.addProperty("groundY", currentGroundY);
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
    private static void generateWindmill(ServerLevel level, int cx, int cz, int groundY) {
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossyBricks = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState cobblestone = Blocks.COBBLESTONE.defaultBlockState();
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState darkOakPlanks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState darkOakLog = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState spruceFence = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState whiteWool = Blocks.WOOL.white().defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();

        // 0. Clear entire windmill bounding box of any trees, logs, leaves, or obstacles, and fill foundation below
        // Sails reach dx=±6, dz=+4. A clearing radius of ±9 guarantees no trees or leaves intersect the blades or tower.
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int wx = cx + dx;
                int wz = cz + dz;
                // Solid cobblestone foundation below
                for (int y = groundY - 6; y < groundY; y++) {
                    BlockPos p = new BlockPos(wx, y, wz);
                    if (!level.getBlockState(p).isSolid()) {
                        level.setBlock(p, cobblestone, 2);
                    }
                }
                // Air clearance above (up to hub/sail peak height + 26) to wipe out any birch/oak trees
                for (int y = groundY; y <= groundY + 26; y++) {
                    BlockPos ap = new BlockPos(wx, y, wz);
                    if (!level.getBlockState(ap).isAir()) {
                        level.setBlock(ap, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 1. Base Floor and Walls (Y = groundY to groundY + 3) - 7x7 octagonal cylinder
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
    private static void generateFarmerHouse(ServerLevel level, int hx, int hz, int groundY) {
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
        int porchZ = minZ - 1;

        // 0. Clear entire house bounding box of any trees, logs, leaves, or obstacles, and fill foundation below
        for (int x = minX - 4; x <= maxX + 4; x++) {
            for (int z = porchZ - 4; z <= maxZ + 4; z++) {
                // Foundation below
                for (int y = groundY - 6; y < groundY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isSolid()) {
                        level.setBlock(p, cobblestone, 2);
                    }
                }
                // Air clearance above (up to roof/chimney smoke height + 18)
                for (int y = groundY; y <= groundY + 18; y++) {
                    BlockPos ap = new BlockPos(x, y, z);
                    if (!level.getBlockState(ap).isAir()) {
                        level.setBlock(ap, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 1. Porch floor at groundY
        for (int x = hx - 2; x <= hx + 2; x++) {
            level.setBlock(new BlockPos(x, groundY, porchZ), cobblestone, 2);
            level.setBlock(new BlockPos(x, groundY + 1, porchZ), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, groundY + 2, porchZ), Blocks.AIR.defaultBlockState(), 2);
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
        level.setBlock(new BlockPos(hx - 2, groundY + 1, porchZ), spruceFence, 2);
        level.setBlock(new BlockPos(hx - 2, groundY + 2, porchZ), spruceFence, 2);
        level.setBlock(new BlockPos(hx + 2, groundY + 1, porchZ), spruceFence, 2);
        level.setBlock(new BlockPos(hx + 2, groundY + 2, porchZ), spruceFence, 2);
        for (int x = hx - 2; x <= hx + 2; x++) {
            level.setBlock(new BlockPos(x, groundY + 3, porchZ), sprucePlanks, 2);
        }
        // Lanterns hanging directly beneath the porch roof
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
        int originX = currentOriginX;
        int originZ = currentOriginZ;

        int witheredCount = 0;
        int remainingCropCount = 0;

        for (int dx = -maxScan; dx <= maxScan; dx++) {
            for (int dz = -maxScan; dz <= maxScan; dz++) {
                int x = originX + dx;
                int z = originZ + dz;
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
                json.addProperty("centerX", currentOriginX);
                json.addProperty("centerY", cropY);
                json.addProperty("centerZ", currentOriginZ);
                json.addProperty("groundY", groundY);
                json.addProperty("totalCrops", total);
                Files.writeString(markerFile, json.toString());
            } catch (Exception ignored) {}
        }

        return new RescanResult(total, witheredCount, remainingCropCount);
    }

    public static void forceRegenerateField(MinecraftServer server, ServerLevel serverLevel, Player player) {
        Path saveDir = server.getWorldPath(LevelResource.ROOT);
        UnharvestState.getInstance().loadForWorld(saveDir);
        Path markerFile = saveDir.resolve(MARKER_FILE_NAME);
        try {
            Files.deleteIfExists(markerFile);
        } catch (Exception ignored) {}
        stopGeneration();
        startGeneration(server, serverLevel, markerFile, player);
    }

    public static void clearCache() {
        stopGeneration();
        currentOriginX = 0;
        currentOriginZ = 0;
        currentGroundY = 64;
        currentCropY = 65;
        currentMarkerFile = null;
        totalPlacedCrops = 0;
        processedPointsCount = 0;
        pendingPoints.clear();
    }
}
