$ErrorActionPreference = "Stop"

# 1. Update gradle.properties
$gradleProps = @'
# Project
version=1.0.0
group=com.heavenlynether
java_version=25

# Common
minecraft_version=26.3
mod_name=Heavenly Nether
mod_author=Antigravity
mod_id=heavenlynether
license=MIT
credits=
description=Transforms the subterranean world into a celestial underground paradise with crystal oceans, marble caves, and cherry blossom gardens.
minecraft_version_range=[26.3, 26.4)
neo_form_version=26.3-1

# Fabric
fabric_version=0.160.6+26.3
fabric_loader_version=0.19.5

# NeoForge
neoforge_version=26.3.0.1-beta
neoforge_loader_version_range=[4,)

# Gradle
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
org.gradle.java.home=C:/Program Files/Java/jdk-25
'@
$gradleProps | Set-Content -Path 'gradle.properties' -Encoding utf8
Write-Output "[OK] Updated gradle.properties"

# 2. Update settings.gradle
(Get-Content 'settings.gradle' -Raw) -replace "rootProject\.name = 'block-place'", "rootProject.name = 'heavenly-nether'" | Set-Content -Path 'settings.gradle' -Encoding utf8
Write-Output "[OK] Updated settings.gradle"

# 3. Refactor data/ directory from blockplace to heavenlynether
$oldData = "common/src/main/resources/data/blockplace"
$newData = "common/src/main/resources/data/heavenlynether"
if (Test-Path $newData) { Remove-Item -Path $newData -Recurse -Force }
Move-Item -Path $oldData -Destination $newData
Write-Output "[OK] Moved data/blockplace to data/heavenlynether"

# Replace all namespace occurrences in the json files
$jsonFiles = Get-ChildItem -Path $newData -Recurse -Filter "*.json"
foreach ($f in $jsonFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace "blockplace:", "heavenlynether:"
    $content | Set-Content -Path $f.FullName -Encoding utf8
}
Write-Output "[OK] Replaced namespace references to heavenlynether:"

# 4. Remove blockplace.mixins.json and create empty heavenlynether.mixins.json
Remove-Item -Path "common/src/main/resources/blockplace.mixins.json" -Force -ErrorAction SilentlyContinue
@'
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.heavenlynether.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
'@ | Set-Content -Path "common/src/main/resources/heavenlynether.mixins.json" -Encoding utf8
Write-Output "[OK] Created heavenlynether.mixins.json"

# 5. Clean up old Java code
Remove-Item -Path "common/src/main/java/com/blockplace" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "fabric/src/main/java/com/blockplace" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "neoforge/src/main/java/com/blockplace" -Recurse -Force -ErrorAction SilentlyContinue
Write-Output "[OK] Removed old com.blockplace Java source trees"

# 6. Create new Java source trees
# Common
$commonDir = "common/src/main/java/com/heavenlynether"
New-Item -ItemType Directory -Force -Path $commonDir | Out-Null
@'
package com.heavenlynether;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "heavenlynether";
    public static final String MOD_NAME = "Heavenly Nether";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
}
'@ | Set-Content -Path "$commonDir/Constants.java" -Encoding utf8

@'
package com.heavenlynether;

public class HeavenlyNetherCommon {
    public static void init() {
        Constants.LOG.info("Initializing {}", Constants.MOD_NAME);
    }
}
'@ | Set-Content -Path "$commonDir/HeavenlyNetherCommon.java" -Encoding utf8
Write-Output "[OK] Created Common classes"

# Fabric
$fabricDir = "fabric/src/main/java/com/heavenlynether/fabric"
New-Item -ItemType Directory -Force -Path $fabricDir | Out-Null
@'
package com.heavenlynether.fabric;

import com.heavenlynether.HeavenlyNetherCommon;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Set;

public class HeavenlyNetherFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HeavenlyNetherCommon.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("heaven")
                            .executes(context -> {
                                try {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ResourceKey<Level> dimKey = ResourceKey.create(
                                            Registries.DIMENSION,
                                            Identifier.fromNamespaceAndPath("heavenlynether", "heavenly_nether")
                                    );
                                    ServerLevel targetLevel = context.getSource().getServer().getLevel(dimKey);
                                    if (targetLevel != null) {
                                        player.teleportTo(targetLevel, 0.5, 70.0, 0.5, Set.of(), 0.0f, 0.0f, true);
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("§a[Heavenly Nether] §fWelcome to the Heavenly Underground!"),
                                                false
                                        );
                                        return 1;
                                    } else {
                                        context.getSource().sendFailure(
                                                Component.literal("§c[Heavenly Nether] §fDimension 'heavenlynether:heavenly_nether' not found.")
                                        );
                                        return 0;
                                    }
                                } catch (Exception e) {
                                    context.getSource().sendFailure(Component.literal("§cOnly players can run this command."));
                                    return 0;
                                }
                            })
            );
        });
    }
}
'@ | Set-Content -Path "$fabricDir/HeavenlyNetherFabric.java" -Encoding utf8
Write-Output "[OK] Created Fabric class"

# NeoForge
$neoDir = "neoforge/src/main/java/com/heavenlynether/neoforge"
New-Item -ItemType Directory -Force -Path $neoDir | Out-Null
@'
package com.heavenlynether.neoforge;

import com.heavenlynether.Constants;
import com.heavenlynether.HeavenlyNetherCommon;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;

@Mod(Constants.MOD_ID)
public class HeavenlyNetherNeoForge {
    public HeavenlyNetherNeoForge() {
        HeavenlyNetherCommon.init();
    }

    @EventBusSubscriber(modid = Constants.MOD_ID)
    public static class ServerEvents {
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("heaven")
                            .executes(context -> {
                                try {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ResourceKey<Level> dimKey = ResourceKey.create(
                                            Registries.DIMENSION,
                                            Identifier.fromNamespaceAndPath("heavenlynether", "heavenly_nether")
                                    );
                                    ServerLevel targetLevel = context.getSource().getServer().getLevel(dimKey);
                                    if (targetLevel != null) {
                                        player.teleportTo(targetLevel, 0.5, 70.0, 0.5, Set.of(), 0.0f, 0.0f, true);
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("§a[Heavenly Nether] §fWelcome to the Heavenly Underground!"),
                                                false
                                        );
                                        return 1;
                                    } else {
                                        context.getSource().sendFailure(
                                                Component.literal("§c[Heavenly Nether] §fDimension 'heavenlynether:heavenly_nether' not found.")
                                        );
                                        return 0;
                                    }
                                } catch (Exception e) {
                                    context.getSource().sendFailure(Component.literal("§cOnly players can run this command."));
                                    return 0;
                                }
                            })
            );
        }
    }
}
'@ | Set-Content -Path "$neoDir/HeavenlyNetherNeoForge.java" -Encoding utf8
Write-Output "[OK] Created NeoForge class"

# 7. Update fabric.mod.json
$fabricModJson = @'
{
    "schemaVersion": 1,
    "id": "${mod_id}",
    "version": "${version}",
    "name": "${mod_name}",
    "description": "${description}",
    "authors": [
        "${mod_author}"
    ],
    "contact": {},
    "license": "${license}",
    "icon": "icon.png",
    "environment": "*",
    "entrypoints": {
        "main": [
            "com.heavenlynether.fabric.HeavenlyNetherFabric"
        ]
    },
    "mixins": [
        "${mod_id}.mixins.json"
    ],
    "depends": {
        "fabricloader": ">=${fabric_loader_version}",
        "fabric-api": "*",
        "minecraft": "~${minecraft_version}",
        "java": ">=${java_version}"
    }
}
'@
$fabricModJson | Set-Content -Path 'fabric/src/main/resources/fabric.mod.json' -Encoding utf8
Write-Output "[OK] Updated fabric.mod.json"

# 8. Update deploy.ps1
$deployScript = @'
param (
    [string]$Loader = "fabric",
    [string]$MultiMcModsDir = "D:\MultiMC\instances\26.3\.minecraft\mods",
    [string]$DownloadsDir = "$env:USERPROFILE\Downloads"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deploying Heavenly Nether Mod [$Loader]" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$searchPath = if ($Loader -eq "neoforge") { "neoforge\build\libs" } else { "fabric\build\libs" }

if (-not (Test-Path $searchPath)) {
    Write-Error "Build output directory not found: $searchPath. Please build the mod first."
    exit 1
}

$jar = Get-ChildItem -Path $searchPath -Filter "heavenlynether-*.jar" | 
    Where-Object { $_.Name -notmatch "sources|javadoc" } | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1

if (-not $jar) {
    Write-Error "No compiled mod JAR found in $searchPath. Run '.\gradlew.bat buildFabric' first."
    exit 1
}

$jarName = $jar.Name
Write-Host "[OK] Found Mod JAR: $jarName" -ForegroundColor Green

# 1. Clean previous old mod jars from target directories
if (Test-Path $DownloadsDir) {
    Get-ChildItem -Path $DownloadsDir -Filter "blockplace-*.jar" | Remove-Item -Force -ErrorAction SilentlyContinue
    Copy-Item -Path $jar.FullName -Destination $DownloadsDir -Force
    Write-Host "[OK] Copied to Downloads -> $DownloadsDir\$jarName" -ForegroundColor Green
}

if (-not (Test-Path $MultiMcModsDir)) {
    New-Item -ItemType Directory -Path $MultiMcModsDir -Force | Out-Null
}
Get-ChildItem -Path $MultiMcModsDir -Filter "blockplace-*.jar" | Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item -Path $jar.FullName -Destination $MultiMcModsDir -Force
Write-Host "[OK] Copied to MultiMC  -> $MultiMcModsDir\$jarName" -ForegroundColor Green

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
'@
$deployScript | Set-Content -Path 'scripts/deploy.ps1' -Encoding utf8
Write-Output "[OK] Updated scripts/deploy.ps1"

Write-Output "`nProject successfully refactored to Heavenly Nether!"
