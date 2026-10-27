param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot 'icon-source/build285')
)
$ErrorActionPreference = 'Stop'
$res = Join-Path $PSScriptRoot '../apps/android/app/src/main/res'
# The user selected the original transparent build-285 launcher icons. Copy the
# approved files verbatim: do not add adaptive padding, outlines or resampling.
foreach ($density in @('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')) {
    $source = Join-Path $SourceDirectory "$density.png"
    if (-not (Test-Path -LiteralPath $source)) { throw "Missing approved icon: $source" }
    foreach ($name in @('ic_launcher', 'ic_controller_transparent')) {
        $destination = Join-Path $res "mipmap-$density/$name.png"
        Copy-Item -LiteralPath $source -Destination $destination
        if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $destination).Hash) {
            throw "Icon copy mismatch: $destination"
        }
    }
    Write-Output "${density}: restored exact approved build-285 PNG"
}
