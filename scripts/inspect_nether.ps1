Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = 'C:\Users\Gulab\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)

$entry1 = $zip.GetEntry('data/minecraft/dimension_type/the_nether.json')
$reader1 = New-Object System.IO.StreamReader($entry1.Open())
Write-Output "=== DIMENSION_TYPE/THE_NETHER.JSON ==="
Write-Output $reader1.ReadToEnd()
$reader1.Close()

$entry2 = $zip.GetEntry('data/minecraft/worldgen/noise_settings/nether.json')
$reader2 = New-Object System.IO.StreamReader($entry2.Open())
Write-Output "=== WORLDGEN/NOISE_SETTINGS/NETHER.JSON ==="
Write-Output $reader2.ReadToEnd()
$reader2.Close()

$zip.Dispose()
