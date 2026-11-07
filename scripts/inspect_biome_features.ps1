$files = Get-ChildItem -Path 'common/src/main/resources/data/blockplace/worldgen/biome' -Filter '*.json'
$placedDir = "common/src/main/resources/data/blockplace/worldgen/placed_feature"

foreach ($f in $files) {
    $json = Get-Content $f.FullName -Raw | ConvertFrom-Json
    Write-Output "`n=== $($f.Name) ==="
    foreach ($step in $json.features) {
        foreach ($feat in $step) {
            if ($feat -like 'blockplace:*') {
                $leaf = ($feat -replace 'blockplace:', '') + ".json"
                $exists = Test-Path "$placedDir/$leaf"
                Write-Output "  $feat -> Exists: $exists"
            } else {
                Write-Output "  $feat (vanilla)"
            }
        }
    }
}
