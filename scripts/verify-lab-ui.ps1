param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [ValidateSet('Language','Contract')]
    [string]$Mode = 'Language'
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

if ($Mode -eq 'Contract') {
    $dashboard = Get-Content -LiteralPath (Join-Path $uiRoot 'dashboard\index.vue') -Raw -Encoding UTF8
    $api = Get-Content -LiteralPath (Join-Path $ProjectRoot 'ruoyi-ui\src\api\lab\dashboard.js') -Raw -Encoding UTF8
    $taskDrawer = Get-Content -LiteralPath (Join-Path $uiRoot 'task\components\TaskFormDrawer.vue') -Raw -Encoding UTF8
    $contracts = [ordered]@{
        'manager workbench API' = $api.Contains('getManagerWorkbench')
        'lead workbench API' = $api.Contains('getLeadWorkbench')
        'member workbench API' = $api.Contains('getMemberWorkbench')
        'server role routing' = $dashboard.Contains('workbenchRole') -and $dashboard.Contains('loadWorkbench')
        'stale response fencing' = $dashboard.Contains('workbenchRequest')
        'manager action section' = $dashboard.Contains('data-workbench-section="actions"')
        'manager goal section' = $dashboard.Contains('data-workbench-section="goals"')
        'manager commitment section' = $dashboard.Contains('data-workbench-section="commitments"')
        'manager meeting section' = $dashboard.Contains('data-workbench-section="meeting"')
        'member create commitment action' = $dashboard.Contains('data-member-action="create-weekly"')
        'member report block action' = $dashboard.Contains('data-member-action="report-block"')
        'member submit result action' = $dashboard.Contains('data-member-action="submit-result"')
        'workbench error retry' = $dashboard.Contains('@retry="loadWorkbench"')
        'keyboard Enter action' = $dashboard.Contains('@keydown.enter.prevent')
        'keyboard Space action' = $dashboard.Contains('@keydown.space.prevent')
        'weekly form hides monthly contract' = $taskDrawer.Contains('v-if="!isWeekly"') -and $taskDrawer.Contains('isWeekly()')
    }
    $failed = @($contracts.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object { $_.Key })
    if ($failed.Count -gt 0) {
        throw "Lab workbench behavior verification failed: $($failed -join ', ')"
    }
    Write-Host "Lab workbench behavior verification passed ($($contracts.Count) contracts)."
    exit 0
}

Write-Host "Lab UI Chinese-language verification passed ($($files.Count) source files)."
