[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'invoke-maven.ps1'

if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw 'scripts/invoke-maven.ps1 must exist'
}

$output = & powershell -NoProfile -ExecutionPolicy Bypass -File $launcher -version 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "invoke-maven.ps1 -version exited with code $LASTEXITCODE`n$($output -join "`n")"
}

$text = $output -join "`n"
if (-not $text.Contains('Apache Maven')) {
    throw "Maven version output was not forwarded`n$text"
}
if (-not $text.Contains('Java version:')) {
    throw "Java version output was not forwarded`n$text"
}

Write-Host 'invoke-maven contract verified'
