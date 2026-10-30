<#
.SYNOPSIS
    计费与调用一致性检查：成功的提问必须付了两次模型钱，失败的至少付了一次。

.DESCRIPTION
    单次提问的正常路径是两次物理模型调用：SQL 架构师 + 业务分析师。
    如果某次提问**成功返回了答案**却只记了一次调用，说明业务分析师那次没进账——
    这是收入漏记；反过来，失败的提问如果记了两次，就是多收客户钱。

    本脚本按 requestId 把「问答审计表」和「逐物理调用账本」对起来：
    ① 成功的提问：ARCHITECT 与 ANALYST 两个阶段都必须有调用记录；
    ② 失败的提问：至少有一次调用（架构师跑过），不要求分析师；
    ③ 账本里不得存在非终态（RESERVED/DISPATCHED）的调用。

.PARAMETER Hours
    回看窗口，默认 24 小时。

.EXAMPLE
    pwsh -File scripts/demo/billing-call-consistency-check.ps1
#>
[CmdletBinding()]
param(
    [int]$Hours = 24,
    [int]$MysqlPort = 3407,
    [string]$Database = 'lingsql_demo_control'
)

$ErrorActionPreference = 'Stop'
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
$failures = 0

function Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) { Write-Host ("[PASS] {0,-32} {1}" -f $Name, $Detail) -ForegroundColor Green }
    else { $script:failures++; Write-Host ("[FAIL] {0,-32} {1}" -f $Name, $Detail) -ForegroundColor Red }
}

function Sql {
    param([string]$Statement)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            "--database=$Database" "--execute=$Statement" 2>&1)
}

Write-Host ''
Write-Host "计费与调用一致性（最近 $Hours 小时）" -ForegroundColor Cyan
Write-Host ''

# ① 成功的提问必须有 ANALYST 调用
$okNoAnalyst = Sql @"
SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log q
WHERE q.create_time >= NOW() - INTERVAL $Hours HOUR AND q.success = 1
  AND NOT EXISTS (
      SELECT 1 FROM t_lingsql_engine_billing_invocation b
      WHERE b.workflow_request_id = q.request_id AND b.stage = 'BUSINESS_ANALYST'
  );
"@
Check '成功的都付了分析师' ("$okNoAnalyst".Trim() -eq '0') "成功却没有 ANALYST 调用的=$($okNoAnalyst.ToString().Trim())"

# ② 成功的提问必须有 ARCHITECT 调用
$okNoArchitect = Sql @"
SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log q
WHERE q.create_time >= NOW() - INTERVAL $Hours HOUR AND q.success = 1
  AND NOT EXISTS (
      SELECT 1 FROM t_lingsql_engine_billing_invocation b
      WHERE b.workflow_request_id = q.request_id AND b.stage = 'SQL_ARCHITECT'
  );
"@
Check '成功的都付了架构师' ("$okNoArchitect".Trim() -eq '0') "成功却没有 ARCHITECT 调用的=$($okNoArchitect.ToString().Trim())"

# ③ 失败的提问也要有调用记录（跑过才失败）
$failNoCall = Sql @"
SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log q
WHERE q.create_time >= NOW() - INTERVAL $Hours HOUR AND q.success = 0
  AND NOT EXISTS (
      SELECT 1 FROM t_lingsql_engine_billing_invocation b
      WHERE b.workflow_request_id = q.request_id
  );
"@
Check '失败的也有调用记录' ("$failNoCall".Trim() -eq '0') "失败却没有任何调用记录的=$($failNoCall.ToString().Trim())"

# ④ 不允许残留非终态
$pending = Sql "SELECT COUNT(*) FROM t_lingsql_engine_billing_invocation WHERE state IN ('RESERVED','DISPATCHED');"
Check '无未终态调用' ("$pending".Trim() -eq '0') "非终态（RESERVED/DISPATCHED）=$($pending.ToString().Trim())"

Write-Host ''
if ($failures -eq 0) {
    Write-Host '计费与调用一致：干了几次活，收几次钱。' -ForegroundColor Green
    exit 0
}
Write-Host "$failures 项不一致 —— 要么漏收，要么多收，都必须在交付前查清。" -ForegroundColor Red
exit 1
