$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot "build.ps1")
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.FiveTurnApiTest

