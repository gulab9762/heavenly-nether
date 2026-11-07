Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = 'C:\Users\Gulab\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)

New-Item -ItemType Directory -Force -Path 'scripts/vanilla_nether' | Out-Null

$entries = $zip.Entries | Where-Object { $_.FullName -like '*the_nether*' -or $_.FullName -like '*material_rule*' -or $_.FullName -like '*noise_settings*' }
foreach ($e in $entries) {
    Write-Output $e.FullName
    $dest = Join-Path 'scripts/vanilla_nether' ($e.FullName -replace '[/:]', '_')
    $reader = New-Object System.IO.StreamReader($e.Open())
    $reader.ReadToEnd() | Out-File -FilePath $dest -Encoding utf8
}

$zip.Dispose()
