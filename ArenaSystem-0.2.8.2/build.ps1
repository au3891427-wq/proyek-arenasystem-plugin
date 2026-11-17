$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
Write-Host 'Building ArenaSystem 0.2.8.2 with Maven...'
& mvn clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }
$jar = Join-Path $PSScriptRoot 'target\ArenaSystem-0.2.8.2.jar'
if (-not (Test-Path $jar)) { throw "Build finished but JAR was not found: $jar" }
Write-Host "Build successful: $jar"
