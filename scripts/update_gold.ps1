$files = Get-ChildItem -Path 'common/src/main/resources/data/blockplace/worldgen/biome' -Filter '*.json'
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace '"minecraft:ore_gold_nether"', '"blockplace:ore_nether_gold"'
    $content | Set-Content $f.FullName -Encoding utf8
}
Write-Output "Updated biomes to use blockplace:ore_nether_gold."
