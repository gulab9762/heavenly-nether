package com.unharvest;

import com.mojang.blaze3d.platform.InputConstants;
import com.unharvest.gui.UnharvestUpgradeScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Core manager for the Unharvest reverse-farming gameplay mechanics:
 * world validation, delayed visual contagion spread, corrupted soil generation,
 * cooldown gating, and Locust Swarm automation.
 */
public class UnharvestManager {

    public static final KeyMapping UPGRADE_KEY = new KeyMapping(
            "key.unharvest.upgrades",
            InputConstants.KEY_U,
            KeyMapping.Category.GAMEPLAY
    );

    private static long clientTickCount = 0;
    private static boolean nextWorldIsUnharvest = false;
    private static Boolean cachedIsUnharvestWorld = null;
    private static String cachedWorldName = null;

    // Delayed Contagion Wave Queue for smooth visual spreading
    private static class ScheduledWither {
        final BlockPos pos;
        int delayTicks;

        ScheduledWither(BlockPos pos, int delayTicks) {
            this.pos = pos;
            this.delayTicks = delayTicks;
        }
    }

    private static final List<ScheduledWither> WITHER_QUEUE = new ArrayList<>();
    private static final Set<BlockPos> PENDING_POSITIONS = new HashSet<>();

    public static void markNextWorldAsUnharvest() {
        nextWorldIsUnharvest = true;
    }

    /**
     * Checks if the active world is an Unharvest world.
     * Keeps Unharvest completely dormant in regular survival worlds.
     */
    public static boolean isUnharvestWorld(Level level) {
        if (level == null) return false;

        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null) {
            return cachedIsUnharvestWorld != null && cachedIsUnharvestWorld;
        }

        String worldName = server.getWorldData().getLevelName();
        if (cachedIsUnharvestWorld != null && Objects.equals(cachedWorldName, worldName)) {
            return cachedIsUnharvestWorld;
        }

        cachedWorldName = worldName;
        Path saveDir = server.getWorldPath(LevelResource.ROOT);

        // 1. World created via Unharvest preset
        if (nextWorldIsUnharvest) {
            nextWorldIsUnharvest = false;
            cachedIsUnharvestWorld = true;
            try {
                Files.writeString(saveDir.resolve("unharvest_world.json"), "{\"unharvest\": true}");
            } catch (Exception ignored) {}
            return true;
        }

        // 2. Check if unharvest marker files exist in save directory
        if (Files.exists(saveDir.resolve("unharvest_world.json")) || Files.exists(saveDir.resolve("unharvest_field_generated.json"))) {
            cachedIsUnharvestWorld = true;
            return true;
        }

        // 3. Flat world check: Check if wheat/wither rose is present at spawn
        if (server.getWorldData().isFlatWorld()) {
            int checkY = UnharvestFieldGenerator.getWheatY(level);
            BlockState checkState = level.getBlockState(new BlockPos(0, checkY, 0));
            if (checkState.is(Blocks.WHEAT) || checkState.is(Blocks.WITHER_ROSE)) {
                cachedIsUnharvestWorld = true;
                return true;
            }
        }

        cachedIsUnharvestWorld = false;
        return false;
    }

    public static void clearWorldCache() {
        cachedIsUnharvestWorld = null;
        cachedWorldName = null;
        WITHER_QUEUE.clear();
        PENDING_POSITIONS.clear();
    }

    /**
     * Intercepts left-clicks on crops with the Withering Scythe.
     * Withers the primary crop instantly and cascades surrounding/chained crops in delayed waves.
     */
    public static boolean onLeftClickBlock(Level level, Player player, BlockPos pos, Direction direction) {
        if (level == null || player == null || pos == null) {
            return false;
        }

        if (!isUnharvestWorld(level)) {
            return false;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!UnharvestItems.isWitheringScythe(heldItem)) {
            return false;
        }

        BlockState hitState = level.getBlockState(pos);
        if (hitState.is(Blocks.WITHER_ROSE)) {
            return true; // Cancel breaking so withered roses remain intact
        }

        // If player clicked on farmland/ground beneath a crop, redirect target to the crop above!
        if (!isCrop(hitState)) {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (isCrop(aboveState)) {
                pos = above;
                hitState = aboveState;
            } else if (aboveState.is(Blocks.WITHER_ROSE)) {
                return true; // Crop above is already withered
            } else if (hitState.is(Blocks.FARMLAND) || hitState.is(Blocks.COARSE_DIRT) || hitState.is(Blocks.SOUL_SOIL)) {
                return true; // Protect field ground from breaking
            } else {
                return false;
            }
        }

        // Anti-spam cooldown check
        if (player.getCooldowns().isOnCooldown(heldItem)) {
            return true; // Prevent player from spamming normal block breaking
        }

        UnharvestState state = UnharvestState.getInstance();

        // Apply scythe cooldown and swing animation
        int cooldownTicks = state.getCooldownTicks();
        player.getCooldowns().addCooldown(heldItem, cooldownTicks);
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);

        // 1. Immediate primary strike
        witherCrop(level, pos);
        long earned = state.getYieldMultiplier();
        state.addEssence(earned);
        state.recordWithered(1);

        level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS,
                0.35f, 1.8f, false
        );
        level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                0.8f, 0.7f, false
        );

        // 2. Queue AoE Radius crops in gradual delay waves
        int radius = state.getRadius();
        if (radius > 0) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos target = pos.offset(dx, 0, dz);
                    if (!isCrop(level.getBlockState(target)) && isCrop(level.getBlockState(target.above()))) {
                        target = target.above();
                    }
                    if (isCrop(level.getBlockState(target)) && !PENDING_POSITIONS.contains(target)) {
                        PENDING_POSITIONS.add(target.immutable());
                        int dist = Math.max(Math.abs(dx), Math.abs(dz));
                        int delay = dist * 2; // 2 ticks per distance step for smooth wave
                        WITHER_QUEUE.add(new ScheduledWither(target.immutable(), delay));
                    }
                }
            }
        }

        // 3. Queue Contagion (Spores) chain reactions with delayed cascade
        float contagionChance = state.getContagionChance();
        if (contagionChance > 0.0f) {
            RandomSource rand = level.getRandom();
            Set<BlockPos> currentWave = new HashSet<>();
            currentWave.add(pos);

            int cascadeSteps = 3;
            for (int step = 1; step <= cascadeSteps; step++) {
                Set<BlockPos> nextWave = new HashSet<>();
                int delay = (radius + step) * 2;

                for (BlockPos source : currentWave) {
                    if (rand.nextFloat() < contagionChance) {
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos side = source.relative(dir);
                            if (!isCrop(level.getBlockState(side)) && isCrop(level.getBlockState(side.above()))) {
                                side = side.above();
                            }
                            if (isCrop(level.getBlockState(side)) && !PENDING_POSITIONS.contains(side)) {
                                PENDING_POSITIONS.add(side.immutable());
                                WITHER_QUEUE.add(new ScheduledWither(side.immutable(), delay));
                                nextWave.add(side);
                            }
                        }
                    }
                }
                currentWave = nextWave;
                if (currentWave.isEmpty()) break;
            }
        }

        // Feedback action-bar
        player.sendOverlayMessage(
                Component.literal(String.format("§5§lUnharvest §7| §d+%d Essence §7(Total: §d%s§7) | §aCleared: §f%.1f%%",
                        earned, state.getFormattedEssence(), state.getClearedPercentage()))
        );

        return true;
    }

    /**
     * Opens upgrades menu on right-click with Withering Scythe.
     */
    public static boolean onRightClick(Level level, Player player, InteractionHand hand) {
        if (player == null || level == null) return false;
        if (!isUnharvestWorld(level)) return false;

        ItemStack stack = player.getItemInHand(hand);
        if (UnharvestItems.isWitheringScythe(stack)) {
            openUpgradesScreen();
            return true;
        }

        return false;
    }

    /**
     * Ticks delayed contagion spread queue, idle automation, field setup, and keybinds.
     */
    public static void tick(Level level, Player player) {
        if (level == null || player == null) return;
        if (!isUnharvestWorld(level)) return;

        clientTickCount++;

        // Listen for Upgrade Keybind [U]
        while (UPGRADE_KEY.consumeClick()) {
            openUpgradesScreen();
        }

        // 1. Process delayed withering spread queue (for beautiful visual cascading)
        if (!WITHER_QUEUE.isEmpty()) {
            tickWitherQueue(level, player);
        }

        // 2. Initial field setup and scythe verification
        if (clientTickCount % 40 == 1) {
            UnharvestFieldGenerator.ensureFieldGenerated(level);
            UnharvestItems.ensurePlayerHasScythe(player);
        }

        // 3. Locust Swarm (Idle Automation)
        UnharvestState state = UnharvestState.getInstance();
        if (state.getSwarmLevel() > 0 && clientTickCount % state.getSwarmPeriodTicks() == 0) {
            tickLocustSwarm(level, player, state);
        }
    }

    private static void tickWitherQueue(Level level, Player player) {
        UnharvestState state = UnharvestState.getInstance();
        Iterator<ScheduledWither> it = WITHER_QUEUE.iterator();
        int witheredThisTick = 0;

        while (it.hasNext()) {
            ScheduledWither item = it.next();
            item.delayTicks--;

            if (item.delayTicks <= 0) {
                it.remove();
                PENDING_POSITIONS.remove(item.pos);

                if (witherCrop(level, item.pos)) {
                    witheredThisTick++;
                }
            }
        }

        if (witheredThisTick > 0) {
            long earned = (long) witheredThisTick * state.getYieldMultiplier();
            state.addEssence(earned);
            state.recordWithered(witheredThisTick);

            // Soft decay sound
            BlockPos p = player.blockPosition();
            level.playLocalSound(
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                    SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                    0.4f, 0.6f, false
            );
        }
    }

    /**
     * Periodic passive locust swarm that auto-seeks and withers nearby wheat.
     */
    private static void tickLocustSwarm(Level level, Player player, UnharvestState state) {
        int countToWither = state.getSwarmWitherCount();
        BlockPos playerPos = player.blockPosition();

        List<BlockPos> candidates = new ArrayList<>();
        int searchRadius = 24;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                BlockPos check = new BlockPos(playerPos.getX() + x, UnharvestFieldGenerator.getWheatY(level), playerPos.getZ() + z);
                if (isCrop(level.getBlockState(check)) && !PENDING_POSITIONS.contains(check)) {
                    candidates.add(check);
                }
            }
        }

        if (candidates.isEmpty()) return;
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(playerPos)));

        int limit = Math.min(countToWither, candidates.size());
        for (int i = 0; i < limit; i++) {
            BlockPos target = candidates.get(i);
            PENDING_POSITIONS.add(target.immutable());
            WITHER_QUEUE.add(new ScheduledWither(target.immutable(), (i + 1) * 2));
        }
    }

    /**
     * Withers a crop into a Wither Rose and converts the soil beneath into corrupted coarse dirt or soul soil.
     * Updates both ClientLevel and Integrated ServerLevel to guarantee full synchronization.
     */
    public static boolean witherCrop(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;

        BlockState current = level.getBlockState(pos);
        if (!isCrop(current)) {
            return false;
        }

        BlockState witherRose = Blocks.WITHER_ROSE.defaultBlockState();
        BlockPos below = pos.below();
        RandomSource random = level.getRandom();
        BlockState corruptedSoil = (random.nextFloat() < 0.25f)
                ? Blocks.SOUL_SOIL.defaultBlockState()
                : Blocks.COARSE_DIRT.defaultBlockState();

        // 1. Update Client Level
        level.setBlock(pos, witherRose, 3);
        level.setBlock(below, corruptedSoil, 3);

        // 2. Update Integrated Server Level (authoritative world state!)
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            ServerLevel serverLevel = server.overworld();
            if (serverLevel != null) {
                server.execute(() -> {
                    serverLevel.setBlock(pos, witherRose, 3);
                    serverLevel.setBlock(below, corruptedSoil, 3);
                });
            }
        }

        // 3. Visual necrotic bursts
        for (int i = 0; i < 5; i++) {
            double px = pos.getX() + random.nextDouble();
            double py = pos.getY() + random.nextDouble() * 0.7;
            double pz = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SQUID_INK, px, py, pz, 0, 0.05, 0);
            level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.03, 0);
            level.addParticle(ParticleTypes.PORTAL, px, py, pz, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
        }

        return true;
    }

    public static boolean isCrop(BlockState state) {
        if (state == null) return false;
        return state.is(Blocks.WHEAT)
                || state.is(Blocks.CARROTS)
                || state.is(Blocks.POTATOES)
                || state.is(Blocks.BEETROOTS)
                || (state.getBlock() instanceof CropBlock);
    }

    public static void openUpgradesScreen() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreenAndShow(new UnharvestUpgradeScreen(client.gui.screen())));
    }
}
