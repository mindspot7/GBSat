param(
    [string]$Source = (Join-Path $PSScriptRoot '../apps/android/app/src/main/res/drawable/ic_launcher_foreground.png')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$res = Join-Path $PSScriptRoot '../apps/android/app/src/main/res'
$sourceBitmap = [Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $Source))
try {
    $minX = $sourceBitmap.Width; $minY = $sourceBitmap.Height; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $sourceBitmap.Height; $y++) {
        for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
            if ($sourceBitmap.GetPixel($x, $y).A -gt 8) {
                $minX = [Math]::Min($minX, $x); $maxX = [Math]::Max($maxX, $x)
                $minY = [Math]::Min($minY, $y); $maxY = [Math]::Max($maxY, $y)
            }
        }
    }
    if ($maxX -lt 0) { throw 'Source icon is empty' }
    $sourceWidth = $maxX - $minX + 1; $sourceHeight = $maxY - $minY + 1
    $densities = @{mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192}
    foreach ($density in $densities.Keys) {
        $size = [int]$densities[$density]
        # Adaptive foreground safe-zone padding must not shrink the standalone raster.
        $scale = $size * 0.85 / [Math]::Max($sourceWidth, $sourceHeight)
        $width = [int][Math]::Round($sourceWidth * $scale)
        $height = [int][Math]::Round($sourceHeight * $scale)
        $left = [int][Math]::Floor(($size - $width) / 2)
        $top = [int][Math]::Floor(($size - $height) / 2)
        $bitmap = [Drawing.Bitmap]::new($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($sourceBitmap, [Drawing.Rectangle]::new($left, $top, $width, $height),
                $minX, $minY, $sourceWidth, $sourceHeight, [Drawing.GraphicsUnit]::Pixel)
            foreach ($name in @('ic_launcher', 'ic_controller_transparent')) {
                $bitmap.Save((Join-Path $res "mipmap-$density/$name.png"), [Drawing.Imaging.ImageFormat]::Png)
            }
        } finally { $graphics.Dispose(); $bitmap.Dispose() }
        Write-Output "${density}: ${size}x${size}, visible controller approximately ${width}x${height}"
    }
} finally { $sourceBitmap.Dispose() }
