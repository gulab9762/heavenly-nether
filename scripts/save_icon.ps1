Add-Type -AssemblyName System.Drawing
$src = "C:\Users\Gulab\.gemini\antigravity-ide\brain\d4a59ed3-d261-4375-8ba8-fc38c3626d20\heavenly_icon_v2_1792785913174.jpg"
$img = [System.Drawing.Image]::FromFile($src)

$targets = @(
    "c:\Users\Gulab\IdeaProjects\block-place\icon.png",
    "c:\Users\Gulab\IdeaProjects\block-place\fabric\src\main\resources\icon.png",
    "c:\Users\Gulab\IdeaProjects\block-place\neoforge\src\main\resources\icon.png"
)

foreach ($target in $targets) {
    $parent = Split-Path -Parent $target
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $img.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host "Saved: $target"
}

$img.Dispose()
