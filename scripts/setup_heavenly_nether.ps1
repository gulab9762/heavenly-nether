$baseDir = "common/src/main/resources/data/minecraft"

# Ensure directories exist
$dirs = @(
    "$baseDir/dimension_type",
    "$baseDir/worldgen/noise_settings",
    "$baseDir/worldgen/material_rule",
    "$baseDir/worldgen/biome",
    "$baseDir/worldgen/feature",
    "$baseDir/worldgen/placed_feature"
)
foreach ($d in $dirs) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

Write-Output "Directories created."
