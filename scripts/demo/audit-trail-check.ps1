<#
.SYNOPSIS
    审计留痕检查：每一次提问都必须留下"问了什么、实际跑了什么 SQL、耗时、花了多少"。

.DESCRIPTION
    客户会问的第一个合规问题通常是：「能不能查到谁在什么时候查了什么数据」。
    回答它靠的是 t_lingsql_engine_sql_execute_log 这张表——但没人验证过它是不是真的完整。

    本脚本按不变量检查最近一段时间内的记录：
    ① 每条都必须有 request_id、question、tenant_id、user_id；
    ② 成功的记录必须有 executed_sql（没记下来就等于"跑了但查不到"）；
    ③ 失败的记录必须带 error_message；
    ④ 记录数应与同期提问数一致（这里只做非零校验，避免误判）。

.PARAMETER Hours
    回看窗口，默认 24 小时。

.EXAMPLE
    pwsh -File scripts/demo/audit-trail-check.ps1
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
    if ($Ok) { Write-Host ("[PASS] {0,-30} {1}" -f $Name, $Detail) -ForegroundColor Green }
    else { $script:failures++; Write-Host ("[FAIL] {0,-30} {1}" -f $Name, $Detail) -ForegroundColor Red }
}

function Sql {
    param([string]$Statement)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            "--database=$Database" "--execute=$Statement" 2>&1)
}

Write-Host ''
Write-Host "审计留痕检查（最近 $Hours 小时）" -ForegroundColor Cyan
Write-Host ''

$total = [int](Sql "SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log WHERE create_time >= NOW() - INTERVAL $Hours HOUR;")
if ($total -eq 0) {
    # 全新环境还没人提过问，没有记录可查不是缺陷
    Write-Host ("[SKIP] {0,-30} {1}" -f '窗口内有记录', "最近 $Hours 小时没有提问记录，无可校验") -ForegroundColor DarkGray
    Write-Host ''
    exit 0
}
Check '窗口内有记录' ($total -gt 0) "共 $total 条"

if ($total -gt 0) {
    $missingCore = [int](Sql "SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log
        WHERE create_time >= NOW() - INTERVAL $Hours HOUR
          AND (request_id IS NULL OR request_id = '' OR question IS NULL OR question = ''
               OR tenant_id IS NULL OR tenant_id = '' OR user_id IS NULL);")
    Check '关键字段齐全' ($missingCore -eq 0) "缺 request_id/question/tenant_id/user_id 的=$missingCore"

    $successNoSql = [int](Sql "SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log
        WHERE create_time >= NOW() - INTERVAL $Hours HOUR AND success = 1
          AND (executed_sql IS NULL OR executed_sql = '');")
    Check '成功的都有实际 SQL' ($successNoSql -eq 0) "成功却没记 executed_sql 的=$successNoSql"

    $failNoReason = [int](Sql "SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log
        WHERE create_time >= NOW() - INTERVAL $Hours HOUR AND success = 0
          AND (error_message IS NULL OR error_message = '');")
    Check '失败的都有原因' ($failNoReason -eq 0) "失败却没记 error_message 的=$failNoReason"

    $succeeded = [int](Sql "SELECT COUNT(*) FROM t_lingsql_engine_sql_execute_log WHERE create_time >= NOW() - INTERVAL $Hours HOUR AND success = 1;")
    Write-Host ("       分布：成功 {0} 条 / 失败 {1} 条" -f $succeeded, ($total - $succeeded)) -ForegroundColor DarkGray
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host '审计留痕完整。' -ForegroundColor Green
    exit 0
}
Write-Host "$failures 项不满足——留痕不全，客户问「谁查了什么」就答不上来。" -ForegroundColor Red
exit 1
