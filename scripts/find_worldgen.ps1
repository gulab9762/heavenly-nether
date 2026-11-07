Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = 'C:\Users\Gulab\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$found = $zip.Entries | Where-Object { $_.FullName -like 'data/minecraft/dimension*nether*.json' -or $_.FullName -like 'data/minecraft/worldgen/noise_settings/*nether*.json' }
foreach ($e in $found) {
    Write-Output $e.FullName
}
$zip.Dispose()
