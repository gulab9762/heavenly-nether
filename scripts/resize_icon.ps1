Add-Type -AssemblyName System.Drawing

$src = "c:\Users\Gulab\IdeaProjects\block-place\icon.png"
$orig = [System.Drawing.Image]::FromFile($src)

$width = 512
$height = 512

$bmp = New-Object System.Drawing.Bitmap $width, $height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

$g.DrawImage($orig, 0, 0, $width, $height)
$orig.Dispose()
$g.Dispose()

$targets = @(
    "c:\Users\Gulab\IdeaProjects\block-place\icon.png",
    "c:\Users\Gulab\IdeaProjects\block-place\fabric\src\main\resources\icon.png",
    "c:\Users\Gulab\IdeaProjects\block-place\neoforge\src\main\resources\icon.png"
)

foreach ($target in $targets) {
    $bmp.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $sizeKb = [math]::Round(((Get-Item $target).Length / 1KB), 2)
    Write-Host "Saved $target ($sizeKb KB)"
}

$bmp.Dispose()
