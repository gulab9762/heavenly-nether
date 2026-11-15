package com.igxxd.structentity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.igxxd.structentity.generator.GridGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GridCommands {

    public static final int DEFAULT_WIDTH = 500;
    public static final int DEFAULT_SPACING = 3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("grid")
                        .requires(GridCommands::hasPermission)
                        // Default execution: /grid -> starts glass grid with 500 width, 3 spacing, origin pos + 2
                        .executes(context -> startGrid(context.getSource(), GridGenerator.Mode.GLASS, DEFAULT_WIDTH, DEFAULT_SPACING))
                        .then(Commands.literal("start")
                                .executes(context -> startGrid(context.getSource(), GridGenerator.Mode.GLASS, DEFAULT_WIDTH, DEFAULT_SPACING))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("glass");
                                            builder.suggest("lanterns");
                                            builder.suggest("rainbow");
                                            builder.suggest("rainbow_lanterns");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            GridGenerator.Mode mode = parseMode(StringArgumentType.getString(context, "mode"));
                                            return startGrid(context.getSource(), mode, DEFAULT_WIDTH, DEFAULT_SPACING);
                                        })
                                        .then(Commands.argument("width", IntegerArgumentType.integer(10, 5000))
                                                .executes(context -> {
                                                    GridGenerator.Mode mode = parseMode(StringArgumentType.getString(context, "mode"));
                                                    int width = IntegerArgumentType.getInteger(context, "width");
                                                    return startGrid(context.getSource(), mode, width, DEFAULT_SPACING);
                                                })
                                                .then(Commands.argument("spacing", IntegerArgumentType.integer(1, 64))
                                                        .executes(context -> {
                                                            GridGenerator.Mode mode = parseMode(StringArgumentType.getString(context, "mode"));
                                                            int width = IntegerArgumentType.getInteger(context, "width");
                                                            int spacing = IntegerArgumentType.getInteger(context, "spacing");
                                                            return startGrid(context.getSource(), mode, width, spacing);
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    GridGenerator.Task stopped = GridGenerator.stop();
                                    if (stopped != null) {
                                        context.getSource().sendSuccess(() -> Component.literal(String.format(
                                                java.util.Locale.US,
                                                "§c[GridGen] Grid generation stopped! Placed %,d of %,d blocks.",
                                                stopped.blocksPlaced, stopped.totalBlocks
                                        )), true);
                                    } else {
                                        context.getSource().sendFailure(Component.literal("§7No grid generation currently in progress."));
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    GridGenerator.Task task = GridGenerator.getActiveTask();
                                    if (task != null && !task.completed) {
                                        double percent = (task.blocksPlaced * 100.0) / Math.max(1L, task.totalBlocks);
                                        context.getSource().sendSuccess(() -> Component.literal(String.format(
                                                java.util.Locale.US,
                                                "§e[GridGen Status] Progress: §a%.2f%% §7(%,d / %,d blocks) | Mode: §f%s | Origin: §b%s",
                                                percent, task.blocksPlaced, task.totalBlocks, task.mode.name(), task.origin.toShortString()
                                        )), false);
                                    } else {
                                        context.getSource().sendSuccess(() -> Component.literal("§7No active grid generation task."), false);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("speed")
                                .then(Commands.argument("blocksPerTick", IntegerArgumentType.integer(100, 20000))
                                        .executes(context -> {
                                            int speed = IntegerArgumentType.getInteger(context, "blocksPerTick");
                                            GridGenerator.setSpeed(speed);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "§a[GridGen] Speed set to §e" + speed + " §ablocks/tick (" + (speed * 20) + " blocks/sec)."
                                            ), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }

    private static int startGrid(CommandSourceStack source, GridGenerator.Mode mode, int width, int spacing) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§cOnly players can start grid generation."));
            return 0;
        }

        // Origin = player position + 2 blocks in each axis
        BlockPos playerPos = player.blockPosition();
        BlockPos origin = playerPos.offset(2, 2, 2);

        boolean started = GridGenerator.start(source.getLevel(), origin, width, spacing, mode, player);
        if (!started) {
            source.sendFailure(Component.literal("§cGrid generation is already running! Run §e/grid stop §cto cancel it first."));
            return 0;
        }

        GridGenerator.Task task = GridGenerator.getActiveTask();
        source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.US,
                "§a[GridGen] Started %s grid from origin §e%s§a! Range: §e%d x %d§a, Y: §e%d -> %d§a, Spacing: §e%d blocks§a (~%,d blocks total).",
                mode.name(), origin.toShortString(), width, width, origin.getY(), task.maxY, spacing, task.totalBlocks
        )), true);

        return 1;
    }

    private static GridGenerator.Mode parseMode(String modeStr) {
        if (modeStr == null) return GridGenerator.Mode.GLASS;
        return switch (modeStr.toLowerCase(java.util.Locale.ROOT)) {
            case "lanterns", "lantern" -> GridGenerator.Mode.LANTERNS;
            case "rainbow" -> GridGenerator.Mode.RAINBOW;
            case "rainbow_lanterns", "rainbow-lanterns", "rainbowlanterns" -> GridGenerator.Mode.RAINBOW_LANTERNS;
            default -> GridGenerator.Mode.GLASS;
        };
    }

    private static boolean hasPermission(CommandSourceStack source) {
        if (source == null) return true;
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) return true;
        if (server.isSingleplayer()) return true;
        if (source.getPlayer() == null) return true;
        if (server.getPlayerList() == null) return true;
        return server.getPlayerList().isOp(source.getPlayer().nameAndId());
    }
}
