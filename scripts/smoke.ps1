$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot "build.ps1")
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.PlannerHookSmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.ExpressionSelectorSmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.ReplyEffectTrackerSmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.RuntimeLoopSmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.ProactiveRhythmSmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.MemoryV2SmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.MemoryStrategyMaintenanceSmokeTest
java -cp (Join-Path $root "out\classes") com.maidsoul.brain.SmokeTest
