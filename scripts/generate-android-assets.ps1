# Generates Android launcher + splash PNGs from resources/icon.png and resources/splash.png
# Run when @capacitor/assets (sharp) cannot install (e.g. Windows ARM).

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$iconSrc = Join-Path $root "resources\icon.png"
$splashSrc = Join-Path $root "resources\splash.png"
$resRoot = Join-Path $root "android\app\src\main\res"

if (-not (Test-Path $iconSrc)) { throw "Missing $iconSrc" }
if (-not (Test-Path $splashSrc)) { throw "Missing $splashSrc" }

function Save-ScaledPng {
    param(
        [System.Drawing.Image]$Source,
        [int]$Width,
        [int]$Height,
        [string]$OutPath,
        [System.Drawing.Color]$Background,
        [double]$ContentScale = 1.0
    )
    $dir = Split-Path $OutPath -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    $bmp = New-Object System.Drawing.Bitmap $Width, $Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($Background)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

    $targetW = [int]($Width * $ContentScale)
    $targetH = [int]($Height * $ContentScale)
    $x = ($Width - $targetW) / 2
    $y = ($Height - $targetH) / 2
    $g.DrawImage($Source, $x, $y, $targetW, $targetH)
    $g.Dispose()
    $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$icon = [System.Drawing.Image]::FromFile((Resolve-Path $iconSrc))
$splash = [System.Drawing.Image]::FromFile((Resolve-Path $splashSrc))
$white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)

$launcherSizes = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}
$foregroundSizes = @{
    "mipmap-mdpi"    = 108
    "mipmap-hdpi"    = 162
    "mipmap-xhdpi"   = 216
    "mipmap-xxhdpi"  = 324
    "mipmap-xxxhdpi" = 432
}

foreach ($folder in $launcherSizes.Keys) {
    $px = $launcherSizes[$folder]
    $base = Join-Path $resRoot $folder
    Save-ScaledPng $icon $px $px (Join-Path $base "ic_launcher.png") $white 0.92
    Save-ScaledPng $icon $px $px (Join-Path $base "ic_launcher_round.png") $white 0.92
}

foreach ($folder in $foregroundSizes.Keys) {
    $px = $foregroundSizes[$folder]
    $base = Join-Path $resRoot $folder
    Save-ScaledPng $icon $px $px (Join-Path $base "ic_launcher_foreground.png") $white 0.55
}

$splashPort = @{
    "drawable-mdpi"    = @(320, 480)
    "drawable-hdpi"    = @(480, 800)
    "drawable-xhdpi"   = @(720, 1280)
    "drawable-xxhdpi"  = @(960, 1600)
    "drawable-xxxhdpi" = @(1280, 1920)
}
foreach ($folder in $splashPort.Keys) {
    $w = $splashPort[$folder][0]
    $h = $splashPort[$folder][1]
    $out = Join-Path (Join-Path $resRoot $folder) "splash.png"
    Save-ScaledPng $splash $w $h $out $white 0.45
}

# Default drawable for Android 12+ splash theme
$defaultSplash = Join-Path $resRoot "drawable\splash.png"
Save-ScaledPng $splash 480 800 $defaultSplash $white 0.45

$icon.Dispose()
$splash.Dispose()
Write-Host "Android assets generated under $resRoot"
