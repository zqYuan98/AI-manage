$ErrorActionPreference = 'Stop'

function Resolve-JavaHome {
    foreach ($configured in @($env:AILAB_JAVA_HOME, $env:JAVA_HOME)) {
        if ($configured -and (Test-Path -LiteralPath (Join-Path $configured 'bin\java.exe') -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $configured).Path
        }
    }
    if ($env:ProgramFiles) {
        $homes = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'JetBrains') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'jbr' }
        foreach ($javaCandidateHome in $homes) {
            if (Test-Path -LiteralPath (Join-Path $javaCandidateHome 'bin\java.exe') -PathType Leaf) {
                return $javaCandidateHome
            }
        }
    }
    throw 'A compatible JDK was not found. Set AILAB_JAVA_HOME.'
}

function Resolve-Maven {
    if ($env:AILAB_MAVEN_CMD -and (Test-Path -LiteralPath $env:AILAB_MAVEN_CMD -PathType Leaf)) {
        return (Resolve-Path -LiteralPath $env:AILAB_MAVEN_CMD).Path
    }
    foreach ($name in @('mvn.cmd', 'mvn')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }
    if ($env:MAVEN_HOME) {
        foreach ($relative in @('bin\mvn.cmd', 'bin\mvn')) {
            $candidate = Join-Path $env:MAVEN_HOME $relative
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return (Resolve-Path -LiteralPath $candidate).Path }
        }
    }
    if ($env:ProgramFiles) {
        $candidates = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'JetBrains') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd' }
        foreach ($candidate in $candidates) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    throw 'Maven was not found. Set AILAB_MAVEN_CMD or MAVEN_HOME.'
}

$env:JAVA_HOME = Resolve-JavaHome
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:Path
$maven = Resolve-Maven

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Maven=$maven"
& $maven @args
exit $LASTEXITCODE
