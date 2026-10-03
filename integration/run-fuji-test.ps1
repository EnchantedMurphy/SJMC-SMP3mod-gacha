param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$testRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build/run/fujiIntegration'))
if (-not $testRoot.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Test directory is outside the project' }
$jarName = 'fuji-fabric-14.12.0-38824a5371-mc26.3.jar'
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $jarName))) {
    throw "Download the official Fuji 14.12.0 for Minecraft 26.3 into integration/$jarName first."
}
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
Set-Content -LiteralPath (Join-Path $testRoot 'eula.txt') -Value 'eula=true' -Encoding utf8
@'
server-ip=127.0.0.1
server-port=0
online-mode=false
view-distance=2
simulation-distance=2
spawn-protection=0
pause-when-empty-seconds=0
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
'@ | Set-Content -LiteralPath (Join-Path $testRoot 'server.properties') -Encoding utf8
$fujiRoot = Join-Path $testRoot 'config/fuji'
New-Item -ItemType Directory -Path (Join-Path $fujiRoot 'modules/economy') -Force | Out-Null
@'
{"modules":{"economy":{"enable":true}}}
'@ | Set-Content -LiteralPath (Join-Path $fujiRoot 'config.json') -Encoding utf8
@'
{
  "provider_icon": "minecraft:paper",
  "balance_top_page_size": 10,
  "currencies": [
    {"currency_id":"fuji:coffee_stamp","currency_name":"Coffee Stamp","currency_icon_item":"minecraft:paper","default_face_balance":0,"format_value_string":"%.2f","format_value_text":"<yellow>%.2f"},
    {"currency_id":"fuji:tongbao","currency_name":"Tongbao","currency_icon_item":"minecraft:iron_ingot","default_face_balance":0,"format_value_string":"%.2f","format_value_text":"<yellow>%.2f"}
  ]
}
'@ | Set-Content -LiteralPath (Join-Path $fujiRoot 'modules/economy/config.json') -Encoding utf8
$resultPath = Join-Path $testRoot 'fuji-test-result.json'
if (Test-Path -LiteralPath $resultPath) { Remove-Item -LiteralPath $resultPath }
Push-Location $projectRoot
try {
    $arguments = @('runFujiIntegration', "-Pfuji_test_jar=integration/$jarName", '--console=plain')
    if ($Offline) { $arguments += '--offline' }
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw "Dedicated server exited with $LASTEXITCODE" }
    if (-not (Test-Path -LiteralPath $resultPath)) { throw 'Integration server did not write a result' }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if (-not $result.success) { throw $result.detail }
    Write-Output 'PASS: real Fuji accounts received 20 Coffee Stamps and 20 Tongbao; the S bundle also gave one mace.'
} finally { Pop-Location }
