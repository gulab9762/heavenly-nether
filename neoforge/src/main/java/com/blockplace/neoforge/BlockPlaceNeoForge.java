package com.blockplace.neoforge;

import com.blockplace.BlockPlaceCommon;
import com.blockplace.Constants;
import com.blockplace.client.animation.BlockPlacementManager;
import com.blockplace.client.animation.tree.TreeShakeManager;
import com.blockplace.client.config.BlockPlaceConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@Mod(Constants.MOD_ID)
public class BlockPlaceNeoForge {

    public BlockPlaceNeoForge() {
        BlockPlaceCommon.init();
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && !client.isPaused()) {
                BlockPlacementManager.tick(client.level);
                TreeShakeManager.tick(client.level);
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            BlockPlacementManager.clear();
            TreeShakeManager.clear();
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            BlockPlaceConfig.load();

            event.getDispatcher().register(
                    Commands.literal("blockplace")
                            .then(Commands.literal("toggle")
                                    .executes(context -> toggleAnimation(context.getSource()))
                            )
                            .then(Commands.literal("tree")
                                    .then(Commands.argument("value", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                builder.suggest("on");
                                                builder.suggest("off");
                                                builder.suggest("toggle");
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String val = StringArgumentType.getString(context, "value");
                                                return applyTreeCommand(context.getSource(), val);
                                            })
                                    )
                                    .executes(context -> toggleTreeShake(context.getSource()))
                            )
                            .then(Commands.literal("style")
                                    .then(Commands.argument("value", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                for (BlockPlaceConfig.AnimationStyle s : BlockPlaceConfig.AnimationStyle.values()) {
                                                    builder.suggest(s.name().toLowerCase());
                                                }
                                                builder.suggest("vanilla");
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String val = StringArgumentType.getString(context, "value");
                                                return applyStyleCommand(context.getSource(), val);
                                            })
                                    )
                                    .executes(context -> showCurrentStyle(context.getSource()))
                            )
                            .then(Commands.argument("value", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        for (BlockPlaceConfig.AnimationStyle s : BlockPlaceConfig.AnimationStyle.values()) {
                                            builder.suggest(s.name().toLowerCase());
                                        }
                                        builder.suggest("vanilla");
                                        builder.suggest("toggle");
                                        builder.suggest("tree");
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> {
                                        String val = StringArgumentType.getString(context, "value");
                                        if (val.equalsIgnoreCase("toggle")) {
                                            return toggleAnimation(context.getSource());
                                        }
                                        if (val.equalsIgnoreCase("tree")) {
                                            return toggleTreeShake(context.getSource());
                                        }
                                        return applyStyleCommand(context.getSource(), val);
                                    })
                            )
                            .executes(context -> showCurrentStyle(context.getSource()))
            );
        }

        private static int toggleAnimation(CommandSourceStack source) {
            if (BlockPlaceConfig.style == BlockPlaceConfig.AnimationStyle.NONE) {
                BlockPlaceConfig.style = BlockPlaceConfig.AnimationStyle.ROTATING;
                BlockPlaceConfig.save();
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fAnimations §aENABLED §f(style: §erotating§f)"), false);
            } else {
                BlockPlaceConfig.style = BlockPlaceConfig.AnimationStyle.NONE;
                BlockPlaceConfig.save();
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fAnimations §cDISABLED §7(pure vanilla placement)"), false);
            }
            return 1;
        }

        private static int toggleTreeShake(CommandSourceStack source) {
            BlockPlaceConfig.enableTreeShake = !BlockPlaceConfig.enableTreeShake;
            BlockPlaceConfig.save();
            if (BlockPlaceConfig.enableTreeShake) {
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fCartoon Tree Shake animations §aENABLED"), false);
            } else {
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fCartoon Tree Shake animations §cDISABLED"), false);
            }
            return 1;
        }

        private static int applyTreeCommand(CommandSourceStack source, String val) {
            if (val.equalsIgnoreCase("on") || val.equalsIgnoreCase("true") || val.equalsIgnoreCase("enable")) {
                BlockPlaceConfig.enableTreeShake = true;
                BlockPlaceConfig.save();
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fCartoon Tree Shake animations §aENABLED"), false);
                return 1;
            } else if (val.equalsIgnoreCase("off") || val.equalsIgnoreCase("false") || val.equalsIgnoreCase("disable")) {
                BlockPlaceConfig.enableTreeShake = false;
                BlockPlaceConfig.save();
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fCartoon Tree Shake animations §cDISABLED"), false);
                return 1;
            } else if (val.equalsIgnoreCase("toggle")) {
                return toggleTreeShake(source);
            } else {
                source.sendFailure(Component.literal("§c[Block Place] §fUnknown option: '§c" + val + "§f'. Use: §eon, off, toggle"));
                return 0;
            }
        }

        private static int applyStyleCommand(CommandSourceStack source, String val) {
            if (BlockPlaceConfig.setStyle(val)) {
                String name = (BlockPlaceConfig.style == BlockPlaceConfig.AnimationStyle.NONE) ? "none (vanilla placement)" : BlockPlaceConfig.style.name().toLowerCase();
                source.sendSuccess(() -> Component.literal("§a[Block Place] §fAnimation style set to: §e" + name), false);
                return 1;
            } else {
                source.sendFailure(Component.literal("§c[Block Place] §fUnknown style: '§c" + val + "§f'. Available: §esmooth, rotating, pop, drop, spiral, expand, spring, none"));
                return 0;
            }
        }

        private static int showCurrentStyle(CommandSourceStack source) {
            String name = (BlockPlaceConfig.style == BlockPlaceConfig.AnimationStyle.NONE) ? "none (vanilla)" : BlockPlaceConfig.style.name().toLowerCase();
            String treeStatus = BlockPlaceConfig.enableTreeShake ? "§aenabled" : "§cdisabled";
            source.sendSuccess(() -> Component.literal("§a[Block Place] §fPlacement style: §e" + name + " §7| Tree shake: " + treeStatus), false);
            source.sendSuccess(() -> Component.literal("§7Use §f/blockplace style <name>§7, §f/blockplace toggle§7, or §f/blockplace tree"), false);
            return 1;
        }
    }
}
