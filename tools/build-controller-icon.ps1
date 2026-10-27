param([Parameter(Mandatory = $true)][string]$Source)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$sourceBitmap = [Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $Source))
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
$outline = 3
$width = 257; $height = [int][Math]::Round($width * $sourceHeight / $sourceWidth)
$scaled = [Drawing.Bitmap]::new($width, $height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($scaled)
$graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.DrawImage($sourceBitmap, [Drawing.Rectangle]::new(0, 0, $width, $height), $minX, $minY, $sourceWidth, $sourceHeight, [Drawing.GraphicsUnit]::Pixel)
$graphics.Dispose(); $sourceBitmap.Dispose()
$canvas = [Drawing.Bitmap]::new(432, 432, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$left = [int][Math]::Floor((432 - $width) / 2); $top = [int][Math]::Floor((432 - $height) / 2)
for ($y = 0; $y -lt $height; $y++) {
    for ($x = 0; $x -lt $width; $x++) {
        if ($scaled.GetPixel($x, $y).A -gt 16) {
            for ($dy = -$outline; $dy -le $outline; $dy++) {
                for ($dx = -$outline; $dx -le $outline; $dx++) {
                    if ($dx*$dx + $dy*$dy -le $outline*$outline) {
                        $canvas.SetPixel($left+$x+$dx, $top+$y+$dy, [Drawing.Color]::Black)
                    }
                }
            }
        }
    }
}
$graphics = [Drawing.Graphics]::FromImage($canvas)
$graphics.DrawImageUnscaled($scaled, $left, $top)
$graphics.Dispose(); $scaled.Dispose()
$res = Join-Path $PSScriptRoot '../apps/android/app/src/main/res'
$canvas.Save((Join-Path $res 'drawable/ic_launcher_foreground.png'), [Drawing.Imaging.ImageFormat]::Png)
$densities = @{mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192}
foreach ($density in $densities.Keys) {
    $size = [int]$densities[$density]
    $bitmap = [Drawing.Bitmap]::new($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($canvas, 0, 0, $size, $size)
    $bitmap.Save((Join-Path $res "mipmap-$density/ic_launcher.png"), [Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Save((Join-Path $res "mipmap-$density/ic_controller_transparent.png"), [Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose()
}
$canvas.Dispose()
Write-Output "Source content: ${sourceWidth}x${sourceHeight}; scaled content: ${width}x${height}; canvas: 432x432"
