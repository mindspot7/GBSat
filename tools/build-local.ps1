<#
.SYNOPSIS
Build and optionally install a locally signed official GBSat APK.
.EXAMPLE
./tools/build-local.ps1 -BuildNumber 377 -Install
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateRange(1, 2100000000)][int]$BuildNumber,
    [string]$SigningDirectory,
    [switch]$Install,
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$androidProject = Join-Path $repo 'apps/android'
if (-not $SigningDirectory) { $SigningDirectory = $env:GBSAT_SIGNING_DIR }
if (-not $SigningDirectory) { $SigningDirectory = [Environment]::GetEnvironmentVariable('GBSAT_SIGNING_DIR', 'User') }
if (-not $SigningDirectory) { throw 'Set GBSAT_SIGNING_DIR or pass -SigningDirectory pointing to the external signing folder.' }
$keystore = Join-Path $SigningDirectory 'gbsat-release.jks'
$passwordFile = Join-Path $SigningDirectory 'GBSAT_KEYSTORE_PASSWORD.txt'
if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $passwordFile)) {
    throw 'The external signing folder must contain gbsat-release.jks and GBSAT_KEYSTORE_PASSWORD.txt.'
}
$savedEnvironment = @{}
foreach ($name in @('ANDROID_HOME', 'PATH', 'GBSAT_KEYSTORE_FILE', 'GBSAT_KEYSTORE_PASSWORD')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location $androidProject
try {
    if (-not $env:ANDROID_HOME) {
        $env:ANDROID_HOME = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    }
    if (-not (Test-Path -LiteralPath $env:ANDROID_HOME)) { throw 'Android SDK not found; set ANDROID_HOME.' }
    if (-not (Get-Command dxc -ErrorAction SilentlyContinue)) {
        $toolchains = Join-Path (Split-Path $repo -Parent) 'Toolchains'
        $dxc = Get-ChildItem -Path "$toolchains/dxc*/bin/x64/dxc.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if (-not $dxc) { throw 'DXC with SPIR-V support is required; add its bin directory to PATH.' }
        $env:PATH = $dxc.DirectoryName + ';' + $env:PATH
    }
    $env:GBSAT_KEYSTORE_FILE = (Resolve-Path -LiteralPath $keystore).Path
    $env:GBSAT_KEYSTORE_PASSWORD = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $passwordFile).Path).TrimEnd("`r", "`n")
    if (-not $env:GBSAT_KEYSTORE_PASSWORD) { throw 'The signing password file is empty.' }
    # Pass the password only through this process environment, never command-line arguments.
    & ./gradlew.bat --no-daemon --no-configuration-cache "-PgbsatVersionCode=$BuildNumber" :app:assembleRelease :app:testReleaseUnitTest --tests '*AppIconResourcesTest' --tests '*LibraryStartupTest' --tests '*GbSatPausePolicyTest' :gbframe:testReleaseUnitTest
    if ($LASTEXITCODE -ne 0) { throw 'Local build/tests failed.' }
    $sdkBuildTools = Join-Path $env:ANDROID_HOME 'build-tools'
    $apksigner = Get-ChildItem -Path "$sdkBuildTools/*/apksigner.bat" |
        Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $apksigner) { throw 'Android apksigner not found.' }
    $apk = Join-Path $androidProject 'app/build/outputs/apk/release/app-release.apk'
    & $apksigner.FullName verify $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing verification failed.' }
    $version = [IO.File]::ReadAllText((Join-Path $androidProject 'VERSION')).Trim()
    $output = Join-Path $repo "test-output/local-build$BuildNumber"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $namedApk = Join-Path $output "GBSat-v$version-build$BuildNumber.apk"
    Copy-Item -LiteralPath $apk -Destination $namedApk
    if ($Install) {
        $adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
        $adbArgs = @()
        if ($Serial) { $adbArgs += @('-s', $Serial) }
        & $adb @adbArgs install -r $namedApk
        if ($LASTEXITCODE -ne 0) { throw 'Installation failed; the existing app was not uninstalled.' }
    }
    Write-Output "APK: $namedApk"
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    Pop-Location
}
