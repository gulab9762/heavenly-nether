$biomeDir = "common/src/main/resources/data/minecraft/worldgen/biome"
New-Item -ItemType Directory -Force -Path $biomeDir | Out-Null

$configs = @{
    "nether_wastes.json" = @{
        fog = "#b0e0ff"
        water = "#38bdf8"
    }
    "soul_sand_valley.json" = @{
        fog = "#e0d4f7"
        water = "#818cf8"
    }
    "crimson_forest.json" = @{
        fog = "#fed7e2"
        water = "#f472b6"
        particle = "minecraft:cherry_leaves"
    }
    "warped_forest.json" = @{
        fog = "#ccfbf1"
        water = "#2dd4bf"
    }
    "basalt_deltas.json" = @{
        fog = "#bae6fd"
        water = "#0ea5e9"
    }
}

foreach ($item in $configs.GetEnumerator()) {
    $srcPath = "scripts/vanilla_nether/$($item.Key)"
    if (Test-Path $srcPath) {
        $json = Get-Content $srcPath -Raw | ConvertFrom-Json
        
        # update fog_color
        if ($json.attributes.'minecraft:visual/fog_color') {
            $json.attributes.'minecraft:visual/fog_color' = $item.Value.fog
        }
        
        # update water_color
        if ($json.effects.water_color) {
            $json.effects.water_color = $item.Value.water
        }
        
        # update particle if present
        if ($item.Value.particle -and $json.attributes.'minecraft:visual/ambient_particles') {
            $json.attributes.'minecraft:visual/ambient_particles'.argument[0].particle.type = $item.Value.particle
        }
        
        $destPath = "$biomeDir/$($item.Key)"
        $json | ConvertTo-Json -Depth 30 | Set-Content -Path $destPath -Encoding utf8
        Write-Output "Updated biome $destPath"
    } else {
        Write-Warning "Could not find $srcPath"
    }
}
