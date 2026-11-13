# Run independent checks in separate Gradle invocations; preserve every exit code.
param([switch]$Online)
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$evidence = Join-Path $projectRoot 'docs/evidence/localisation'
New-Item -ItemType Directory -Force $evidence | Out-Null
$results = @()
$checks = @(
    @{ Name = 'clean'; Tasks = @('clean', ':app:clean') },
    @{ Name = 'assembleDebug'; Tasks = @(':app:assembleDebug') },
    @{ Name = 'lintDebug'; Tasks = @(':app:lintDebug') },
    @{ Name = 'testDebugUnitTest'; Tasks = @(':app:testDebugUnitTest') }
)
foreach ($check in $checks) {
    $arguments = @('--no-daemon')
    if (-not $Online) { $arguments += '--offline' }
    $arguments += $check.Tasks
    $log = Join-Path $evidence ($check.Name + '.log')
    $started = [DateTime]::UtcNow.ToString('o')
    & ./gradlew.bat @arguments *> $log
    $status = $LASTEXITCODE
    Set-Content (Join-Path $evidence ($check.Name + '.exit.txt')) "exit=$status"
    $results += @{ check = $check.Name; command = ('gradlew.bat ' + ($arguments -join ' ')); exit = $status; startedUtc = $started }
    $results | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $evidence 'gradle-results.json')
    Write-Output ($check.Name + ' exit=' + $status)
    Get-Content $log -Tail 8
}
if (@($results | Where-Object { $_.exit -ne 0 }).Count -gt 0) { exit 1 }
exit 0
