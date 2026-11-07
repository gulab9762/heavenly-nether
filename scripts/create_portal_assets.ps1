$assetsDir = "common/src/main/resources/assets/heavenlynether"
New-Item -ItemType Directory -Force -Path "$assetsDir/blockstates" | Out-Null
New-Item -ItemType Directory -Force -Path "$assetsDir/models/block" | Out-Null
New-Item -ItemType Directory -Force -Path "$assetsDir/textures/block" | Out-Null

# 1. blockstates/heavenly_portal.json
@'
{
  "variants": {
    "axis=x": {
      "model": "heavenlynether:block/heavenly_portal_ew"
    },
    "axis=z": {
      "model": "heavenlynether:block/heavenly_portal_ns"
    }
  }
}
'@ | Set-Content -Path "$assetsDir/blockstates/heavenly_portal.json" -Encoding utf8

# 2. models/block/heavenly_portal_ew.json
@'
{
  "textures": {
    "particle": "heavenlynether:block/heavenly_portal",
    "portal": "heavenlynether:block/heavenly_portal"
  },
  "elements": [
    {
      "from": [6, 0, 0],
      "to": [10, 16, 16],
      "faces": {
        "east": {"uv": [0, 0, 16, 16], "texture": "#portal"},
        "west": {"uv": [0, 0, 16, 16], "texture": "#portal"}
      }
    }
  ]
}
'@ | Set-Content -Path "$assetsDir/models/block/heavenly_portal_ew.json" -Encoding utf8

# 3. models/block/heavenly_portal_ns.json
@'
{
  "textures": {
    "particle": "heavenlynether:block/heavenly_portal",
    "portal": "heavenlynether:block/heavenly_portal"
  },
  "elements": [
    {
      "from": [0, 0, 6],
      "to": [16, 16, 10],
      "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#portal"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#portal"}
      }
    }
  ]
}
'@ | Set-Content -Path "$assetsDir/models/block/heavenly_portal_ns.json" -Encoding utf8

# 4. Generate heavenly_portal.png texture
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 16, 16
for ($x = 0; $x -lt 16; $x++) {
    for ($y = 0; $y -lt 16; $y++) {
        # Ethereal celestial gradient with crystalline stars
        $r = [int](140 + 60 * [Math]::Sin(($x + $y) * 0.4))
        $g = [int](220 + 35 * [Math]::Cos(($x - $y) * 0.5))
        $b = [int](255)
        $a = [int](185 + 40 * [Math]::Sin(($x * $y) * 0.3))
        
        # Add sparkling bright stars
        if (($x -eq 3 -and $y -eq 4) -or ($x -eq 11 -and $y -eq 2) -or 
            ($x -eq 8 -and $y -eq 12) -or ($x -eq 13 -and $y -eq 9) -or
            ($x -eq 5 -and $y -eq 8) -or ($x -eq 2 -and $y -eq 13)) {
            $r = 255; $g = 255; $b = 255; $a = 245
        }
        
        $color = [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
        $bmp.SetPixel($x, $y, $color)
    }
}
$bmp.Save("$assetsDir/textures/block/heavenly_portal.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "Generated portal blockstates, models, and texture."
