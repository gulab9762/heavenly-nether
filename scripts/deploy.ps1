param (
    [string]$Loader = "universal",
    [string]$MultiMcModsDir = "D:\MultiMC\instances\26.3\.minecraft\mods",
    [string]$DownloadsDir = "$env:USERPROFILE\Downloads"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deploying Unharvest Mod [$Loader]" -ForegroundColor Cyan
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

$jar = Get-ChildItem -Path $searchPath -Filter "*unharvest*.jar" | 
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

# Remove older versions of mod in mods directory to prevent duplicate collisions
Get-ChildItem -Path $MultiMcModsDir -Include "*unharvest*.jar","*treeshake*.jar" -File | ForEach-Object {
    if ($_.Name -ne $jarName) {
        Remove-Item -Path $_.FullName -Force
        Write-Host "[INFO] Removed older mod JAR: $($_.Name)" -ForegroundColor DarkGray
    }
}

Copy-Item -Path $jar.FullName -Destination $MultiMcModsDir -Force
Write-Host "[OK] Copied to MultiMC  -> $MultiMcModsDir\$jarName" -ForegroundColor Green

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
