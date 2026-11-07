Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = 'C:\Users\Gulab\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$files = @(
    'ore_magma.json', 'ore_blackstone.json', 'ore_gravel_nether.json', 'ore_quartz_nether.json', 
    'patch_fire.json', 'patch_soul_fire.json', 'spring_lava.json', 'spring_open.json', 'spring_closed.json',
    'glowstone_extra.json', 'crimson_fungi.json', 'warped_fungi.json'
)
foreach ($f in $files) {
    $entry = $zip.GetEntry("data/minecraft/worldgen/placed_feature/$f")
    if ($entry) {
        Write-Output "=== $f ==="
        $r = New-Object System.IO.StreamReader($entry.Open())
        Write-Output $r.ReadToEnd()
    } else {
        Write-Output "NOT FOUND: $f"
    }
}
$zip.Dispose()
