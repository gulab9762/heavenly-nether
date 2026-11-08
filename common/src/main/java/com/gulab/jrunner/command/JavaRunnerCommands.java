package com.gulab.jrunner.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.gulab.jrunner.DynamicJavaEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

public class JavaRunnerCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 1. /jexec <code...>
        dispatcher.register(
                Commands.literal("jexec")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String code = StringArgumentType.getString(context, "code");
                                    DynamicJavaEngine.executeCode(code, context.getSource());
                                    return 1;
                                })
                        )
        );

        // 2. /jfile <filename>
        dispatcher.register(
                Commands.literal("jfile")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (String f : DynamicJavaEngine.listScriptFiles()) {
                                        if (f.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                            builder.suggest(f);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String file = StringArgumentType.getString(context, "file");
                                    DynamicJavaEngine.executeScriptFile(file, context.getSource());
                                    return 1;
                                })
                        )
        );

        // 3. /jlist
        dispatcher.register(
                Commands.literal("jlist")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            List<String> files = DynamicJavaEngine.listScriptFiles();
                            if (files.isEmpty()) {
                                context.getSource().sendSuccess(() -> Component.literal("§7No scripts found in jrunner_scripts/ folder."), false);
                            } else {
                                context.getSource().sendSuccess(() -> Component.literal("§6[Available Scripts (" + files.size() + ")]"), false);
                                for (String f : files) {
                                    context.getSource().sendSuccess(() -> Component.literal("  §e- " + f), false);
                                }
                            }
                            return 1;
                        })
        );

        // 4. /jhelp
        dispatcher.register(
                Commands.literal("jhelp")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("§6=== §eJava Runner Live Coding §6==="), false);
                            context.getSource().sendSuccess(() -> Component.literal("§e/jexec <code...> §7- Execute live Java snippet"), false);
                            context.getSource().sendSuccess(() -> Component.literal("§e/jfile <file>    §7- Run .java file from jrunner_scripts/"), false);
                            context.getSource().sendSuccess(() -> Component.literal("§e/jlist          §7- List all available script files"), false);
                            context.getSource().sendSuccess(() -> Component.literal("§7Injected Vars: §fplayer, world, server, pos, ctx"), false);
                            context.getSource().sendSuccess(() -> Component.literal("§7Shortcuts:     §fecho(), give(), setBlock(), strikeLightning(), teleport()"), false);
                            return 1;
                        })
        );
    }
}

