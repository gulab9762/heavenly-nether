<#
.SYNOPSIS
    Cleans up the multiloader project into a clean template on a specified branch and pushes to remote.

.DESCRIPTION
    This script:
    1. Validates that the git working directory is clean.
    2. Creates/checkouts the specified target branch (e.g., 'template/multiloader' or 'feature/my-new-mod').
    3. Cleans out old mod-specific Java sources, mixin configs, assets, data, and audio artifacts.
    4. Scaffolds clean, minimal entry points for Common, Fabric, NeoForge, and Forge.
    5. Updates gradle.properties, settings.gradle, fabric.mod.json, mods.toml, and deploy.ps1.
    6. Creates a clean git commit and optionally pushes to the remote repository.

.PARAMETER BranchName
    The target branch name (e.g. 'template/multiloader', 'feature/new-mod'). Default: 'template/multiloader'.

.PARAMETER ModId
    The Minecraft mod ID (lowercase letters, numbers, underscores). Default: 'modtemplate'.

.PARAMETER ModName
    Human-readable display name of the mod. Default: 'Mod Template'.

.PARAMETER Group
    The Java package group. Default: 'com.example.modtemplate'.

.PARAMETER Description
    Short description of the mod/template. Default: 'Clean multiloader mod template for Minecraft 26.3'.

.PARAMETER Author
    Mod author. Default: 'Antigravity'.

.PARAMETER Remote
    Git remote name. Defaults to the first detected git remote (e.g. 'heavenly-nether' or 'origin').

.PARAMETER BaseBranch
    The base branch to branch off from if creating a new branch. Default: 'master'.

.PARAMETER Push
    Switch to automatically push the new branch to the remote repository.

.PARAMETER Force
    Force checkout if the branch already exists.

.EXAMPLE
    .\scripts\create_template_branch.ps1 -BranchName "template/multiloader" -Push
    .\scripts\create_template_branch.ps1 -BranchName "feature/magic-mod" -ModId "magicmod" -ModName "Magic Mod" -Group "com.gulab.magicmod" -Push
#>

param (
    [Parameter(Position = 0)]
    [string]$BranchName = "template/multiloader",

    [string]$ModId = "modtemplate",
    [string]$ModName = "Mod Template",
    [string]$Group = "com.example.modtemplate",
    [string]$Description = "Clean multiloader mod template for Minecraft 26.3 supporting Fabric, NeoForge, and Forge.",
    [string]$Author = "Antigravity",
    [string]$Remote = "",
    [string]$BaseBranch = "master",
    [switch]$Push,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

# Ensure script runs from repository root
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Multiloader Mod Template Scaffolder & Branch Creator   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Branch : $BranchName" -ForegroundColor Yellow
Write-Host "Mod ID        : $ModId" -ForegroundColor Yellow
Write-Host "Mod Name      : $ModName" -ForegroundColor Yellow
Write-Host "Java Group    : $Group" -ForegroundColor Yellow
Write-Host "Base Branch   : $BaseBranch" -ForegroundColor Yellow
Write-Host "Push to Remote: $(if ($Push) { 'Yes' } else { 'No (use -Push to enable)' })" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verify Git working directory is clean
$currentBranch = (git branch --show-current).Trim()
$gitStatus = git status --porcelain
if ($gitStatus -and -not $Force -and ($currentBranch -ne $BranchName)) {
    Write-Error "Working directory has uncommitted changes on branch '$currentBranch'. Please commit or stash your work, or specify -Force."
    exit 1
}

# 2. Auto-detect Git Remote if not supplied
if ([string]::IsNullOrWhiteSpace($Remote)) {
    $detectedRemotes = @(git remote)
    if ($detectedRemotes.Count -gt 0 -and $detectedRemotes[0]) {
        $Remote = ("" + $detectedRemotes[0]).Trim()
    } else {
        $Remote = "origin"
    }
}
Write-Host "[OK] Using Git Remote: $Remote" -ForegroundColor Green

# 3. Create or checkout target branch
if ($currentBranch -eq $BranchName) {
    Write-Host "[*] Already on target branch '$BranchName'." -ForegroundColor Green
} else {
    $branchCheck = @(git branch --list $BranchName)
    $existingLocalBranch = if ($branchCheck) { ("" + ($branchCheck -join "")).Trim() } else { "" }

    if ($existingLocalBranch) {
        Write-Host "[*] Branch '$BranchName' already exists. Switching to it..." -ForegroundColor Yellow
        git checkout $BranchName
    } else {
        $baseCheck = @(git branch --list $BaseBranch)
        $existingBase = if ($baseCheck) { ("" + ($baseCheck -join "")).Trim() } else { "" }
        if ($existingBase) {
            Write-Host "[*] Creating and checking out branch '$BranchName' from '$BaseBranch'..." -ForegroundColor Green
            git checkout -b $BranchName $BaseBranch
        } else {
            Write-Host "[*] Creating and checking out branch '$BranchName' from current HEAD..." -ForegroundColor Green
            git checkout -b $BranchName
        }
    }
}

# 4. Clean up previous mod-specific Java source trees
Write-Host "[*] Purging previous mod Java source trees..." -ForegroundColor Cyan
$sourceDirs = @(
    "common/src/main/java",
    "fabric/src/main/java",
    "neoforge/src/main/java",
    "forge/src/main/java"
)
foreach ($dir in $sourceDirs) {
    if (Test-Path $dir) {
        Get-ChildItem -Path $dir -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# 5. Clean up previous mod resources & data
Write-Host "[*] Purging previous assets, data, and mixin configurations..." -ForegroundColor Cyan
Get-ChildItem -Path "common/src/main/resources" -Filter "*.mixins.json" -File | Remove-Item -Force -ErrorAction SilentlyContinue
if (Test-Path "common/src/main/resources/assets") {
    Remove-Item -Path "common/src/main/resources/assets" -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path "common/src/main/resources/data") {
    Remove-Item -Path "common/src/main/resources/data" -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path "resourcepacks") {
    Remove-Item -Path "resourcepacks" -Recurse -Force -ErrorAction SilentlyContinue
}

# Purge leftover soundtrack, audio zips, and one-off helper files
$filesToClean = @(
    "Unharvest-Original-Soundtrack.zip",
    "extract_soundtrack.bat",
    "extract_soundtrack.py",
    "scripts/extract_soundtrack.bat",
    "scripts/extract_soundtrack.py",
    "scripts/vanilla_nether",
    "neoforge/.eclipse",
    "neoforge/run",
    "fabric/run",
    "forge/run",
    "common/run",
    "run",
    "runs"
)
foreach ($f in $filesToClean) {
    if (Test-Path $f) {
        Remove-Item -Path $f -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# 6. Scaffold directory structures for the new package
$groupRelPath = ($Group -replace '\.', '/')
$pascalCaseName = ($ModName -replace '[^a-zA-Z0-9]', '')
if ([string]::IsNullOrWhiteSpace($pascalCaseName)) {
    $pascalCaseName = "TemplateMod"
}

$commonPkgDir   = "common/src/main/java/$groupRelPath"
$fabricPkgDir   = "fabric/src/main/java/$groupRelPath/fabric"
$fabricClientDir= "fabric/src/main/java/$groupRelPath/fabric/client"
$neoPkgDir      = "neoforge/src/main/java/$groupRelPath/neoforge"
$forgePkgDir    = "forge/src/main/java/$groupRelPath/forge"

New-Item -ItemType Directory -Force -Path $commonPkgDir | Out-Null
New-Item -ItemType Directory -Force -Path $fabricPkgDir | Out-Null
New-Item -ItemType Directory -Force -Path $fabricClientDir | Out-Null
New-Item -ItemType Directory -Force -Path $neoPkgDir | Out-Null
New-Item -ItemType Directory -Force -Path $forgePkgDir | Out-Null

# Helper function to safely replace placeholders without format string brace collision
function Replace-Placeholders([string]$text) {
    $text = $text.Replace('__GROUP__', $Group)
    $text = $text.Replace('__MOD_ID__', $ModId)
    $text = $text.Replace('__MOD_NAME__', $ModName)
    $text = $text.Replace('__NAME__', $pascalCaseName)
    $text = $text.Replace('__AUTHOR__', $Author)
    $text = $text.Replace('__DESCRIPTION__', $Description)
    return $text
}

# 7. Generate clean Java source code
Write-Host "[*] Scaffolding template Java classes..." -ForegroundColor Cyan

# Constants.java
@'
package __GROUP__;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "__MOD_ID__";
    public static final String MOD_NAME = "__MOD_NAME__";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "$commonPkgDir/Constants.java" -Encoding utf8

# Common class
@'
package __GROUP__;

public class __NAME__Common {
    public static void init() {
        Constants.LOG.info("Initializing {}", Constants.MOD_NAME);
    }
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "$commonPkgDir/${pascalCaseName}Common.java" -Encoding utf8

# Fabric entry point
@'
package __GROUP__.fabric;

import __GROUP__.__NAME__Common;
import net.fabricmc.api.ModInitializer;

public class __NAME__Fabric implements ModInitializer {
    @Override
    public void onInitialize() {
        __NAME__Common.init();
    }
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "$fabricPkgDir/${pascalCaseName}Fabric.java" -Encoding utf8

# Fabric client entry point
@'
package __GROUP__.fabric.client;

import net.fabricmc.api.ClientModInitializer;

public class __NAME__FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register client events, screens, and renderers here
    }
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "$fabricClientDir/${pascalCaseName}FabricClient.java" -Encoding utf8

# NeoForge entry point
@'
package __GROUP__.neoforge;

import __GROUP__.Constants;
import __GROUP__.__NAME__Common;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class __NAME__NeoForge {
    public __NAME__NeoForge(IEventBus modBus) {
        __NAME__Common.init();
    }
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "$neoPkgDir/${pascalCaseName}NeoForge.java" -Encoding utf8

# Forge entry point
@'
package __GROUP__.forge;

import __GROUP__.Constants;
import __GROUP__.__NAME__Common;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class __NAME__Forge {
    public __NAME__Forge() {
        __NAME__Common.init();
    }
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "$forgePkgDir/${pascalCaseName}Forge.java" -Encoding utf8

# 8. Generate clean resources & metadata
Write-Host "[*] Updating metadata, configuration, and resources..." -ForegroundColor Cyan

# Ensure resource directories exist
New-Item -ItemType Directory -Force -Path "common/src/main/resources" | Out-Null
New-Item -ItemType Directory -Force -Path "fabric/src/main/resources" | Out-Null
New-Item -ItemType Directory -Force -Path "forge/src/main/resources/META-INF" | Out-Null
New-Item -ItemType Directory -Force -Path "neoforge/src/main/resources/META-INF" | Out-Null

# Clean .gitignore
@'
.gradle/
build/
*/build/
out/
.idea/
*.iml
*.ipr
*.iws
run/
*/run/
runs/
*/runs/
bin/
.DS_Store
*.log
gradle-local.properties
resourcepacks/*.zip
*.zip
.eclipse/
*/.eclipse/
*.eclipse/
'@ | Set-Content -Path '.gitignore' -Encoding utf8

# mixins.json
@'
{
  "required": true,
  "minVersion": "0.8",
  "package": "__GROUP__.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "common/src/main/resources/${ModId}.mixins.json" -Encoding utf8

# pack.mcmeta
@'
{
  "pack": {
    "pack_format": 81,
    "supported_formats": [1, 100],
    "description": "${description}"
  }
}
'@ | Set-Content -Path "common/src/main/resources/pack.mcmeta" -Encoding utf8

# Starter lang file
$langDir = "common/src/main/resources/assets/$ModId/lang"
New-Item -ItemType Directory -Force -Path $langDir | Out-Null
@'
{}
'@ | Set-Content -Path "$langDir/en_us.json" -Encoding utf8

# fabric.mod.json
@'
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
            "__GROUP__.fabric.__NAME__Fabric"
        ],
        "client": [
            "__GROUP__.fabric.client.__NAME__FabricClient"
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
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "fabric/src/main/resources/fabric.mod.json" -Encoding utf8

# mods.toml (Forge)
@'
modLoader="javafml"
loaderVersion="${forge_loader_version_range}"
license="${license}"

[[mods]]
modId="${mod_id}"
version="${version}"
displayName="${mod_name}"
authors="${mod_author}"
description="${description}"
logoFile="icon.png"

[[mixins]]
config="${mod_id}.mixins.json"

[[dependencies.${mod_id}]]
modId="forge"
mandatory=true
versionRange="[${forge_version},)"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="minecraft"
mandatory=true
versionRange="${minecraft_version_range}"
ordering="NONE"
side="BOTH"
'@ | Set-Content -Path "forge/src/main/resources/META-INF/mods.toml" -Encoding utf8

# neoforge.mods.toml (NeoForge)
@'
modLoader="javafml"
loaderVersion="${neoforge_loader_version_range}"
license="${license}"

[[mods]]
modId="${mod_id}"
version="${version}"
displayName="${mod_name}"
authors="${mod_author}"
description="${description}"
logoFile="icon.png"

[[mixins]]
config="${mod_id}.mixins.json"

[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="[${neoforge_version},)"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="BOTH"
'@ | Set-Content -Path "neoforge/src/main/resources/META-INF/neoforge.mods.toml" -Encoding utf8

# 9. Update gradle.properties and settings.gradle
@'
# Project
version=1.0.0
group=__GROUP__
java_version=25

# Common
minecraft_version=26.3
mod_name=__MOD_NAME__
mod_author=__AUTHOR__
mod_id=__MOD_ID__
license=MIT
credits=__AUTHOR__
description=__DESCRIPTION__
minecraft_version_range=[26.3, 26.4)
neo_form_version=26.3-1

# Fabric
fabric_version=0.160.6+26.3
fabric_loader_version=0.19.5

# NeoForge
neoforge_version=26.3.0.1-beta
neoforge_loader_version_range=[4,)

# Forge
forge_version=66.0.10
forge_loader_version_range=[66,)

# Gradle
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
org.gradle.java.home=C:/Program Files/Java/jdk-25
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path "gradle.properties" -Encoding utf8

# Update rootProject.name in settings.gradle
$settingsContent = Get-Content 'settings.gradle' -Raw
$settingsContent = $settingsContent -replace "rootProject\.name\s*=\s*['`"][^'`"]+['`"]", "rootProject.name = '$ModId'"
$settingsContent | Set-Content -Path 'settings.gradle' -Encoding utf8

# 10. Update scripts/deploy.ps1 with new ModId and ModName
$deployScriptContent = @'
param (
    [string]$Loader = "universal",
    [string]$MultiMcModsDir = "D:\MultiMC\instances\26.3\.minecraft\mods",
    [string]$DownloadsDir = "$env:USERPROFILE\Downloads"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deploying __MOD_NAME__ Mod [$Loader]" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$searchPath = switch ($Loader.ToLower()) {
    "neoforge" { "neoforge\build\libs" }
    "fabric"   { "fabric\build\libs" }
    "forge"    { "forge\build\libs" }
    default    { "build\libs" }
}

if (-not (Test-Path $searchPath)) {
    Write-Error "Build output directory not found: $searchPath. Please run '.\gradlew.bat universalJar' first."
    exit 1
}

$jar = Get-ChildItem -Path $searchPath -Filter "*__MOD_ID__*.jar" | 
    Where-Object { $_.Name -notmatch "sources|javadoc" } | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1

if (-not $jar) {
    # Fallback to any jar in directory
    $jar = Get-ChildItem -Path $searchPath -Filter "*.jar" | 
        Where-Object { $_.Name -notmatch "sources|javadoc" } | 
        Sort-Object LastWriteTime -Descending | 
        Select-Object -First 1
}

if (-not $jar) {
    Write-Error "No compiled mod JAR found in $searchPath. Run '.\gradlew.bat universalJar' first."
    exit 1
}

$jarName = $jar.Name
Write-Host "[OK] Found Mod JAR: $jarName" -ForegroundColor Green

# 1. Deploy to Downloads
if (Test-Path $DownloadsDir) {
    Copy-Item -Path $jar.FullName -Destination $DownloadsDir -Force
    Write-Host "[OK] Copied to Downloads -> $DownloadsDir\$jarName" -ForegroundColor Green
} else {
    Write-Warning "Downloads directory not found: $DownloadsDir"
}

# 2. Deploy to MultiMC instance on D: drive
if (-not (Test-Path $MultiMcModsDir)) {
    New-Item -ItemType Directory -Path $MultiMcModsDir -Force | Out-Null
}

# Remove older versions of this mod in mods directory to prevent duplicate collisions
Get-ChildItem -Path $MultiMcModsDir -Include "*__MOD_ID__*.jar" -File | ForEach-Object {
    Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue
    Write-Host "[-] Removed previous build from MultiMC: $($_.Name)" -ForegroundColor Yellow
}

Copy-Item -Path $jar.FullName -Destination $MultiMcModsDir -Force
Write-Host "[OK] Copied to MultiMC  -> $MultiMcModsDir\$jarName" -ForegroundColor Green

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
'@ | ForEach-Object { Replace-Placeholders $_ }
$deployScriptContent | Set-Content -Path 'scripts/deploy.ps1' -Encoding utf8

# 11. Create a clean README.md
@'
# __MOD_NAME__

__DESCRIPTION__

## Supported Mod Loaders
- **Fabric** (Fabric 0.160.6+26.3)
- **NeoForge** (NeoForge 26.3.0.1-beta)
- **MinecraftForge** (Forge 66.0.10)

## Building
- Build Universal JAR (Fabric + NeoForge + Forge combined):
  ```powershell
  .\gradlew.bat universalJar
  ```
- Build Fabric only:
  ```powershell
  .\gradlew.bat buildFabric
  ```
- Build NeoForge only:
  ```powershell
  .\gradlew.bat buildNeoForge
  ```
- Build Forge only:
  ```powershell
  .\gradlew.bat buildForge
  ```

## Deploying
Deploy the compiled JAR to MultiMC and Downloads:
```powershell
.\scripts\deploy.ps1 -Loader universal
```
'@ | ForEach-Object { Replace-Placeholders $_ } | Set-Content -Path 'README.md' -Encoding utf8

# 12. Clean build directories
Write-Host "[*] Cleaning build caches..." -ForegroundColor Cyan
$buildDirs = @("build", ".gradle", "common/build", "fabric/build", "forge/build", "neoforge/build")
foreach ($b in $buildDirs) {
    if (Test-Path $b) {
        Remove-Item -Path $b -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# 13. Git commit
Write-Host "[*] Staging files and creating commit..." -ForegroundColor Cyan
git add -A
git commit -m "feat: initialize clean multiloader mod template ($ModName)"

Write-Host "`n[OK] Template successfully created on branch '$BranchName'!" -ForegroundColor Green

# 14. Git push (if requested)
if ($Push) {
    Write-Host "[*] Pushing branch '$BranchName' to remote '$Remote'..." -ForegroundColor Cyan
    git push -u $Remote $BranchName
    Write-Host "[OK] Successfully pushed '$BranchName' to '$Remote'!" -ForegroundColor Green
} else {
    Write-Host "`nTo push this branch to GitHub/remote, run:" -ForegroundColor Yellow
    Write-Host "    git push -u $Remote $BranchName`n" -ForegroundColor White
}
