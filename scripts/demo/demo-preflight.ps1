<#
.SYNOPSIS
    演示前 60 秒自检：一次性确认栈、数据、配置、问答链路是否可用。

.DESCRIPTION
    演示当天最容易翻车的几件事，都不是"程序报错"，而是"看起来正常但内容是空的"：
    数据跨天过期导致日报为空、语义字典没导入、cron 被人改回去、模型桩没起。
    这个脚本把这些问题变成一眼可见的 PASS / FAIL。

    只读：不修改数据库、不启停任何服务。

.PARAMETER AppPort
    应用端口，默认取 demo-secrets.json 中的配置。

.PARAMETER MysqlPort
    MySQL 端口，默认取 demo-secrets.json 中的配置。

.EXAMPLE
    pwsh -File scripts/demo/demo-preflight.ps1
#>
[CmdletBinding()]
param(
    [int]$AppPort = 0,
    [int]$MysqlPort = 0
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$secretsPath = Join-Path $root '.local-private/demo/demo-secrets.json'
$mysqlClient = $null
foreach ($candidate in @(
        (Join-Path $root '.local-private/demo/mysql/bin/mysql.exe'),
        'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
    )) {
    if (Test-Path $candidate) { $mysqlClient = $candidate; break }
}
if (-not $mysqlClient) {
    $found = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($found) { $mysqlClient = $found.Source }
}

$secrets = $null
if (Test-Path $secretsPath) {
    $secrets = Get-Content -LiteralPath $secretsPath -Raw | ConvertFrom-Json
}
if ($AppPort -le 0) { $AppPort = if ($secrets) { [int]$secrets.ports.app } else { 8000 } }
if ($MysqlPort -le 0) { $MysqlPort = if ($secrets) { [int]$secrets.ports.mysql } else { 3407 } }

$failures = 0
$checks = 0

function Report-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    $script:checks++
    if ($Ok) {
        Write-Host ("[PASS] {0,-28} {1}" -f $Name, $Detail) -ForegroundColor Green
    }
    else {
        $script:failures++
        Write-Host ("[FAIL] {0,-28} {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

function Test-PortListening {
    param([int]$Port)
    return (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0
}

function Invoke-Sql {
    param([string]$Database, [string]$Sql)
    $sqlArgs = @('--host=127.0.0.1', "--port=$MysqlPort", '--user=root', '--batch', '--skip-column-names',
        '--default-character-set=utf8mb4', "--database=$Database", "--execute=$Sql")
    return (& $mysqlClient @sqlArgs 2>&1 | Select-Object -First 1)
}

Write-Host ''
Write-Host '======== 演示前自检 ========' -ForegroundColor Cyan

# 1. 端口
Report-Check '应用端口' (Test-PortListening $AppPort) "port=$AppPort"
Report-Check 'MySQL 端口' (Test-PortListening $MysqlPort) "port=$MysqlPort"
$stubPort = if ($secrets) { [int]$secrets.ports.stub } else { 18082 }
$redisPort = if ($secrets) { [int]$secrets.ports.redis } else { 6380 }
Report-Check '模型桩端口' (Test-PortListening $stubPort) "port=$stubPort"
Report-Check 'Redis 端口' (Test-PortListening $redisPort) "port=$redisPort"

# 2. 应用就绪
$ready = $false
try {
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/actuator/health/readiness" -TimeoutSec 8 -SkipHttpErrorCheck
    $ready = ($response.StatusCode -eq 200)
    Report-Check '应用 readiness' $ready "http=$($response.StatusCode)"
}
catch {
    Report-Check '应用 readiness' $false $_.Exception.Message
}

# 3. 演示数据是否覆盖「昨天」
$yesterday = (Get-Date).Date.AddDays(-1).ToString('yyyy-MM-dd')
$maxOrderDate = Invoke-Sql -Database 'lingsql_demo_sales' -Sql 'SELECT MAX(order_date) FROM demo_sales_order;'
$coversYesterday = $false
if ($maxOrderDate -match '^\d{4}-\d{2}-\d{2}') {
    $coversYesterday = ([datetime]$maxOrderDate).Date -ge [datetime]$yesterday
}
Report-Check '数据覆盖昨天' $coversYesterday "MAX=$maxOrderDate 需要>=$yesterday"

# 4. 语义字典
$lexiconCount = Invoke-Sql -Database 'lingsql_demo_control' -Sql "SELECT COUNT(*) FROM t_lingsql_engine_semantic_lexicon WHERE tenant_id='demo_sales' AND status=1;"
$lexiconOk = ($lexiconCount -match '^\d+$') -and ([int]$lexiconCount -ge 10)
Report-Check '语义字典条数' $lexiconOk "count=$lexiconCount（建议 >=10）"

# 5. 三条日报 cron
$cronRows = & $mysqlClient --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names --database=lingsql_demo_control "--execute=SELECT schedule_cron FROM t_lingsql_engine_digital_employee WHERE tenant_id='demo_sales' AND is_deleted=0 AND employee_name LIKE '并行验证%';" 2>&1
$cronList = @($cronRows | Where-Object { $_ -match '\S' })
$cronOk = ($cronList.Count -ge 3) -and (@($cronList | Where-Object { $_.Trim() -ne '0 0 8 * * ?' }).Count -eq 0)
Report-Check '日报 cron' $cronOk ("共 {0} 条，值={1}" -f $cronList.Count, (($cronList | ForEach-Object { $_.Trim() }) -join '/'))

# 6. 实问两题
if ($ready -and $secrets) {
    try {
        $loginBody = @{ tenantId = 'demo_sales'; username = 'demo_admin'; password = $secrets.app.demoAdminFinal } | ConvertTo-Json -Compress
        $login = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/api/v1/auth/login" -Method POST -TimeoutSec 30 -SkipHttpErrorCheck `
            -ContentType 'application/json; charset=UTF-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($loginBody))
        $loginJson = [System.Text.Encoding]::UTF8.GetString($login.RawContentStream.ToArray()) | ConvertFrom-Json
        $token = $loginJson.data.token
        Report-Check '演示账号登录' ([bool]$token) "code=$($loginJson.code)"

        if ($token) {
            foreach ($question in @('2026年9月已完成订单的销售额是多少？', '哪些商品当前库存低于安全库存？')) {
                $body = @{ question = $question } | ConvertTo-Json -Compress
                $ask = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/api/v1/chat/ask" -Method POST -TimeoutSec 90 -SkipHttpErrorCheck `
                    -Headers @{ Authorization = "Bearer $token"; 'X-Tenant-Id' = 'demo_sales' } `
                    -ContentType 'application/json; charset=UTF-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
                $askJson = [System.Text.Encoding]::UTF8.GetString($ask.RawContentStream.ToArray()) | ConvertFrom-Json
                $rows = @($askJson.data.data).Count
                $label = if ($question.Length -gt 12) { $question.Substring(0, 12) + '…' } else { $question }
                Report-Check "实问：$label" (($askJson.code -eq 200) -and ($rows -gt 0)) "code=$($askJson.code) rows=$rows"
            }
        }
    }
    catch {
        Report-Check '问答链路' $false $_.Exception.Message
    }
}
elseif (-not $secrets) {
    Report-Check '演示账号登录' $false '未找到 demo-secrets.json'
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host "全部通过（$checks 项）—— 可以开始演示。" -ForegroundColor Green
    exit 0
}
Write-Host "有 $failures / $checks 项未通过 —— 先修再演示。" -ForegroundColor Red
Write-Host '常见修复：数据过期 -> 加 -RefreshDemoData 重启；语义字典缺失 -> 重跑 sql/demo_semantic_lexicon.sql。' -ForegroundColor Yellow
exit 1
