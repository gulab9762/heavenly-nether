Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = 'C:\Users\Gulab\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)

$files = @(
    'data/minecraft/worldgen/feature/crimson_fungi.json',
    'data/minecraft/worldgen/feature/warped_fungi.json'
)

foreach ($f in $files) {
    $e = $zip.GetEntry($f)
    if ($e) {
        Write-Output "=== $f ==="
        $reader = New-Object System.IO.StreamReader($e.Open())
        Write-Output $reader.ReadToEnd()
    }
}

$zip.Dispose()
