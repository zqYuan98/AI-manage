[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Results = New-Object System.Collections.Generic.List[object]
$Failed = $false

function Resolve-NativeTool([string]$EnvironmentName, [string[]]$Commands, [string[]]$Candidates) {
    $configured = [Environment]::GetEnvironmentVariable($EnvironmentName)
    if ($configured -and (Test-Path -LiteralPath $configured -PathType Leaf)) { return (Resolve-Path -LiteralPath $configured).Path }
    foreach ($command in $Commands) {
        $resolved = Get-Command $command -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Source }
    }
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    throw "Required tool was not found. Set $EnvironmentName to its executable path."
}

function Configure-Java {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return }
    if ($env:AILAB_JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:AILAB_JAVA_HOME 'bin\java.exe'))) { $env:JAVA_HOME = $env:AILAB_JAVA_HOME; return }
    if ($env:ProgramFiles) {
        $homes = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'JetBrains') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'jbr' }
        foreach ($javaCandidateHome in $homes) { if (Test-Path -LiteralPath (Join-Path $javaCandidateHome 'bin\java.exe')) { $env:JAVA_HOME = $javaCandidateHome; return } }
    }
}

function Invoke-Native([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command exited with code $LASTEXITCODE" }
}

function Invoke-Stage([string]$Name, [scriptblock]$Action) {
    $started = Get-Date
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    try {
        & $Action
        $Results.Add([pscustomobject]@{ Stage=$Name; Result='PASS'; Seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,1) })
        Write-Host "PASS: $Name" -ForegroundColor Green
    } catch {
        $Results.Add([pscustomobject]@{ Stage=$Name; Result='FAIL'; Seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,1) })
        $script:Failed = $true
        Write-Host "FAIL: $Name - $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

Push-Location $ProjectRoot
try {
    Invoke-Stage 'Toolchain discovery' {
        Configure-Java
        $mavenCandidates = @()
        if ($env:MAVEN_HOME) { $mavenCandidates += (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'); $mavenCandidates += (Join-Path $env:MAVEN_HOME 'bin/mvn') }
        if ($env:ProgramFiles) {
            $mavenCandidates += Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'JetBrains') -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd' }
        }
        $script:Maven = Resolve-NativeTool 'AILAB_MAVEN_CMD' @('mvn.cmd','mvn') $mavenCandidates
        $script:Npm = Resolve-NativeTool 'AILAB_NPM_CMD' @('npm.cmd','npm') @('D:\nodejs\npm.cmd','C:\Program Files\nodejs\npm.cmd')
        $eslintName = if ([IO.Path]::DirectorySeparatorChar -eq '\') { 'eslint.cmd' } else { 'eslint' }
        $script:Eslint = Resolve-NativeTool 'AILAB_ESLINT_CMD' @() @((Join-Path $ProjectRoot "ruoyi-ui/node_modules/.bin/$eslintName"))
        Write-Host "Maven=$Maven`nNpm=$Npm`nEslint=$Eslint"
    }
    Invoke-Stage 'SQL contract' { & (Join-Path $PSScriptRoot 'verify-sql.ps1') }
    Invoke-Stage 'Mapper XML contracts' {
        $mappers = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'ruoyi-lab\src\main\resources\mapper\lab') -Filter '*Mapper.xml' -File
        if (-not $mappers) { throw 'No lab mapper XML files found' }
        foreach ($mapper in $mappers) {
            try { $null = [xml](Get-Content -LiteralPath $mapper.FullName -Raw -Encoding UTF8) }
            catch { throw "Invalid mapper XML: $($mapper.Name) - $($_.Exception.Message)" }
        }
        $reportMapper = Join-Path $ProjectRoot 'ruoyi-lab\src\main\resources\mapper\lab\LabReportMapper.xml'
        if ((Get-Content -LiteralPath $reportMapper -Raw -Encoding UTF8).Contains('${')) { throw 'Raw interpolation is forbidden in LabReportMapper.xml' }
        Write-Host "Parsed $($mappers.Count) lab mapper XML files"
    }
    Invoke-Stage 'Backend unit tests' { Invoke-Native $Maven @('-pl','ruoyi-lab','-am','clean','test') }
    Invoke-Stage 'Backend package' { Invoke-Native $Maven @('-pl','ruoyi-admin','-am','-DskipTests','clean','package') }
    Invoke-Stage 'Lab frontend lint and production build' {
        Push-Location (Join-Path $ProjectRoot 'ruoyi-ui')
        try {
            Invoke-Native $Eslint @('--no-ignore','--ext','.js,.vue','src/views/lab','src/api/lab')
            Invoke-Native $Npm @('run','build:prod')
        }
        finally { Pop-Location }
    }
    Invoke-Stage 'Production placeholder scan' {
        $roots = @('ruoyi-lab\src\main\java','ruoyi-ui\src\views\lab','ruoyi-ui\src\api\lab') | ForEach-Object { Join-Path $ProjectRoot $_ }
        $files = Get-ChildItem -LiteralPath $roots -Recurse -File | Where-Object { $_.Extension -in @('.java','.js','.vue') }
        $matches = $files | Select-String -Pattern '\bTODO\b|\bFIXME\b|UnsupportedOperationException|Not implemented|not implemented'
        if ($matches) { $matches | ForEach-Object { Write-Host "$($_.Path):$($_.LineNumber):$($_.Line)" }; throw 'Production placeholder markers found' }
    }
    Invoke-Stage 'Tracked artifact checks' {
        $base = Join-Path $ProjectRoot 'samples\2026-07-ai-lab-monthly-report'
        $json = [System.IO.File]::ReadAllBytes($base + '.json'); $null = ([Text.Encoding]::UTF8.GetString($json) | ConvertFrom-Json)
        $markdown = [Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($base + '.md'))
        $markdownMarkers = @(
            'Algorithm Lead',
            '92\.50',
            'July performance calibration completed',
            'Improve model quality'
        )
        $missingMarkdownMarkers = $markdownMarkers | Where-Object { -not $markdown.Contains($_) }
        if ($missingMarkdownMarkers) { throw "Markdown demo content is missing: $($missingMarkdownMarkers -join ', ')" }
        $word = [System.IO.File]::ReadAllBytes($base + '.docx'); if ($word.Length -lt 4 -or $word[0] -ne 0x50 -or $word[1] -ne 0x4b) { throw 'DOCX magic is invalid' }
        $pdf = [System.IO.File]::ReadAllBytes($base + '.pdf')
        $pdfText = [Text.Encoding]::ASCII.GetString($pdf)
        if ($pdf.Length -lt 1000 -or -not $pdfText.StartsWith('%PDF-')) { throw 'PDF magic is invalid' }
        $eof = $pdfText.LastIndexOf('%%EOF')
        $startXref = if ($eof -gt 0) { $pdfText.LastIndexOf('startxref', $eof) } else { -1 }
        if ($eof -lt 0 -or $startXref -lt 0 -or $pdfText.Substring($eof + 5).Trim().Length -ne 0) { throw 'PDF EOF/startxref is invalid' }
        $xrefTail = $pdfText.Substring($startXref)
        $xrefMatch = [regex]::Match($xrefTail, '^startxref\s+([0-9]+)\s+%%EOF\s*$')
        if (-not $xrefMatch.Success) { throw 'PDF startxref syntax is invalid' }
        [long]$xrefOffset = $xrefMatch.Groups[1].Value
        if ($xrefOffset -lt 5 -or $xrefOffset -ge $startXref -or -not $pdfText.Substring([int]$xrefOffset).StartsWith('xref')) { throw 'PDF xref offset is invalid' }
        $trailer = $pdfText.IndexOf('trailer', [int]$xrefOffset + 4)
        if ($trailer -lt 0 -or $trailer -ge $startXref) { throw 'PDF trailer is invalid' }
        Write-Host "Artifacts: JSON=$($json.Length) MD=$([Text.Encoding]::UTF8.GetByteCount($markdown)) DOCX=$($word.Length) PDF=$($pdf.Length) bytes"
    }
} catch {
    # Fail fast: no later stage runs, but the finally block still prints the completed stage summary.
} finally {
    Pop-Location
    Write-Host "`n=== Verification summary ===" -ForegroundColor Cyan
    $Results | Format-Table -AutoSize
    $office = Get-Command soffice,soffice.exe,libreoffice -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($office) { Write-Host "PDF capability: AVAILABLE ($($office.Source))" -ForegroundColor Green }
    else { Write-Host 'PDF capability: UNAVAILABLE (tracked PDF validated; live LibreOffice test will report skip)' -ForegroundColor Yellow }
}

if ($Failed) { exit 1 }
exit 0
