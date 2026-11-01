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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Core manager for the Unharvest reverse-farming gameplay mechanics:
 * world validation (Unharvest world only), Withering Scythe interactions,
 * cooldown gating, radius contagion, Locust Swarm automation, and field status tracking.
 */
public class UnharvestManager {

    public static final KeyMapping UPGRADE_KEY = new KeyMapping(
            "key.unharvest.upgrades",
            InputConstants.KEY_U,
            KeyMapping.Category.GAMEPLAY
    );

    private static long clientTickCount = 0;
    private static Boolean cachedIsUnharvestWorld = null;
    private static String cachedWorldName = null;

    /**
     * Checks if the active world is an Unharvest world.
     * Returns false for standard vanilla worlds so Unharvest stays dormant and gives no scythe.
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

        // 1. Check if unharvest marker files exist in the world save folder
        if (Files.exists(saveDir.resolve("unharvest_world.json")) || Files.exists(saveDir.resolve("unharvest_field_generated.json"))) {
            cachedIsUnharvestWorld = true;
            return true;
        }

        // 2. Check if chunk generator uses the unharvest_overworld noise settings
        ServerLevel overworld = server.overworld();
        if (overworld != null && overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGen) {
            String settingsKey = noiseGen.generatorSettings()
                    .unwrapKey()
                    .map(k -> k.identifier().toString())
                    .orElse("");

            if ("unharvest:unharvest_overworld".equals(settingsKey)) {
                cachedIsUnharvestWorld = true;
                try {
                    Files.writeString(saveDir.resolve("unharvest_world.json"), "{\"unharvest\": true}");
                } catch (Exception ignored) {}
                return true;
            }
        }

        cachedIsUnharvestWorld = false;
        return false;
    }

    public static void clearWorldCache() {
        cachedIsUnharvestWorld = null;
        cachedWorldName = null;
        UnharvestFieldGenerator.clearCache();
    }

    /**
     * Called when the player left-clicks (attacks/punches) a block.
     * Intercepts wheat punches with the Withering Scythe and converts crops into Wither Roses.
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

        if (!isCrop(hitState)) {
            return false;
        }

        // Anti-spam cooldown check
        if (player.getCooldowns().isOnCooldown(heldItem)) {
            return true; // Cancel block breaking so player cannot spam
        }

        UnharvestState state = UnharvestState.getInstance();

        // Apply scythe cooldown
        int cooldownTicks = state.getCooldownTicks();
        player.getCooldowns().addCooldown(heldItem, cooldownTicks);
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);

        // Track all blocks withered in this strike
        Set<BlockPos> toWither = new LinkedHashSet<>();
        toWither.add(pos.immutable());

        // 1. Wither Radius (AoE reach)
        int radius = state.getRadius();
        if (radius > 0) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    // Check around same height
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (isCrop(level.getBlockState(neighbor))) {
                            toWither.add(neighbor.immutable());
                            break;
                        }
                    }
                }
            }
        }

        // 2. Contagion (Spores) Chain-Reaction
        float contagionChance = state.getContagionChance();
        if (contagionChance > 0.0f) {
            RandomSource rand = level.getRandom();
            List<BlockPos> chained = new ArrayList<>();
            for (BlockPos target : toWither) {
                if (rand.nextFloat() < contagionChance) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos side = target.relative(dir).above(dy);
                            if (!toWither.contains(side) && isCrop(level.getBlockState(side))) {
                                chained.add(side.immutable());
                                break;
                            }
                        }
                    }
                }
            }
            toWither.addAll(chained);
        }

        // Execute withering on all gathered crops
        int witheredCount = 0;
        for (BlockPos targetPos : toWither) {
            if (witherCrop(level, targetPos)) {
                witheredCount++;
            }
        }

        if (witheredCount > 0) {
            long earnedEssence = (long) witheredCount * state.getYieldMultiplier();
            state.addEssence(earnedEssence);
            state.recordWithered(witheredCount);

            // Play impact audio
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

            // Feedback action-bar
            player.sendOverlayMessage(
                    Component.literal(String.format("§5§lUnharvest §7| §d+%d Essence §7(Total: §d%s§7) | §aCleared: §f%.1f%% §7(§e[U] Upgrades§7)",
                            earnedEssence, state.getFormattedEssence(), state.getClearedPercentage()))
            );
        }

        return true;
    }

    /**
     * Called when the player right-clicks with an item.
     * Opens the Unharvest Plague Upgrades Menu when holding the Withering Scythe.
     */
    public static boolean onRightClick(Level level, Player player, InteractionHand hand) {
        if (player == null || level == null) return false;

        if (!isUnharvestWorld(level)) {
            return false;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (UnharvestItems.isWitheringScythe(stack)) {
            openUpgradesScreen();
            return true;
        }

        return false;
    }

    /**
     * Ticks idle automation (Locust Swarm), verifies field setup, and listens for keybinds.
     * Strictly runs only in Unharvest worlds.
     */
    public static void tick(Level level, Player player) {
        if (level == null || player == null) return;

        if (!isUnharvestWorld(level)) {
            return;
        }

        clientTickCount++;

        // Listen for Upgrade Keybind [U]
        while (UPGRADE_KEY.consumeClick()) {
            openUpgradesScreen();
        }

        // 1. Initial village field setup and scythe verification
        if (clientTickCount % 40 == 1) {
            UnharvestFieldGenerator.ensureFieldGenerated(level);
            UnharvestItems.ensurePlayerHasScythe(player);
        }

        // 2. Locust Swarm (Idle Automation)
        UnharvestState state = UnharvestState.getInstance();
        if (state.getSwarmLevel() > 0 && clientTickCount % state.getSwarmPeriodTicks() == 0) {
            tickLocustSwarm(level, player, state);
        }
    }

    /**
     * Periodic passive locust swarm that auto-seeks and withers nearby crops across rolling terrain.
     */
    private static void tickLocustSwarm(Level level, Player player, UnharvestState state) {
        int countToWither = state.getSwarmWitherCount();
        BlockPos playerPos = player.blockPosition();

        List<BlockPos> candidates = new ArrayList<>();
        int searchRadius = 24;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos check = new BlockPos(playerPos.getX() + x, playerPos.getY() + dy, playerPos.getZ() + z);
                    if (isCrop(level.getBlockState(check))) {
                        candidates.add(check);
                        break;
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        // Sort by distance to player
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(playerPos)));

        int withered = 0;
        int limit = Math.min(countToWither, candidates.size());

        for (int i = 0; i < limit; i++) {
            BlockPos target = candidates.get(i);
            if (witherCrop(level, target)) {
                withered++;

                // Swarm particles: black ink and smoke flying upwards
                level.addParticle(ParticleTypes.SQUID_INK, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 0, 0.1, 0);
                level.addParticle(ParticleTypes.SMOKE, target.getX() + 0.5, target.getY() + 0.8, target.getZ() + 0.5, 0, 0.05, 0);
            }
        }

        if (withered > 0) {
            long earned = (long) withered * state.getYieldMultiplier();
            state.addEssence(earned);
            state.recordWithered(withered);

            level.playLocalSound(
                    playerPos.getX() + 0.5, playerPos.getY() + 0.5, playerPos.getZ() + 0.5,
                    SoundEvents.BEE_POLLINATE, SoundSource.PLAYERS,
                    0.4f, 0.5f, false
            );
        }
    }

    /**
     * Wither a single crop block into a Wither Rose and turn the soil into coarse dirt.
     */
    public static boolean witherCrop(Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!isCrop(current)) {
            return false;
        }

        // Place Wither Rose
        level.setBlock(pos, Blocks.WITHER_ROSE.defaultBlockState(), 3);

        // Turn soil underneath into coarse dirt
        BlockPos below = pos.below();
        if (level.getBlockState(below).is(Blocks.FARMLAND)) {
            level.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), 3);
        }

        // Visual necrotic burst
        RandomSource random = level.getRandom();
        for (int i = 0; i < 4; i++) {
            double px = pos.getX() + random.nextDouble();
            double py = pos.getY() + random.nextDouble() * 0.6;
            double pz = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.04, 0);
            level.addParticle(ParticleTypes.PORTAL, px, py, pz, (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
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
