[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:1024',
    [string]$ApiUrl = 'http://127.0.0.1:8080',
    [string]$EvidenceDir = '.acceptance/lightweight-management',
    [string]$Period = (Get-Date -Format 'yyyy-MM'),
    [string]$ManagerUser = 'lab_manager',
    [string]$LeadUser = 'lab_algorithm',
    [string]$MemberUser = 'lab_researcher'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw "Acceptance assertion failed: $message" }
}

function Require-Secret([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Set $name in the process environment before running acceptance."
    }
    return $value
}

function Invoke-Login([string]$username, [string]$password) {
    $body = @{ username = $username; password = $password; code = ''; uuid = '' } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$ApiUrl/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
    Assert-True ($response.code -eq 200) "login failed for $username"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.token)) "login returned no token for $username"
    return [string]$response.token
}

function Invoke-AuthorizedGet([string]$path, [string]$token) {
    $headers = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$ApiUrl$path" -Headers $headers -Method Get -TimeoutSec 30
}

function Assert-Role([object]$info, [string]$role, [string]$username) {
    Assert-True ($info.code -eq 200) "getInfo failed for $username"
    Assert-True (@($info.roles) -contains $role) "$username does not have expected role $role"
}

function Assert-Workbench([string]$route, [string]$token) {
    $asOf = [Uri]::EscapeDataString((Get-Date).ToUniversalTime().ToString('o'))
    $response = Invoke-AuthorizedGet "/lab/workbench/$route`?period=$Period&asOf=$asOf" $token
    Assert-True ($response.code -eq 200) "$route workbench returned code $($response.code)"
    Assert-True ($null -ne $response.data) "$route workbench returned no data"
    return $response.data
}

function Get-Count([object]$value) {
    if ($null -eq $value) { return 0 }
    return @($value).Count
}

function Assert-PdfStructure([string]$path) {
    $bytes = [IO.File]::ReadAllBytes($path)
    Assert-True ($bytes.Length -gt 1024) 'LibreOffice PDF is too small'
    $text = [Text.Encoding]::ASCII.GetString($bytes)
    Assert-True ($text.StartsWith('%PDF-')) 'LibreOffice output has no PDF header'
    $match = [regex]::Match($text, 'startxref\s+(\d+)\s+%%EOF\s*$')
    Assert-True $match.Success 'LibreOffice PDF has no valid startxref/EOF trailer'
    $offset = [int64]$match.Groups[1].Value
    Assert-True ($offset -ge 0 -and $offset + 4 -le $bytes.Length) 'PDF xref offset is outside the file'
    Assert-True ($text.Substring([int]$offset, 4) -eq 'xref') 'PDF startxref does not point to xref'
}

function Invoke-RealLibreOffice([string]$targetDir) {
    & wsl.exe -d Ubuntu-24.04 --exec test -x /usr/bin/soffice
    Assert-True ($LASTEXITCODE -eq 0) '/usr/bin/soffice is unavailable in WSL'
    $sample = (Resolve-Path 'samples/2026-07-ai-lab-monthly-report.docx').Path
    $wslSample = (& wsl.exe -d Ubuntu-24.04 --exec wslpath -a $sample).Trim()
    Assert-True ($LASTEXITCODE -eq 0 -and $wslSample) 'cannot map the DOCX path into WSL'
    $runDir = "/tmp/ailab-acceptance-lo-$([Guid]::NewGuid().ToString('N'))"
    try {
        & wsl.exe -d Ubuntu-24.04 --exec mkdir -p $runDir
        Assert-True ($LASTEXITCODE -eq 0) 'cannot create the LibreOffice run directory'
        & wsl.exe -d Ubuntu-24.04 --exec /usr/bin/soffice --headless --convert-to pdf --outdir $runDir $wslSample | Out-Null
        Assert-True ($LASTEXITCODE -eq 0) 'LibreOffice conversion failed'
        $wslPdf = "$runDir/2026-07-ai-lab-monthly-report.pdf"
        $targetPdf = Join-Path $targetDir 'libreoffice-acceptance.pdf'
        $wslTarget = (& wsl.exe -d Ubuntu-24.04 --exec wslpath -a $targetPdf).Trim()
        & wsl.exe -d Ubuntu-24.04 --exec cp $wslPdf $wslTarget
        Assert-True ($LASTEXITCODE -eq 0 -and (Test-Path $targetPdf)) 'cannot copy the LibreOffice PDF evidence'
        Assert-PdfStructure $targetPdf
        return $targetPdf
    }
    finally {
        & wsl.exe -d Ubuntu-24.04 --exec rm -rf -- $runDir | Out-Null
    }
}

$managerPassword = Require-Secret 'AILAB_ACCEPTANCE_MANAGER_PASSWORD'
$leadPassword = Require-Secret 'AILAB_ACCEPTANCE_LEAD_PASSWORD'
$memberPassword = Require-Secret 'AILAB_ACCEPTANCE_MEMBER_PASSWORD'
$evidence = [IO.Path]::GetFullPath((Join-Path (Get-Location) $EvidenceDir))
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

$frontend = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/" -TimeoutSec 30
Assert-True ($frontend.StatusCode -eq 200) 'frontend application entry is unavailable'

$managerToken = Invoke-Login $ManagerUser $managerPassword
$leadToken = Invoke-Login $LeadUser $leadPassword
$memberToken = Invoke-Login $MemberUser $memberPassword

$managerInfo = Invoke-AuthorizedGet '/getInfo' $managerToken
$leadInfo = Invoke-AuthorizedGet '/getInfo' $leadToken
$memberInfo = Invoke-AuthorizedGet '/getInfo' $memberToken
Assert-Role $managerInfo 'lab_manager' $ManagerUser
Assert-Role $leadInfo 'lab_lead' $LeadUser
Assert-Role $memberInfo 'lab_member' $MemberUser

$manager = Assert-Workbench 'manager' $managerToken
$lead = Assert-Workbench 'lead' $leadToken
$member = Assert-Workbench 'member' $memberToken
Assert-True ($null -ne $manager.teamCommitments) 'manager workbench has no team commitment projection'
Assert-True ($null -ne $lead.teamCommitments) 'lead workbench has no scoped commitment projection'
Assert-True ($null -ne $member.weeklyCommitments) 'member workbench has no personal weekly commitments'
Assert-True ($null -ne $member.monthlyResults -and (Get-Count $member.monthlyResults) -ge 1) 'member acceptance account has no active owned monthly result'

$pdf = Invoke-RealLibreOffice $evidence
$commit = (& git rev-parse HEAD).Trim()
$frontendPid = @(Get-NetTCPConnection -State Listen -LocalPort ([Uri]$BaseUrl).Port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
$backendPid = @((& wsl.exe -d Ubuntu-24.04 --exec pgrep -f 'ruoyi-admin.jar') | Where-Object { $_ -match '^\d+$' })

$summary = [ordered]@{
    result = 'PASS'
    timestampUtc = (Get-Date).ToUniversalTime().ToString('o')
    commit = $commit
    baseUrl = $BaseUrl
    apiUrl = $ApiUrl
    period = $Period
    frontendPid = $frontendPid
    backendPid = $backendPid
    credentialsPersisted = $false
    realServices = [ordered]@{ mysqlReadAndLoginUpdate = $true; redisTokenRoundTrip = $true; libreOfficePdf = (Split-Path $pdf -Leaf) }
    roles = [ordered]@{
        manager = [ordered]@{ username = $ManagerUser; teamCommitments = (Get-Count $manager.teamCommitments); pendingAcceptance = (Get-Count $manager.pendingAcceptance) }
        lead = [ordered]@{ username = $LeadUser; teamCommitments = (Get-Count $lead.teamCommitments); newBlocks = (Get-Count $lead.newBlocks) }
        member = [ordered]@{ username = $MemberUser; monthlyResults = (Get-Count $member.monthlyResults); weeklyCommitments = (Get-Count $member.weeklyCommitments); dueItems = (Get-Count $member.dueItems) }
    }
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $evidence 'summary.json')
Write-Host "Acceptance PASS. Evidence: $EvidenceDir"
