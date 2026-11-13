$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$nativeRoot = Join-Path $projectRoot '.native-build/libbaresip-android'
$actual = git -C $nativeRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $actual -ne 'bf8448d8db4c3ff0ad3dabdd2d957954fed8ce22') {
    throw 'Native builder must be checked out at the project gitlink revision'
}
$archivePath = Join-Path $projectRoot '.native-build/native-builder.tar'
git -C $nativeRoot -c core.autocrlf=false archive --format=tar "--output=$archivePath" HEAD
if ($LASTEXITCODE -ne 0) { throw 'Native builder export failed' }
