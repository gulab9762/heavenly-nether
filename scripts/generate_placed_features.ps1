$featureDir = "common/src/main/resources/data/minecraft/worldgen/feature"
$placedFeatureDir = "common/src/main/resources/data/minecraft/worldgen/placed_feature"
New-Item -ItemType Directory -Force -Path $placedFeatureDir | Out-Null

# glowstone_extra.json -> ochre_froglight
@'
{
  "type": "minecraft:random_neighbor_spread",
  "accepted_neighbors": "minecraft:ochre_froglight",
  "attempts": 1500,
  "block": {
    "id": "minecraft:ochre_froglight"
  },
  "can_replace": {
    "type": "minecraft:matching_block_tag",
    "tag": "minecraft:air"
  },
  "xz_offset": {
    "type": "minecraft:trapezoid",
    "max": 7,
    "min": -7,
    "plateau": 0
  },
  "y_offset": {
    "type": "minecraft:uniform",
    "max_inclusive": 0,
    "min_inclusive": -11
  }
}
'@ | Set-Content -Path "$featureDir/glowstone_extra.json" -Encoding utf8

# placed_feature/glowstone.json
@'
{
  "feature": "minecraft:glowstone_extra",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 10
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 0
        },
        "min_inclusive": {
          "above_bottom": 0
        }
      }
    },
    {
      "type": "minecraft:block_predicate_filter",
      "predicate": {
        "type": "minecraft:all_of",
        "predicates": [
          {
            "type": "minecraft:matching_block_tag",
            "tag": "minecraft:air"
          },
          {
            "type": "minecraft:matching_blocks",
            "blocks": [
              "minecraft:calcite",
              "minecraft:smooth_quartz",
              "minecraft:prismarine",
              "minecraft:prismarine_bricks"
            ],
            "offset": [
              0,
              1,
              0
            ]
          }
        ]
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedFeatureDir/glowstone.json" -Encoding utf8

# placed_feature/weeping_vines.json -> cave_vine
@'
{
  "feature": "minecraft:cave_vine",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 15
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 0
        },
        "min_inclusive": {
          "above_bottom": 0
        }
      }
    },
    {
      "type": "minecraft:biome"
    },
    {
      "type": "minecraft:block_predicate_filter",
      "predicate": {
        "type": "minecraft:all_of",
        "predicates": [
          {
            "type": "minecraft:matching_block_tag",
            "tag": "minecraft:air"
          },
          {
            "type": "minecraft:matching_blocks",
            "blocks": [
              "minecraft:calcite",
              "minecraft:cherry_leaves",
              "minecraft:smooth_quartz",
              "minecraft:moss_block"
            ],
            "offset": [
              0,
              1,
              0
            ]
          }
        ]
      }
    }
  ]
}
'@ | Set-Content -Path "$placedFeatureDir/weeping_vines.json" -Encoding utf8

Write-Output "Glowstone and placed features created successfully."
