package com.gulab.jrunner;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Execution context provided to all live-compiled Java code.
 * Fields and static helpers can be used directly without qualification.
 */
public class JavaContext {
    public static ServerPlayer player;
    public static ServerLevel world;
    public static MinecraftServer server;
    public static BlockPos pos;
    public static CommandSourceStack source;

    public JavaContext(ServerPlayer p, ServerLevel w, MinecraftServer s, BlockPos bp, CommandSourceStack src) {
        player = p;
        world = w;
        server = s;
        pos = bp;
        source = src;
    }

    /**
     * Send formatted output to the caller.
     */
    public static void echo(Object message) {
        String str = message == null ? "null" : message.toString();
        if (source != null) {
            source.sendSuccess(() -> Component.literal("§b[Java] §f" + str), false);
        } else if (player != null) {
            player.sendSystemMessage(Component.literal("§b[Java] §f" + str));
        } else {
            Constants.LOG.info("[Live Java] {}", str);
        }
    }

    public static void print(Object message) {
        echo(message);
    }

    /**
     * Broadcast a message to all players on the server.
     */
    public static void broadcast(Object message) {
        String str = message == null ? "null" : message.toString();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("§6[Broadcast] §f" + str), false);
        }
    }

    /**
     * Give items to the executing player.
     */
    public static void give(Item item, int count) {
        if (player != null) {
            player.getInventory().add(new ItemStack(item, count));
            echo("Gave " + count + "x " + item);
        }
    }

    public static void give(ItemStack stack) {
        if (player != null) {
            player.getInventory().add(stack);
            echo("Gave " + stack);
        }
    }

    /**
     * Set a block in the current world.
     */
    public static boolean setBlock(BlockPos targetPos, Block block) {
        if (world != null) {
            return world.setBlockAndUpdate(targetPos, block.defaultBlockState());
        }
        return false;
    }

    public static boolean setBlock(int x, int y, int z, Block block) {
        return setBlock(new BlockPos(x, y, z), block);
    }

    public static boolean setBlock(BlockPos targetPos, BlockState state) {
        if (world != null) {
            return world.setBlockAndUpdate(targetPos, state);
        }
        return false;
    }

    /**
     * Get the block at a position.
     */
    public static Block getBlock(BlockPos targetPos) {
        if (world != null) {
            return world.getBlockState(targetPos).getBlock();
        }
        return null;
    }

    public static BlockState getBlockState(BlockPos targetPos) {
        if (world != null) {
            return world.getBlockState(targetPos);
        }
        return null;
    }

    /**
     * Teleport the player.
     */
    public static void teleport(double x, double y, double z) {
        if (player != null && world != null) {
            player.teleportTo(world, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
            echo("Teleported to (" + x + ", " + y + ", " + z + ")");
        }
    }

    /**
     * Strike lightning at a position.
     */
    public static void strikeLightning(BlockPos targetPos) {
        if (world != null) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (bolt != null) {
                bolt.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                world.addFreshEntity(bolt);
            }
        }
    }

    /**
     * Cause an explosion.
     */
    public static void explode(double x, double y, double z, float power) {
        if (world != null) {
            world.explode(null, x, y, z, power, Level.ExplosionInteraction.BLOCK);
        }
    }

    /**
     * Set world time of day.
     */
    public static void setTime(long dayTime) {
        if (world != null) {
            world.setDayTime(dayTime);
            echo("World day time set to " + dayTime);
        }
    }

    /**
     * Heal the player.
     */
    public static void heal() {
        if (player != null) {
            player.setHealth(player.getMaxHealth());
            echo("Player healed to max health.");
        }
    }
}

