package com.gulab.jrunner;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DynamicJavaEngine {
    private static final AtomicLong SCRIPT_ID = new AtomicLong(0);
    private static final String SCRIPT_DIR_NAME = "jrunner_scripts";

    /**
     * Executes arbitrary Java snippet or full class text from in-game commands.
     */
    public static void executeCode(String rawCode, CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel world = source.getLevel();
        ServerPlayer player = source.getPlayer();
        BlockPos pos = player != null ? player.blockPosition() : world.getSharedSpawnPos();

        // 1. Prepare Java Context
        JavaContext ctx = new JavaContext(player, world, server, pos, source);

        // 2. Compile in background thread to avoid freezing the tick loop during compilation
        server.execute(() -> {
            try {
                long id = SCRIPT_ID.incrementAndGet();
                String className = "Snippet_" + id;
                String fullSource = wrapSourceCode(rawCode, className);

                CompilationResult result = compile(className, fullSource);

                if (!result.success && !rawCode.contains(";")) {
                    // Try compiling as statement if expression failed
                    String fallbackSource = wrapSourceCode(rawCode + "; return null;", className);
                    CompilationResult fallbackResult = compile(className, fallbackSource);
                    if (fallbackResult.success) {
                        result = fallbackResult;
                    }
                }

                if (!result.success) {
                    source.sendFailure(Component.literal("§c[Java Compiler Error]"));
                    for (Diagnostic<? extends JavaFileObject> diag : result.diagnostics) {
                        long line = Math.max(1, diag.getLineNumber() - 25); // Offset wrapper boilerplate
                        source.sendFailure(Component.literal("§c Line " + line + ": " + diag.getMessage(Locale.ENGLISH)));
                    }
                    return;
                }

                // 3. Load compiled class using current context ClassLoader
                ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();
                if (parentLoader == null) {
                    parentLoader = DynamicJavaEngine.class.getClassLoader();
                }

                Map<String, byte[]> byteMap = result.compiledBytes;
                ClassLoader scriptLoader = new ClassLoader(parentLoader) {
                    @Override
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                        byte[] bytes = byteMap.get(name);
                        if (bytes != null) {
                            return defineClass(name, bytes, 0, bytes.length);
                        }
                        return super.findClass(name);
                    }
                };

                Class<?> loadedClass = scriptLoader.loadClass("com.gulab.jrunner.dynamic." + className);
                Method runMethod = loadedClass.getMethod("run", ServerPlayer.class, ServerLevel.class, MinecraftServer.class, BlockPos.class, JavaContext.class);

                // 4. Execute on main game thread
                Object output = runMethod.invoke(null, player, world, server, pos, ctx);

                if (output != null) {
                    source.sendSuccess(() -> Component.literal("§a[Result] §e" + output), false);
                } else {
                    source.sendSuccess(() -> Component.literal("§a[Java] §fExecution finished successfully."), false);
                }

            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                source.sendFailure(Component.literal("§c[Runtime Error] " + cause.getClass().getSimpleName() + ": " + cause.getMessage()));
                Constants.LOG.error("Live Java execution exception", cause);
            }
        });
    }

    /**
     * Executes a script file from the jrunner_scripts folder.
     */
    public static void executeScriptFile(String filename, CommandSourceStack source) {
        Path scriptPath = getScriptsDir().resolve(filename.endsWith(".java") ? filename : filename + ".java");
        if (!Files.exists(scriptPath)) {
            source.sendFailure(Component.literal("§cScript file not found: " + scriptPath.getFileName()));
            source.sendFailure(Component.literal("§7Place .java scripts into: " + scriptPath.getParent().toAbsolutePath()));
            return;
        }

        try {
            String content = Files.readString(scriptPath);
            source.sendSuccess(() -> Component.literal("§b[Java] §fExecuting script §e" + scriptPath.getFileName() + "§f..."), false);
            executeCode(content, source);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to read file: " + e.getMessage()));
        }
    }

    /**
     * Returns all available script file names.
     */
    public static List<String> listScriptFiles() {
        Path dir = getScriptsDir();
        List<String> files = new ArrayList<>();
        if (Files.exists(dir)) {
            try {
                File[] list = dir.toFile().listFiles((d, name) -> name.endsWith(".java"));
                if (list != null) {
                    for (File f : list) {
                        files.add(f.getName());
                    }
                }
            } catch (Exception ignored) {}
        }
        return files;
    }

    public static Path getScriptsDir() {
        Path scriptsDir = Path.of("jrunner_scripts");
        if (!Files.exists(scriptsDir)) {
            try {
                Files.createDirectories(scriptsDir);
                generateDefaultScripts(scriptsDir);
            } catch (Exception ignored) {}
        }
        return scriptsDir;
    }

    private static void generateDefaultScripts(Path dir) {
        try {
            Path rainbow = dir.resolve("rainbow_tower.java");
            if (!Files.exists(rainbow)) {
                Files.writeString(rainbow,
                        "// Spawns a spiral tower of colored glass\n" +
                        "echo(\"Building rainbow spiral tower...\");\n" +
                        "net.minecraft.world.level.block.Block[] colors = new net.minecraft.world.level.block.Block[] {\n" +
                        "    Blocks.RED_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS,\n" +
                        "    Blocks.LIME_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS\n" +
                        "};\n" +
                        "for (int i = 0; i < 20; i++) {\n" +
                        "    double angle = i * 0.5;\n" +
                        "    int x = pos.getX() + (int) Math.round(Math.cos(angle) * 3);\n" +
                        "    int z = pos.getZ() + (int) Math.round(Math.sin(angle) * 3);\n" +
                        "    int y = pos.getY() + i;\n" +
                        "    setBlock(x, y, z, colors[i % colors.length]);\n" +
                        "}\n" +
                        "return \"Rainbow tower completed!\";\n"
                );
            }
        } catch (Exception ignored) {}
    }

    private static String wrapSourceCode(String snippet, String className) {
        String trimmed = snippet.trim();
        String body;

        if (trimmed.contains("class " + className)) {
            // User provided an entire standalone class
            return trimmed;
        }

        if (trimmed.startsWith("return ") || (!trimmed.endsWith(";") && !trimmed.contains("\n") && !trimmed.contains(";"))) {
            // Single expression
            body = "return (" + trimmed + ");";
        } else {
            // Statements block
            body = trimmed + (trimmed.endsWith(";") ? "" : ";") + "\nreturn null;";
        }

        return "package com.gulab.jrunner.dynamic;\n\n" +
                "import net.minecraft.server.MinecraftServer;\n" +
                "import net.minecraft.server.level.ServerLevel;\n" +
                "import net.minecraft.server.level.ServerPlayer;\n" +
                "import net.minecraft.commands.CommandSourceStack;\n" +
                "import net.minecraft.core.BlockPos;\n" +
                "import net.minecraft.core.Direction;\n" +
                "import net.minecraft.world.level.Level;\n" +
                "import net.minecraft.world.level.block.Blocks;\n" +
                "import net.minecraft.world.level.block.Block;\n" +
                "import net.minecraft.world.level.block.state.BlockState;\n" +
                "import net.minecraft.world.item.ItemStack;\n" +
                "import net.minecraft.world.item.Items;\n" +
                "import net.minecraft.world.item.Item;\n" +
                "import net.minecraft.world.entity.EntityType;\n" +
                "import net.minecraft.world.entity.LightningBolt;\n" +
                "import net.minecraft.world.effect.MobEffectInstance;\n" +
                "import net.minecraft.world.effect.MobEffects;\n" +
                "import net.minecraft.network.chat.Component;\n" +
                "import java.util.*;\n" +
                "import java.util.function.*;\n" +
                "import java.util.stream.*;\n" +
                "import com.gulab.jrunner.JavaContext;\n" +
                "import static com.gulab.jrunner.JavaContext.*;\n\n" +
                "public class " + className + " {\n" +
                "    public static Object run(ServerPlayer player, ServerLevel world, MinecraftServer server, BlockPos pos, JavaContext ctx) throws Throwable {\n" +
                "        " + body + "\n" +
                "    }\n" +
                "}\n";
    }

    private static CompilationResult compile(String className, String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            DiagnosticCollector<JavaFileObject> dummy = new DiagnosticCollector<>();
            return new CompilationResult(false, dummy.getDiagnostics(), Collections.emptyMap());
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        String fullClassName = "com.gulab.jrunner.dynamic." + className;

        SimpleJavaFileObject sourceFile = new SimpleJavaFileObject(
                URI.create("string:///" + className + ".java"),
                JavaFileObject.Kind.SOURCE
        ) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };

        Map<String, ByteArrayOutputStream> byteStreams = new HashMap<>();
        JavaFileManager fileManager = new ForwardingJavaFileManager<StandardJavaFileManager>(
                compiler.getStandardFileManager(diagnostics, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("byte:///" + name), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byteStreams.put(name, baos);
                        return baos;
                    }
                };
            }
        };

        List<String> options = new ArrayList<>();
        options.add("-proc:none");
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.isEmpty()) {
            options.add("-classpath");
            options.add(classPath);
        }

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, List.of(sourceFile));
        boolean success = Boolean.TRUE.equals(task.call());

        Map<String, byte[]> byteMap = new HashMap<>();
        if (success) {
            for (Map.Entry<String, ByteArrayOutputStream> entry : byteStreams.entrySet()) {
                byteMap.put(entry.getKey(), entry.getValue().toByteArray());
            }
        }

        return new CompilationResult(success, diagnostics.getDiagnostics(), byteMap);
    }

    private record CompilationResult(
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            Map<String, byte[]> compiledBytes
    ) {}
}
