package com.unharvest;

import com.mojang.blaze3d.platform.InputConstants;
import com.unharvest.gui.UnharvestUpgradeScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Core manager for the Unharvest reverse-farming gameplay mechanics:
 * world validation, delayed visual contagion spread, corrupted soil generation,
 * cooldown gating, and active placeable Locust Swarm ability.
 */
public class UnharvestManager {

    public static final KeyMapping UPGRADE_KEY = new KeyMapping(
            "key.unharvest.upgrades",
            InputConstants.KEY_U,
            KeyMapping.Category.GAMEPLAY
    );

    public static final KeyMapping SWARM_KEY = new KeyMapping(
            "key.unharvest.swarm",
            InputConstants.KEY_R,
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

    // Active Placeable Swarms
    public static class ActiveSwarm {
        public final BlockPos center;
        public final int radius;
        public final int devourRate;
        public int ticksRemaining;

        public ActiveSwarm(BlockPos center, int radius, int devourRate, int durationTicks) {
            this.center = center;
            this.radius = radius;
            this.devourRate = devourRate;
            this.ticksRemaining = durationTicks;
        }
    }

    private static final List<ActiveSwarm> ACTIVE_SWARMS = new ArrayList<>();
    private static int swarmCooldownTicks = 0;
    private static boolean wasInitializedThisSession = false;
    private static boolean lastUpgradeAvailable = false;

    public static void playUpgradeNotificationSound() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getSoundManager() != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f));
        }
    }

    // Rolling essence tracker so cascading waves dynamically count up on screen
    private static long recentEarnedEssence = 0;
    private static int recentResetCooldown = 0;

    public static void registerEssenceEarned(Player player, int cropsWithered) {
        if (cropsWithered <= 0) return;
        UnharvestState state = UnharvestState.getInstance();
        long earned = (long) cropsWithered * state.getYieldMultiplier();
        state.addEssence(earned);
        state.recordWithered(cropsWithered);

        recentEarnedEssence += earned;
        recentResetCooldown = 35; // Accumulate for 1.75s across the full wave

        if (player != null) {
            player.sendOverlayMessage(
                    Component.literal(String.format("§5§lUnharvest §7| §d+%,d Essence §7(Total: §d%s§7) | §aCleared: §f%.1f%%",
                            recentEarnedEssence, state.getFormattedEssence(), state.getClearedPercentage()))
            );
        }
    }

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
        ACTIVE_SWARMS.clear();
        swarmCooldownTicks = 0;
        wasInitializedThisSession = false;
        lastUpgradeAvailable = false;
        UnharvestFieldGenerator.clearCache();
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
        registerEssenceEarned(player, 1);

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

            int cascadeSteps = state.getContagionCascadeSteps();
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

        return true;
    }

    /**
     * Handles right-click with Withering Scythe:
     * Right-click opens the Upgrades Menu. Swarm ability is strictly triggered via keybind [R].
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

    // --- PLACEABLE LOCUST SWARM ABILITY ---

    public static boolean canDeploySwarm() {
        UnharvestState state = UnharvestState.getInstance();
        return state.isSwarmUnlocked() && swarmCooldownTicks <= 0;
    }

    public static int getSwarmCooldownRemaining() {
        return swarmCooldownTicks;
    }

    public static int getMaxSwarmCooldown() {
        return UnharvestState.getInstance().getSwarmCooldownTicks();
    }

    public static float getSwarmCooldownFraction() {
        int max = getMaxSwarmCooldown();
        if (max <= 0) return 0.0f;
        return (float) swarmCooldownTicks / (float) max;
    }

    public static boolean isSwarmReady() {
        return UnharvestState.getInstance().isSwarmUnlocked() && swarmCooldownTicks <= 0;
    }

    public static boolean deploySwarm(Level level, Player player) {
        if (level == null || player == null) return false;
        if (!isUnharvestWorld(level)) return false;

        UnharvestState state = UnharvestState.getInstance();
        if (!state.isSwarmUnlocked()) {
            player.sendOverlayMessage(Component.literal("§c[Locust Swarm] Locked! Unlock in Upgrades Menu [U]."));
            return false;
        }
        if (swarmCooldownTicks > 0) {
            player.sendOverlayMessage(Component.literal(String.format("§c[Locust Swarm] On cooldown! (%.1fs)", swarmCooldownTicks / 20.0f)));
            return false;
        }

        HitResult hit = player.pick(24.0, 0.0f, false);
        BlockPos target;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            target = ((BlockHitResult) hit).getBlockPos();
            if (!isCrop(level.getBlockState(target)) && isCrop(level.getBlockState(target.above()))) {
                target = target.above();
            }
        } else {
            BlockPos forward = player.blockPosition().relative(player.getDirection(), 6);
            target = new BlockPos(forward.getX(), UnharvestFieldGenerator.getWheatY(level), forward.getZ());
        }

        int radius = state.getSwarmRadius();
        int devourRate = state.getSwarmDevourRate();
        ACTIVE_SWARMS.add(new ActiveSwarm(target.immutable(), radius, devourRate, 140)); // 7 seconds
        swarmCooldownTicks = state.getSwarmCooldownTicks();

        // Sound effects
        level.playLocalSound(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                SoundEvents.BEE_LOOP, SoundSource.PLAYERS,
                1.2f, 0.6f, false
        );

        player.sendOverlayMessage(Component.literal(String.format("§2§l🦗 Locust Swarm Unleashed! §aDevouring %d-block zone for 7s!", radius)));
        return true;
    }

    /**
     * Ticks delayed contagion spread queue, active placeable swarms, field setup, and keybinds.
     */
    public static void tick(Level level, Player player) {
        if (level == null || player == null) return;
        if (!isUnharvestWorld(level)) return;

        clientTickCount++;

        // Listen for Upgrade Keybind [U]
        while (UPGRADE_KEY.consumeClick()) {
            openUpgradesScreen();
        }

        // Listen for Swarm Ability Keybind [R]
        while (SWARM_KEY.consumeClick()) {
            deploySwarm(level, player);
        }

        // Swarm cooldown tick
        if (swarmCooldownTicks > 0) {
            swarmCooldownTicks--;
        }

        // Rolling essence overlay decay
        if (recentResetCooldown > 0) {
            recentResetCooldown--;
            if (recentResetCooldown == 0) {
                recentEarnedEssence = 0;
            }
        }

        // 0. Progressive chunked field generation (populating world across ticks without lag)
        if (UnharvestFieldGenerator.isGenerating()) {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                if (serverLevel != null) {
                    UnharvestFieldGenerator.tickGeneration(server, serverLevel, player);
                }
            }
        }

        // 1. Process delayed withering spread queue (for beautiful visual cascading)
        if (!WITHER_QUEUE.isEmpty()) {
            tickWitherQueue(level, player);
        }

        // 2. Process active placed Locust Swarms
        if (!ACTIVE_SWARMS.isEmpty()) {
            tickActiveSwarms(level, player);
        }

        // 3. Initial field setup and strict inventory lock verification
        if (clientTickCount % 20 == 1) {
            UnharvestFieldGenerator.ensureFieldGenerated(level);
            UnharvestItems.lockAndCleanseInventory(player);
        }

        // 4. Upgrade availability notification sound
        UnharvestState state = UnharvestState.getInstance();
        if (!wasInitializedThisSession) {
            wasInitializedThisSession = true;
            lastUpgradeAvailable = state.hasAvailableUpgrade();
        } else {
            boolean currentlyAvailable = state.hasAvailableUpgrade();
            if (!lastUpgradeAvailable && currentlyAvailable) {
                playUpgradeNotificationSound();
            }
            lastUpgradeAvailable = currentlyAvailable;
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
            registerEssenceEarned(player, witheredThisTick);
        }
    }

    /**
     * Ticks active placed locust swarms, spawning buzzing particle vortex
     * and devouring crops inside its area of effect.
     */
    private static void tickActiveSwarms(Level level, Player player) {
        UnharvestState state = UnharvestState.getInstance();
        RandomSource random = level.getRandom();
        Iterator<ActiveSwarm> it = ACTIVE_SWARMS.iterator();

        while (it.hasNext()) {
            ActiveSwarm swarm = it.next();
            swarm.ticksRemaining--;

            // 1. Swarm particle vortex
            for (int i = 0; i < 7; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * swarm.radius;
                double px = swarm.center.getX() + 0.5 + Math.cos(angle) * r;
                double py = swarm.center.getY() + 0.2 + random.nextDouble() * 2.0;
                double pz = swarm.center.getZ() + 0.5 + Math.sin(angle) * r;

                // Dark swirling swarm particles
                level.addParticle(ParticleTypes.SQUID_INK, px, py, pz, -Math.sin(angle) * 0.1, 0.05, Math.cos(angle) * 0.1);
                level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.03, 0);
                if (random.nextFloat() < 0.35f) {
                    level.addParticle(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF40B040), px, py, pz, 0, 0.05, 0);
                }
            }

            // 2. Devour crops every 3 ticks
            if (swarm.ticksRemaining % 3 == 0) {
                int withered = 0;
                int maxToWither = swarm.devourRate;

                List<BlockPos> localCrops = new ArrayList<>();
                for (int dx = -swarm.radius; dx <= swarm.radius; dx++) {
                    for (int dz = -swarm.radius; dz <= swarm.radius; dz++) {
                        if (dx * dx + dz * dz <= swarm.radius * swarm.radius) {
                            BlockPos check = new BlockPos(swarm.center.getX() + dx, swarm.center.getY(), swarm.center.getZ() + dz);
                            if (isCrop(level.getBlockState(check))) {
                                localCrops.add(check);
                            }
                        }
                    }
                }

                Collections.shuffle(localCrops);
                for (BlockPos target : localCrops) {
                    if (witherCrop(level, target)) {
                        withered++;
                        if (withered >= maxToWither) break;
                    }
                }

                if (withered > 0) {
                    registerEssenceEarned(player, withered);

                    level.playLocalSound(
                            swarm.center.getX() + 0.5, swarm.center.getY() + 0.5, swarm.center.getZ() + 0.5,
                            SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                            0.5f, 1.4f, false
                    );
                }
            }

            if (swarm.ticksRemaining <= 0) {
                it.remove();
            }
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

        // 4. Soul / essence particles streaming from withered crop into the player
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            double targetX = player.getX();
            double targetY = player.getY() + 0.85; // Player chest / hands height
            double targetZ = player.getZ();

            double startX = pos.getX() + 0.5;
            double startY = pos.getY() + 0.35;
            double startZ = pos.getZ() + 0.5;

            double dx = targetX - startX;
            double dy = targetY - startY;
            double dz = targetZ - startZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > 0.1) {
                double speed = 0.45;
                double vx = (dx / dist) * speed;
                double vy = (dy / dist) * speed + 0.05;
                double vz = (dz / dist) * speed;

                level.addParticle(ParticleTypes.SCULK_SOUL, startX, startY, startZ, vx, vy, vz);
                level.addParticle(ParticleTypes.SOUL, startX, startY, startZ, vx * 0.85, vy * 0.85, vz * 0.85);
                level.addParticle(ParticleTypes.WITCH, startX, startY, startZ, vx * 0.7, vy * 0.7, vz * 0.7);
            }
        }

        // 5. Authentic wheat harvest sound for each wheat crop getting withered (primary and chain cascades)
        float harvestPitch = 0.88f + (level.getRandom().nextFloat() * 0.24f);
        level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                1.0f, harvestPitch, false
        );

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
