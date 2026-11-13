param([string]$SdkPath = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath -and (Test-Path -LiteralPath 'local.properties')) {
    $line = Get-Content local.properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) { $SdkPath = $line.Substring(8).Replace('\:', ':').Replace('\\', '\') }
}
if (-not $SdkPath) { throw 'Set ANDROID_HOME or supply -SdkPath (do not commit local.properties)' }
$revisionMatch = [regex]::Match((Get-Content app/build.gradle.kts -Raw), 'ndkVersion = "([^"]+)"')
if (-not $revisionMatch.Success) { throw 'Cannot read pinned NDK revision' }
$tools = Join-Path $SdkPath ('ndk/' + $revisionMatch.Groups[1].Value + '/toolchains/llvm/prebuilt/windows-x86_64/bin')
$evidence = Join-Path $projectRoot 'docs/evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
$ErrorActionPreference = 'Continue'
python scripts/verify_native.py --tools $tools *> (Join-Path $evidence 'native-windows-verification.txt')
$nativeStatus = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($nativeStatus -ne 0) { throw 'Native archive verification failed; see evidence/native-windows-verification.txt' }
$gradleLog = Join-Path $evidence 'gradle-debug-build.log'
$ErrorActionPreference = 'Continue'
& ./gradlew.bat --no-daemon clean :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug *> $gradleLog
$gradleStatus = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Set-Content (Join-Path $evidence 'gradle-debug-build.exit.txt') "exit=$gradleStatus"
Get-Content $gradleLog -Tail 45
if ($gradleStatus -ne 0) { exit $gradleStatus }
$buildTools = Get-ChildItem (Join-Path $SdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK build-tools not installed' }
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$ErrorActionPreference = 'Continue'
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --aapt $aapt --tools $tools *> (Join-Path $evidence 'apk-verification.txt')
$apkStatus = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Set-Content (Join-Path $evidence 'apk-verification.exit.txt') "exit=$apkStatus"
Get-Content (Join-Path $evidence 'apk-verification.txt') -Tail 15
exit $apkStatus
