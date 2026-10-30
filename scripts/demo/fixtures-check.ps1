<#
.SYNOPSIS
    演示夹具检查：确认四类夹具都在，缺哪一类直接说清楚。

.DESCRIPTION
    2026-10-31 在全新环境上从零跑，发现总验只有 6/9 通过——缺的不是代码，是"演示夹具"：
    数据新鲜度、日报员工、iso_a（逻辑隔离）、scale_a/b/c（规模夹具）。
    当时只能靠人肉发现，本轮把它做成检查，缺什么、去哪补，一行说清。

    检查项：
    ① 数据新鲜度：demo_sales 最新订单日期 >= 昨天；
    ② 日报员工：demo_sales 下 >= 3 个"并行验证%"员工且 cron 为 0 0 8 * * ?；
    ③ iso_a：控制面有该租户、数据源为 LOGICAL，且共享表两个租户都有数据；
    ④ scale 夹具：scale_a/b/c 三个租户存在，且 schema_table_count 分别为 60/100/200。

.PARAMETER MysqlPort
    MySQL 端口，默认 3407。
#>
[CmdletBinding()]
param([int]$MysqlPort = 3407)

$ErrorActionPreference = 'Stop'
$failures = 0

function Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) { Write-Host ("[PASS] {0,-30} {1}" -f $Name, $Detail) -ForegroundColor Green }
    else { $script:failures++; Write-Host ("[FAIL] {0,-30} {1}" -f $Name, $Detail) -ForegroundColor Red }
}
function Sql {
    param([string]$Db, [string]$Statement)
    return @(& 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe' --host=127.0.0.1 "--port=$MysqlPort" --user=root `
            --batch --skip-column-names --default-character-set=utf8mb4 "--database=$Db" "--execute=$Statement" 2>&1 |
            ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

Write-Host ''
Write-Host '演示夹具检查' -ForegroundColor Cyan
Write-Host ''

# ① 数据新鲜度
$maxDate = (Sql 'lingsql_demo_sales' 'SELECT MAX(order_date) FROM demo_sales_order;' | Select-Object -First 1)
$yesterday = (Get-Date).Date.AddDays(-1).ToString('yyyy-MM-dd')
Check '数据新鲜度' ($maxDate -and ([datetime]::Parse($maxDate) -ge [datetime]::Parse($yesterday))) "最新=$maxDate 需要>=$yesterday"

# ② 日报员工
$empCount = (Sql 'lingsql_demo_control' "SELECT COUNT(*) FROM t_lingsql_engine_digital_employee WHERE tenant_id='demo_sales' AND employee_name LIKE '并行验证%' AND schedule_cron='0 0 8 * * ?' AND is_deleted=0;" | Select-Object -First 1)
Check '日报员工' ([int]$empCount -ge 3) "并行验证员工=$empCount（需要>=3）"

# ③ iso_a：控制面 + 共享表
$isoMode = (Sql 'lingsql_demo_control' "SELECT IFNULL(isolation_mode,'(无)') FROM t_lingsql_engine_tenant_db_config WHERE tenant_id='iso_a';" | Select-Object -First 1)
Check 'iso_a 数据源（LOGICAL）' ("$isoMode" -eq 'LOGICAL') "isolation_mode=$isoMode"
$sharedTenants = (Sql 'lingsql_iso_test' 'SELECT COUNT(DISTINCT tenant_id) FROM demo_shared_orders;' | Select-Object -First 1)
Check 'iso_a 共享表数据' ([int]$sharedTenants -ge 2) "共享表内租户数=$sharedTenants（需要>=2，用于验证隔离）"

# ④ scale 夹具
foreach ($pair in @(@('scale_a', 60), @('scale_c', 100), @('scale_b', 200))) {
    $tenant = $pair[0]; $expect = $pair[1]
    $actual = (Sql 'lingsql_demo_control' "SELECT IFNULL(schema_table_count,-1) FROM t_lingsql_engine_tenant_db_config WHERE tenant_id='$tenant';" | Select-Object -First 1)
    if (-not $actual) { $actual = '(缺少该租户)' }
    Check "规模夹具 $tenant" ("$actual" -eq "$expect") "期望 $expect 张表，实际 $actual"
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host '夹具齐全。' -ForegroundColor Green
    exit 0
}
Write-Host "$failures 类夹具缺失。补齐步骤见手册 §10（iso_a）与第 6 节（规模夹具）；" -ForegroundColor Red
Write-Host '日报员工可直接应用 sql/demo_fixtures_daily_report.sql。' -ForegroundColor Yellow
exit 1
