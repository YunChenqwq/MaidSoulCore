$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root "out\classes"
if (Test-Path $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java sources found."
}
javac -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
Write-Host "Build OK -> $out"
