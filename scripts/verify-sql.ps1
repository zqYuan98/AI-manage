$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $root 'sql/ailab.sql'
if (-not (Test-Path -LiteralPath $sqlFile)) { throw "Missing SQL file: $sqlFile" }
$sql = Get-Content -LiteralPath $sqlFile -Raw -Encoding UTF8

function Require([bool]$condition, [string]$message) { if (-not $condition) { throw $message } }
function Has([string]$needle) { return $sql.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase) -ge 0 }

$tables = @('lab_goal','lab_task','lab_task_evidence','lab_task_quality_gate','lab_task_block_event','lab_reminder','lab_asset','lab_member','lab_skill','lab_member_skill','lab_one2one','lab_ipr','lab_collaboration_record','lab_perf_score','lab_period_close','lab_report_template','lab_report_section','lab_report_summary','lab_report_instance','lab_report_job')
$matches = [regex]::Matches($sql, '(?is)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(lab_[a-z0-9_]+)`?\s*\((.*?)\)\s*(?:ENGINE|COMMENT)')
$found = @($matches | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() })
Require ($found.Count -eq 20 -and @($found | Sort-Object -Unique).Count -eq 20) 'Expected exactly 20 CREATE TABLE lab_* blocks.'
Require ((Compare-Object ($tables | Sort-Object) ($found | Sort-Object) -SyncWindow 0).Count -eq 0) 'Missing or extra lab_* table detected.'
foreach ($match in $matches) {
    $table = $match.Groups[1].Value
    $block = $match.Groups[2].Value
    foreach ($column in @('id','del_flag','create_by','create_time','update_by','update_time','remark')) {
        Require ([regex]::IsMatch($block, '(?is)(?:^|,)\s*`?' + [regex]::Escape($column) + '`?\s+[^,]+COMMENT\s+''')) "Missing audited/commented column $column in $table."
    }
    Require ([regex]::IsMatch($block, '(?is)(?:^|,)\s*`?id`?\s+bigint\s+NOT\s+NULL\s+AUTO_INCREMENT')) "Missing bigint auto increment id in $table."
    $statementEnd = $sql.IndexOf(';', $match.Index + $match.Length)
    Require ($statementEnd -gt 0 -and $sql.Substring($match.Index, $statementEnd - $match.Index) -match "(?is)COMMENT\s*=\s*'") "Missing table comment in $table."
}

$requiredDicts = @('lab_biz_line|hardware','lab_biz_line|platform','lab_biz_line|algorithm','lab_biz_line|manage','lab_task_workflow_status|DRAFT','lab_task_workflow_status|ACTIVE','lab_task_workflow_status|PENDING_REVIEW','lab_task_workflow_status|CONFIRMED','lab_task_result_status|DOING','lab_task_result_status|EXCEEDED','lab_task_result_status|ONTIME','lab_task_result_status|DELAYED','lab_task_result_status|UNDONE','lab_task_type|key','lab_task_type|daily','lab_task_level|month','lab_task_level|week','lab_asset_type|hardware','lab_asset_type|algorithm','lab_asset_type|platform','lab_asset_stage|VERIFYING','lab_asset_stage|DEPLOYED','lab_asset_stage|ACCEPTED','lab_ipr_type|SOFTWARE_COPYRIGHT','lab_ipr_type|PATENT','lab_ipr_type|CERTIFICATION','lab_ipr_stage|DRAFTING','lab_ipr_stage|SUBMITTED','lab_ipr_stage|ACCEPTED','lab_ipr_stage|AUTHORIZED','lab_section_type|TABLE','lab_section_type|STAT','lab_section_type|TEXT','lab_section_type|MANUAL','lab_section_type|GROUP_TEXT','lab_section_type|CHART','lab_goal_status|ACTIVE','lab_goal_status|COMPLETED','lab_goal_status|TERMINATED')
foreach ($item in $requiredDicts) { $parts = $item.Split('|'); Require (Has ("'" + $parts[0] + "'") -and Has ("'" + $parts[1] + "'")) "Missing dictionary value $item." }
$dictRows = [regex]::Matches($sql, "(?im)\(\s*\d+\s*,\s*\d+\s*,\s*'[^']*'\s*,\s*'([^']*)'\s*,\s*'(lab_[^']*)'")
$dictKeys = @{}; foreach ($row in $dictRows) { $key = $row.Groups[2].Value + '|' + $row.Groups[1].Value; Require (-not $dictKeys.ContainsKey($key)) "Duplicate dictionary value: $key"; $dictKeys[$key] = $true }

$permissions = @('lab:dashboard:view','lab:goal:list','lab:goal:add','lab:goal:edit','lab:goal:remove','lab:task:list','lab:task:add','lab:task:edit','lab:task:remove','lab:task:evidence','lab:task:review','lab:member:list','lab:member:add','lab:member:edit','lab:member:remove','lab:skill:list','lab:skill:config','lab:one2one:list','lab:one2one:add','lab:asset:list','lab:asset:add','lab:asset:edit','lab:asset:remove','lab:ipr:list','lab:ipr:add','lab:ipr:edit','lab:perf:list','lab:perf:close','lab:perf:reopen','lab:perf:redline','lab:perf:revoke','lab:perf:calibrate','lab:template:list','lab:template:config','lab:template:import','lab:template:export','lab:report:list','lab:report:generate','lab:report:retry','lab:report:download','lab:report:finalize','lab:report:sensitive')
foreach ($permission in $permissions) { Require (Has ("'" + $permission + "'")) "Missing menu permission $permission." }
foreach ($index in @('uk_lab_goal_year_no','uk_lab_member_user','uk_lab_reminder_idempotency','uk_lab_perf_member_period_rev','uk_lab_report_tpl_code_rev','uk_lab_report_summary','uk_lab_report_job_idempotency')) { Require (Has ("UNIQUE KEY ``$index``")) "Missing required unique key $index." }
foreach ($target in @('labScheduleTask.scanBlocks()','labScheduleTask.scanPendingTasks()','labScheduleTask.closeDuePeriods()','labScheduleTask.cleanReportTempFiles()','labScheduleTask.recoverReportJobs()')) { Require (Has $target) "Missing Quartz target $target." }
foreach ($type in @('TABLE','STAT','TEXT','MANUAL','GROUP_TEXT','CHART')) { Require (Has ("'" + $type + "'")) "Missing report renderer type $type." }
Require (Has "'standard_month'") 'Missing standard default month template.'
$memberCount = [regex]::Matches($sql, '(?im)^\s*INSERT\s+INTO\s+`lab_member`').Count
Require ($memberCount -eq 6) "Expected exactly six demo member inserts; found $memberCount."
Write-Output "AI Lab SQL contract verified (tables=$($found.Count); dicts=$($requiredDicts.Count); permissions=$($permissions.Count); jobs=5; members=$memberCount)"
