$featureDir = "common/src/main/resources/data/minecraft/worldgen/feature"
New-Item -ItemType Directory -Force -Path $featureDir | Out-Null

# 1. ore_magma.json -> sea_lantern
@'
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": 33,
  "targets": [
    {
      "state": "minecraft:sea_lantern",
      "target": {
        "block": "minecraft:calcite",
        "predicate_type": "minecraft:block_match"
      }
    }
  ]
}
'@ | Set-Content -Path "$featureDir/ore_magma.json" -Encoding utf8

# 2. ore_nether_gold.json -> gold_block
@'
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": 10,
  "targets": [
    {
      "state": "minecraft:gold_block",
      "target": {
        "block": "minecraft:calcite",
        "predicate_type": "minecraft:block_match"
      }
    }
  ]
}
'@ | Set-Content -Path "$featureDir/ore_nether_gold.json" -Encoding utf8

# 3. ore_quartz.json -> amethyst_block
@'
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": 16,
  "targets": [
    {
      "state": "minecraft:amethyst_block",
      "target": {
        "block": "minecraft:calcite",
        "predicate_type": "minecraft:block_match"
      }
    }
  ]
}
'@ | Set-Content -Path "$featureDir/ore_quartz.json" -Encoding utf8

# 4. ore_gravel_nether.json -> smooth_quartz
@'
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": 33,
  "targets": [
    {
      "state": "minecraft:smooth_quartz",
      "target": {
        "block": "minecraft:calcite",
        "predicate_type": "minecraft:block_match"
      }
    }
  ]
}
'@ | Set-Content -Path "$featureDir/ore_gravel_nether.json" -Encoding utf8

# 5. ore_blackstone.json -> prismarine
@'
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": 33,
  "targets": [
    {
      "state": "minecraft:prismarine",
      "target": {
        "block": "minecraft:calcite",
        "predicate_type": "minecraft:block_match"
      }
    }
  ]
}
'@ | Set-Content -Path "$featureDir/ore_blackstone.json" -Encoding utf8

# 6. spring_lava_nether.json -> water
@'
{
  "type": "minecraft:spring_feature",
  "state": {
    "id": "minecraft:water",
    "properties": {
      "falling": "true"
    }
  },
  "valid_blocks": [
    "minecraft:calcite",
    "minecraft:smooth_quartz",
    "minecraft:prismarine",
    "minecraft:moss_block"
  ]
}
'@ | Set-Content -Path "$featureDir/spring_lava_nether.json" -Encoding utf8

# 7. spring_nether_open.json -> water
@'
{
  "type": "minecraft:spring_feature",
  "requires_block_below": false,
  "state": {
    "id": "minecraft:water",
    "properties": {
      "falling": "true"
    }
  },
  "valid_blocks": "minecraft:calcite"
}
'@ | Set-Content -Path "$featureDir/spring_nether_open.json" -Encoding utf8

# 8. spring_nether_closed.json -> water
@'
{
  "type": "minecraft:spring_feature",
  "hole_count": 0,
  "requires_block_below": false,
  "rock_count": 5,
  "state": {
    "id": "minecraft:water",
    "properties": {
      "falling": "true"
    }
  },
  "valid_blocks": "minecraft:calcite"
}
'@ | Set-Content -Path "$featureDir/spring_nether_closed.json" -Encoding utf8

# 9. patch_fire.json -> lily_of_the_valley
@'
{
  "type": "minecraft:simple_block",
  "to_place": {
    "id": "minecraft:lily_of_the_valley"
  }
}
'@ | Set-Content -Path "$featureDir/patch_fire.json" -Encoding utf8

# 10. patch_soul_fire.json -> allium
@'
{
  "type": "minecraft:simple_block",
  "to_place": {
    "id": "minecraft:allium"
  }
}
'@ | Set-Content -Path "$featureDir/patch_soul_fire.json" -Encoding utf8

# 11. crimson_fungus.json -> Cherry huge tree
@'
{
  "type": "minecraft:huge_fungus",
  "decor_state": "minecraft:pearlescent_froglight",
  "hat_state": "minecraft:cherry_leaves",
  "replaceable_blocks": {
    "type": "minecraft:matching_blocks",
    "blocks": [
      "minecraft:air",
      "minecraft:cave_air",
      "minecraft:cherry_leaves",
      "minecraft:flowering_azalea_leaves",
      "minecraft:moss_carpet",
      "minecraft:pink_petals",
      "minecraft:lily_of_the_valley"
    ]
  },
  "stem_state": "minecraft:cherry_log",
  "valid_base_block": "minecraft:moss_block"
}
'@ | Set-Content -Path "$featureDir/crimson_fungus.json" -Encoding utf8

# 12. warped_fungus.json -> Azalea huge tree
@'
{
  "type": "minecraft:huge_fungus",
  "decor_state": "minecraft:verdant_froglight",
  "hat_state": "minecraft:flowering_azalea_leaves",
  "replaceable_blocks": {
    "type": "minecraft:matching_blocks",
    "blocks": [
      "minecraft:air",
      "minecraft:cave_air",
      "minecraft:flowering_azalea_leaves",
      "minecraft:azalea_leaves",
      "minecraft:moss_carpet",
      "minecraft:glow_lichen"
    ]
  },
  "stem_state": "minecraft:birch_log",
  "valid_base_block": "minecraft:moss_block"
}
'@ | Set-Content -Path "$featureDir/warped_fungus.json" -Encoding utf8

Write-Output "Features created successfully."
