$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root "out\classes"
if (Test-Path $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sourceRoot = Join-Path $root "src\main\java"
$forgeSourceRoot = Join-Path $sourceRoot "com\maidsoul\brain\forge"
$sources = Get-ChildItem -Path $sourceRoot -Recurse -Filter *.java |
    Where-Object { -not $_.FullName.StartsWith($forgeSourceRoot, [System.StringComparison]::OrdinalIgnoreCase) } |
    ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java sources found."
}
javac -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
Write-Host "Build OK -> $out"
