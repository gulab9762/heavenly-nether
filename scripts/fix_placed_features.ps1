$placedDir = "common/src/main/resources/data/blockplace/worldgen/placed_feature"
New-Item -ItemType Directory -Force -Path $placedDir | Out-Null

# 1. ore_magma.json
@'
{
  "feature": "blockplace:ore_magma",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 4
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "absolute": 36
        },
        "min_inclusive": {
          "absolute": 27
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/ore_magma.json" -Encoding utf8

# 2. ore_blackstone.json
@'
{
  "feature": "blockplace:ore_blackstone",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 2
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "absolute": 31
        },
        "min_inclusive": {
          "absolute": 5
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/ore_blackstone.json" -Encoding utf8

# 3. ore_gravel_nether.json
@'
{
  "feature": "blockplace:ore_gravel_nether",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 2
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "absolute": 41
        },
        "min_inclusive": {
          "absolute": 5
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/ore_gravel_nether.json" -Encoding utf8

# 4. ore_quartz.json
@'
{
  "feature": "blockplace:ore_quartz",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 16
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 10
        },
        "min_inclusive": {
          "above_bottom": 10
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/ore_quartz.json" -Encoding utf8

# 5. patch_fire.json (places lilies on calcite)
@'
{
  "feature": "blockplace:patch_fire",
  "placement": [
    {
      "type": "minecraft:count",
      "count": {
        "type": "minecraft:uniform",
        "max_inclusive": 5,
        "min_inclusive": 0
      }
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 4
        },
        "min_inclusive": {
          "above_bottom": 4
        }
      }
    },
    {
      "type": "minecraft:biome"
    },
    {
      "type": "minecraft:count",
      "count": 64
    },
    {
      "type": "minecraft:offset",
      "x": {
        "type": "minecraft:trapezoid",
        "max": 7,
        "min": -7,
        "plateau": 0
      },
      "y": {
        "type": "minecraft:trapezoid",
        "max": 3,
        "min": -3,
        "plateau": 0
      },
      "z": {
        "type": "minecraft:trapezoid",
        "max": 7,
        "min": -7,
        "plateau": 0
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
              "minecraft:moss_block"
            ],
            "offset": [
              0,
              -1,
              0
            ]
          }
        ]
      }
    }
  ]
}
'@ | Set-Content -Path "$placedDir/patch_fire.json" -Encoding utf8

# 6. patch_soul_fire.json (places alliums on quartz/amethyst)
@'
{
  "feature": "blockplace:patch_soul_fire",
  "placement": [
    {
      "type": "minecraft:count",
      "count": {
        "type": "minecraft:uniform",
        "max_inclusive": 5,
        "min_inclusive": 0
      }
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 4
        },
        "min_inclusive": {
          "above_bottom": 4
        }
      }
    },
    {
      "type": "minecraft:biome"
    },
    {
      "type": "minecraft:count",
      "count": 64
    },
    {
      "type": "minecraft:offset",
      "x": {
        "type": "minecraft:trapezoid",
        "max": 7,
        "min": -7,
        "plateau": 0
      },
      "y": {
        "type": "minecraft:trapezoid",
        "max": 3,
        "min": -3,
        "plateau": 0
      },
      "z": {
        "type": "minecraft:trapezoid",
        "max": 7,
        "min": -7,
        "plateau": 0
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
              "minecraft:smooth_quartz",
              "minecraft:amethyst_block",
              "minecraft:calcite"
            ],
            "offset": [
              0,
              -1,
              0
            ]
          }
        ]
      }
    }
  ]
}
'@ | Set-Content -Path "$placedDir/patch_soul_fire.json" -Encoding utf8

# 7. spring_nether_open.json
@'
{
  "feature": "blockplace:spring_nether_open",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 8
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 4
        },
        "min_inclusive": {
          "above_bottom": 4
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/spring_nether_open.json" -Encoding utf8

# 8. spring_nether_closed.json
@'
{
  "feature": "blockplace:spring_nether_closed",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 16
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 10
        },
        "min_inclusive": {
          "above_bottom": 10
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/spring_nether_closed.json" -Encoding utf8

# 9. spring_lava_nether.json
@'
{
  "feature": "blockplace:spring_lava_nether",
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
        "type": "minecraft:very_biased_to_bottom",
        "inner": 8,
        "max_inclusive": {
          "below_top": 8
        },
        "min_inclusive": {
          "above_bottom": 0
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/spring_lava_nether.json" -Encoding utf8

# 10. glowstone_extra.json
@'
{
  "feature": "blockplace:glowstone_extra",
  "placement": [
    {
      "type": "minecraft:count",
      "count": {
        "type": "minecraft:biased_to_bottom",
        "max_inclusive": 9,
        "min_inclusive": 0
      }
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "max_inclusive": {
          "below_top": 4
        },
        "min_inclusive": {
          "above_bottom": 4
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
'@ | Set-Content -Path "$placedDir/glowstone_extra.json" -Encoding utf8

# 11. ore_nether_gold.json
@'
{
  "feature": "blockplace:ore_nether_gold",
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
          "below_top": 10
        },
        "min_inclusive": {
          "above_bottom": 10
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
'@ | Set-Content -Path "$placedDir/ore_nether_gold.json" -Encoding utf8

Write-Output "All 11 placed_feature files created successfully."
