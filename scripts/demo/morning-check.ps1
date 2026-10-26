<#
.SYNOPSIS
    每日巡检：今天跑没跑、推没推出去、数字对不对。

.DESCRIPTION
    对应手册 §7「每天早上的三分钟巡检」。客户上线后每天早上跑一次，比事后解释便宜得多。
    只读，不改任何数据。

    输出三段：
      ① 今天的运行记录（条数、触发来源、成功与否、执行时间是否落在 08:00–08:15）
      ② 推送状态（1 成功 / 0 尝试失败 / NULL 根本没尝试）与失败明细
      ③ 数字抽查：把日报答案里的金额与直接查库的"昨日销售额"并排打印，人工比对

.PARAMETER TenantId
    租户，默认 demo_sales。

.EXAMPLE
    pwsh -File scripts/demo/morning-check.ps1
#>
[CmdletBinding()]
param(
    [string]$TenantId = 'demo_sales',
    [int]$MysqlPort = 3407
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
$issues = 0

function Q {
    param([string]$Db, [string]$Sql)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            --default-character-set=utf8mb4 --database=$Db "--execute=$Sql" 2>&1)
}

Write-Host ''
Write-Host "==== ① 今天的运行记录（租户 $TenantId） ====" -ForegroundColor Cyan
$runs = Q 'lingsql_demo_control' @"
SELECT id, employee_id, trigger_source, success, DATE_FORMAT(create_time,'%H:%i:%s'),
       result_rows, IFNULL(push_delivery_status,'NULL'), LEFT(IFNULL(answer,''),42)
FROM t_lingsql_engine_digital_employee_run_log
WHERE create_time >= CURDATE() ORDER BY id;
"@
$rows = @($runs | Where-Object { "$_".Trim() })
if ($rows.Count -eq 0) {
    Write-Host '  今天还没有任何运行记录 —— 检查员工是否被停用、cron 是否被改' -ForegroundColor Red
    $issues++
} else {
    foreach ($r in $rows) {
        $p = "$r".Trim() -split "`t"
        $mark = if ($p[3] -eq '1') { 'OK  ' } else { 'FAIL' }
        Write-Host ("  [{0}] id={1} 员工={2} 来源={3} 时间={4} 行数={5} 推送={6}" -f $mark, $p[0], $p[1], $p[2], $p[4], $p[5], $p[6])
        if ($p[3] -ne '1') { $issues++ }
        if ($p[4] -lt '08:00:00' -or $p[4] -gt '08:15:00') {
            Write-Host ("       ↳ 执行时间不在 08:00–08:15 区间（补跑或手工触发属正常）") -ForegroundColor Yellow
        }
        if ($p[2] -eq 'MANUAL') {
            Write-Host ("       ↳ 手工执行不会推送（实测：MANUAL 的 push_delivery_status 恒为 NULL）") -ForegroundColor DarkGray
        }
    }
}

Write-Host ''
Write-Host '==== ② 推送状态 ====' -ForegroundColor Cyan
$push = @($rows | ForEach-Object { ("$_".Trim() -split "`t")[6] } | Group-Object)
foreach ($g in $push) {
    $desc = switch ($g.Name) {
        '1' { '成功' }
        '0' { '尝试了但失败 —— 看 push_delivery_detail' }
        'NULL' { '根本没尝试 —— 通常是没有配置任何通道' }
        default { "未知取值 $($g.Name)" }
    }
    Write-Host ("  {0,-5} × {1}  {2}" -f $g.Name, $g.Count, $desc)
    if ($g.Name -eq '0') { $issues++ }
}
$detail = Q 'lingsql_demo_control' @"
SELECT LEFT(IFNULL(push_delivery_detail,''),120) FROM t_lingsql_engine_digital_employee_run_log
WHERE create_time >= CURDATE() AND push_delivery_status = 0 ORDER BY id DESC LIMIT 1;
"@
if ("$detail".Trim()) { Write-Host ("  最近失败明细：" + "$detail".Trim()) -ForegroundColor Yellow }

Write-Host ''
Write-Host '==== ③ 数字抽查（答案 vs 直接查库） ====' -ForegroundColor Cyan
$dbCfg = Q 'lingsql_demo_control' "SELECT IFNULL(schema_name,'') FROM t_lingsql_engine_tenant_db_config WHERE tenant_id='$TenantId' LIMIT 1;"
$answer = ($rows | Select-Object -Last 1)
if ($answer) {
    $ans = ("$answer".Trim() -split "`t")[7]
    Write-Host "  日报答案片段：$ans"
}
$dbName = "lingsql_$TenantId"
$dbExists = (Q 'information_schema' "SELECT COUNT(*) FROM schemata WHERE schema_name='$dbName';")
if ("$dbExists".Trim() -eq '1') {
    $ref = Q $dbName @"
SELECT CONCAT(COUNT(DISTINCT o.order_id),' 单 / ¥', FORMAT(SUM(i.quantity*i.sale_price),2))
FROM demo_sales_order o JOIN demo_sales_order_item i ON i.order_id=o.order_id
WHERE o.order_status='COMPLETED' AND o.order_date = CURDATE() - INTERVAL 1 DAY;
"@
    Write-Host "  直接查库（昨日）：$("$ref".Trim())"
    Write-Host "  → 与上面答案逐字比对；不一致先问口径，不要改 SQL 去凑" -ForegroundColor DarkGray
} else {
    Write-Host "  未找到库 $dbName，跳过数字抽查" -ForegroundColor Yellow
}

Write-Host ''
if ($issues -eq 0) {
    Write-Host '巡检通过：今天有运行、推送无失败。' -ForegroundColor Green
    exit 0
}
Write-Host "$issues 项需要关注（见上方红/黄提示）。" -ForegroundColor Yellow
exit 1
