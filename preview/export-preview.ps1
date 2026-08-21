$ErrorActionPreference = "Stop"

$edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$html = (Get-ChildItem -LiteralPath $dir -Filter *.html | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$png = (Get-ChildItem -LiteralPath $dir -Filter *.png | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$url = "file:///" + ($html -replace "\\", "/")
$profile = Join-Path $env:TEMP "simultranslate-preview-edge"

if (Test-Path -LiteralPath $profile) {
    Remove-Item -LiteralPath $profile -Recurse -Force
}

& $edge `
    --headless=new `
    --disable-gpu `
    --disable-software-rasterizer `
    --disable-gpu-compositing `
    --no-sandbox `
    --disable-extensions `
    --hide-scrollbars `
    --force-device-scale-factor=1 `
    --user-data-dir=$profile `
    --window-size=1920,1080 `
    --screenshot=$png `
    $url

Write-Host "Preview exported: $png"
