$ErrorActionPreference = "Stop"

$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $dir
$tools = Join-Path $root ".tools"
$node = "C:\Users\TIANXVAN\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$env:NODE_PATH = "C:\Users\TIANXVAN\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"

$capture = (Get-ChildItem -LiteralPath $tools -Filter "capture-preview-video.js" | Select-Object -First 1).FullName
& $node $capture

$ffmpegDir = Join-Path (Join-Path (Join-Path $tools "pyvideo") "imageio_ffmpeg") "binaries"
$ffmpeg = (Get-ChildItem -LiteralPath $ffmpegDir -Filter "ffmpeg-win-x86_64-*.exe" | Select-Object -First 1).FullName
$frames = Join-Path (Join-Path $tools "preview-video-frames") "frame_%03d.jpg"
$out = (Get-ChildItem -LiteralPath $dir -Filter "*.mp4" | Select-Object -First 1).FullName

& $ffmpeg -y -framerate 10 -i $frames -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -movflags +faststart $out

Write-Host "Video exported: $out"
