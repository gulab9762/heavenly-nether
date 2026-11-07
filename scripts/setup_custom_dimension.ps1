$dataDir = "common/src/main/resources/data"
$bpDir = "$dataDir/blockplace"

# 1. Ensure target blockplace directories exist
$dirs = @(
    "$bpDir/dimension",
    "$bpDir/dimension_type",
    "$bpDir/worldgen/noise_settings",
    "$bpDir/worldgen/material_rule",
    "$bpDir/worldgen/biome",
    "$bpDir/worldgen/feature",
    "$bpDir/worldgen/placed_feature"
)
foreach ($d in $dirs) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

# 2. Create dimension/heavenly_nether.json
@'
{
  "type": "blockplace:heavenly_nether",
  "generator": {
    "type": "minecraft:noise",
    "settings": "blockplace:heavenly_nether",
    "biome_source": {
      "type": "minecraft:multi_noise",
      "biomes": [
        {
          "biome": "blockplace:heavenly_wastes",
          "parameters": {
            "temperature": 0.0,
            "humidity": 0.0,
            "continentalness": 0.0,
            "erosion": 0.0,
            "weirdness": 0.0,
            "depth": 0.0,
            "offset": 0.0
          }
        },
        {
          "biome": "blockplace:crystal_valley",
          "parameters": {
            "temperature": 0.0,
            "humidity": -0.5,
            "continentalness": 0.0,
            "erosion": 0.0,
            "weirdness": 0.0,
            "depth": 0.0,
            "offset": 0.0
          }
        },
        {
          "biome": "blockplace:cherry_haven",
          "parameters": {
            "temperature": 0.4,
            "humidity": 0.0,
            "continentalness": 0.0,
            "erosion": 0.0,
            "weirdness": 0.0,
            "depth": 0.0,
            "offset": 0.0
          }
        },
        {
          "biome": "blockplace:azalea_haven",
          "parameters": {
            "temperature": 0.0,
            "humidity": 0.5,
            "continentalness": 0.0,
            "erosion": 0.0,
            "weirdness": 0.0,
            "depth": 0.0,
            "offset": 0.375
          }
        },
        {
          "biome": "blockplace:prismarine_deltas",
          "parameters": {
            "temperature": -0.5,
            "humidity": 0.0,
            "continentalness": 0.0,
            "erosion": 0.0,
            "weirdness": 0.0,
            "depth": 0.0,
            "offset": 0.175
          }
        }
      ]
    }
  }
}
'@ | Set-Content -Path "$bpDir/dimension/heavenly_nether.json" -Encoding utf8

# 3. Create dimension_type/heavenly_nether.json
@'
{
  "ambient_light": 0.4,
  "attributes": {
    "minecraft:gameplay/bed_rule": {
      "can_set_spawn": "never",
      "can_sleep": "never",
      "destroy_on_use": true
    },
    "minecraft:gameplay/can_start_raid": false,
    "minecraft:gameplay/fast_lava": false,
    "minecraft:gameplay/piglins_zombify": false,
    "minecraft:gameplay/respawn_anchor_works": true,
    "minecraft:gameplay/sky_light_level": 15.0,
    "minecraft:gameplay/snow_golem_melts": false,
    "minecraft:gameplay/straw_bed_rule": {
      "can_set_spawn": "never",
      "can_sleep": "never",
      "destroy_on_use": true
    },
    "minecraft:gameplay/water_evaporates": false,
    "minecraft:visual/ambient_light_color": "#c8e8ff",
    "minecraft:visual/default_dripstone_particle": {
      "type": "minecraft:dripping_dripstone_water"
    },
    "minecraft:visual/fog_end_distance": 256.0,
    "minecraft:visual/fog_start_distance": 48.0,
    "minecraft:visual/sky_light_color": "#fffbf0",
    "minecraft:visual/sky_light_factor": 0.3
  },
  "cardinal_light": "nether",
  "coordinate_scale": 8.0,
  "has_ceiling": true,
  "has_ender_dragon_fight": false,
  "has_fixed_time": true,
  "has_skylight": false,
  "height": 256,
  "infiniburn": "#minecraft:infiniburn_nether",
  "logical_height": 128,
  "min_y": 0,
  "monster_spawn_block_light_limit": 15,
  "monster_spawn_light_level": 7,
  "skybox": "none",
  "timelines": "#minecraft:in_nether"
}
'@ | Set-Content -Path "$bpDir/dimension_type/heavenly_nether.json" -Encoding utf8

# 4. Create worldgen/noise_settings/heavenly_nether.json
$noiseSettings = Get-Content "$dataDir/minecraft/worldgen/noise_settings/nether.json" -Raw
$noiseSettings = $noiseSettings -replace '"material_rule":\s*"minecraft:nether"', '"material_rule": "blockplace:heavenly_nether"'
$noiseSettings | Set-Content -Path "$bpDir/worldgen/noise_settings/heavenly_nether.json" -Encoding utf8

# 5. Create worldgen/material_rule/heavenly_nether.json with custom biomes
$materialRule = Get-Content "$dataDir/minecraft/worldgen/material_rule/nether.json" -Raw
$materialRule = $materialRule -replace '"biome_is":\s*"minecraft:basalt_deltas"', '"biome_is": "blockplace:prismarine_deltas"'
$materialRule = $materialRule -replace '"biome_is":\s*"minecraft:soul_sand_valley"', '"biome_is": "blockplace:crystal_valley"'
$materialRule = $materialRule -replace '"biome_is":\s*"minecraft:warped_forest"', '"biome_is": "blockplace:azalea_haven"'
$materialRule = $materialRule -replace '"biome_is":\s*"minecraft:crimson_forest"', '"biome_is": "blockplace:cherry_haven"'
$materialRule = $materialRule -replace '"biome_is":\s*"minecraft:nether_wastes"', '"biome_is": "blockplace:heavenly_wastes"'
$materialRule | Set-Content -Path "$bpDir/worldgen/material_rule/heavenly_nether.json" -Encoding utf8

# 6. Copy features & placed features to blockplace
Copy-Item -Path "$dataDir/minecraft/worldgen/feature/*" -Destination "$bpDir/worldgen/feature/" -Force
Copy-Item -Path "$dataDir/minecraft/worldgen/placed_feature/*" -Destination "$bpDir/worldgen/placed_feature/" -Force

# Also add placed feature wrappers for custom fungi
@'
{
  "feature": "blockplace:crimson_fungus",
  "placement": [
    {
      "type": "minecraft:count_on_every_layer",
      "count": 8
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$bpDir/worldgen/placed_feature/cherry_fungus.json" -Encoding utf8

@'
{
  "feature": "blockplace:warped_fungus",
  "placement": [
    {
      "type": "minecraft:count_on_every_layer",
      "count": 8
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$bpDir/worldgen/placed_feature/azalea_fungus.json" -Encoding utf8

# 7. Create biomes
$biomeMap = @{
    "heavenly_wastes.json" = "scripts/vanilla_nether/nether_wastes.json"
    "crystal_valley.json" = "scripts/vanilla_nether/soul_sand_valley.json"
    "cherry_haven.json" = "scripts/vanilla_nether/crimson_forest.json"
    "azalea_haven.json" = "scripts/vanilla_nether/warped_forest.json"
    "prismarine_deltas.json" = "scripts/vanilla_nether/basalt_deltas.json"
}

$biomeConfigs = @{
    "heavenly_wastes.json" = @{ fog = "#b0e0ff"; water = "#38bdf8" }
    "crystal_valley.json" = @{ fog = "#e0d4f7"; water = "#818cf8" }
    "cherry_haven.json" = @{ fog = "#fed7e2"; water = "#f472b6"; particle = "minecraft:cherry_leaves" }
    "azalea_haven.json" = @{ fog = "#ccfbf1"; water = "#2dd4bf" }
    "prismarine_deltas.json" = @{ fog = "#bae6fd"; water = "#0ea5e9" }
}

foreach ($b in $biomeMap.Keys) {
    $src = $biomeMap[$b]
    $json = Get-Content $src -Raw | ConvertFrom-Json
    
    $cfg = $biomeConfigs[$b]
    $json.attributes.'minecraft:visual/fog_color' = $cfg.fog
    $json.effects.water_color = $cfg.water
    
    if ($cfg.particle -and $json.attributes.'minecraft:visual/ambient_particles') {
        $json.attributes.'minecraft:visual/ambient_particles'.argument[0].particle.type = $cfg.particle
    }
    
    # Update feature references to blockplace namespace where overridden
    $jsonStr = $json | ConvertTo-Json -Depth 30
    $jsonStr = $jsonStr -replace '"minecraft:ore_magma"', '"blockplace:ore_magma"'
    $jsonStr = $jsonStr -replace '"minecraft:ore_nether_gold"', '"blockplace:ore_nether_gold"'
    $jsonStr = $jsonStr -replace '"minecraft:ore_quartz_nether"', '"blockplace:ore_quartz"'
    $jsonStr = $jsonStr -replace '"minecraft:ore_gravel_nether"', '"blockplace:ore_gravel_nether"'
    $jsonStr = $jsonStr -replace '"minecraft:ore_blackstone"', '"blockplace:ore_blackstone"'
    $jsonStr = $jsonStr -replace '"minecraft:patch_fire"', '"blockplace:patch_fire"'
    $jsonStr = $jsonStr -replace '"minecraft:patch_soul_fire"', '"blockplace:patch_soul_fire"'
    $jsonStr = $jsonStr -replace '"minecraft:glowstone"', '"blockplace:glowstone"'
    $jsonStr = $jsonStr -replace '"minecraft:glowstone_extra"', '"blockplace:glowstone_extra"'
    $jsonStr = $jsonStr -replace '"minecraft:spring_lava"', '"blockplace:spring_lava_nether"'
    $jsonStr = $jsonStr -replace '"minecraft:spring_open"', '"blockplace:spring_nether_open"'
    $jsonStr = $jsonStr -replace '"minecraft:spring_closed"', '"blockplace:spring_nether_closed"'
    $jsonStr = $jsonStr -replace '"minecraft:crimson_fungi"', '"blockplace:cherry_fungus"'
    $jsonStr = $jsonStr -replace '"minecraft:warped_fungi"', '"blockplace:azalea_fungus"'
    $jsonStr = $jsonStr -replace '"minecraft:weeping_vines"', '"blockplace:weeping_vines"'
    
    $jsonStr | Set-Content -Path "$bpDir/worldgen/biome/$b" -Encoding utf8
}

Write-Output "Custom dimension blockplace:heavenly_nether created."

# 8. Remove data/minecraft overrides so vanilla Nether is 100% restored
Remove-Item -Path "$dataDir/minecraft" -Recurse -Force
Write-Output "Removed data/minecraft - vanilla Nether is completely restored."
