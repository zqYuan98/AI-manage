param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$uiRoot = Join-Path $ProjectRoot 'ruoyi-ui\src\views\lab'
$files = Get-ChildItem -LiteralPath $uiRoot -Recurse -File -Include '*.vue','*.js'
$forbidden = @(
    'Goal trajectory',
    'Task composition',
    'Capacity ledger',
    'Personal inbox',
    'Fact-based feedback',
    'Strategy ledger',
    'Execution ledger',
    'Team directory',
    'Immutable archive',
    'Artifact pipeline'
)

$violations = foreach ($phrase in $forbidden) {
    $files | Select-String -SimpleMatch $phrase | ForEach-Object {
        "$($_.Path):$($_.LineNumber): forbidden visible phrase '$phrase'"
    }
}
if ($violations) {
    $violations | ForEach-Object { Write-Error $_ }
    throw 'Lab UI Chinese-language verification failed.'
}

$catalog = Join-Path $ProjectRoot 'ruoyi-ui\src\utils\lab-status.js'
if (-not (Test-Path -LiteralPath $catalog)) { throw 'Missing frontend business status catalog.' }
Write-Host "Lab UI Chinese-language verification passed ($($files.Count) source files)."
