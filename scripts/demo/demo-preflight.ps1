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

# 2.5 构建对齐：应用必须是从磁盘上这个 jar 启动的。
# 2026-10-27 发现过一次「jar 从工作区构建、不对应任何提交，且进程比 jar 旧」的情况，
# 演示时无法回答「这是哪个版本」。这里用「jar 时间 ≤ 进程启动时间」判定，
# 并把提交号与 jar 短哈希一并打印，便于口头报版本。
try {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $AppPort -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listener) {
        Report-Check '构建对齐' $false "端口 $AppPort 无监听进程"
    } else {
        $proc = Get-Process -Id $listener.OwningProcess
        $jar = Get-Item (Join-Path $root 'target/lingsql-engine-1.0.0-SNAPSHOT.jar') -ErrorAction SilentlyContinue
        if (-not $jar) {
            Report-Check '构建对齐' $false '未找到 target 下的 jar'
        } else {
            $aligned = $jar.LastWriteTime -le $proc.StartTime
            $head = (& git -C $root rev-parse --short HEAD 2>$null)
            $jarHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.Substring(0, 12).ToLower()
            Report-Check '构建对齐' $aligned ("jar={0} 进程={1} HEAD={2} jarHash={3}" -f $jar.LastWriteTime.ToString('HH:mm:ss'), $proc.StartTime.ToString('HH:mm:ss'), "$head".Trim(), $jarHash)
        }
    }
}
catch {
    Report-Check '构建对齐' $false $_.Exception.Message
}

# 2.6 前端产物：index.html 能取到，且它引用的主 JS 也能取到。
# 打包时漏掉 static/admin 时后端一切正常，但界面 404——这类事故后端检查看不出来。
try {
    $idx = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/admin/index.html" -TimeoutSec 10 -SkipHttpErrorCheck
    if ($idx.StatusCode -ne 200) {
        Report-Check '前端产物' $false "admin/index.html HTTP $($idx.StatusCode)"
    } else {
        $idxBody = [System.Text.Encoding]::UTF8.GetString($idx.RawContentStream.ToArray())
        $asset = [regex]::Match($idxBody, '(?:src|href)="(\./assets/[^"]+\.js)"').Groups[1].Value
        if (-not $asset) {
            Report-Check '前端产物' $false 'index.html 里没有引用 assets/*.js'
        } else {
            $rel = $asset -replace '^\./', ''
            $a = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/admin/$rel" -TimeoutSec 20 -SkipHttpErrorCheck
            # 注意：本站是 SPA，未知路径会回退返回 index.html 且状态码仍是 200。
            # 所以只判状态码会「假通过」——必须校验内容不是 HTML。
            $aBody = [System.Text.Encoding]::UTF8.GetString($a.RawContentStream.ToArray())
            $isHtml = $aBody.TrimStart().StartsWith('<!DOCTYPE html') -or $aBody.TrimStart().StartsWith('<html')
            $ok = ($a.StatusCode -eq 200) -and (-not $isHtml) -and ($aBody.Length -gt 1000)
            Report-Check '前端产物' $ok ("{0}=HTTP {1} {2} 字节{3}" -f $rel, $a.StatusCode, $aBody.Length, $(if ($isHtml) { '（回退到 HTML，说明资源缺失）' } else { '' }))
        }
    }
}
catch {
    Report-Check '前端产物' $false $_.Exception.Message
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

        # 登录被限流时，后续所有需要令牌的检查都会以"错的原因"失败（例如菜单读到残缺权限，
        # 报成"菜单只有 1 项"）。这里先把病因说清楚，别再让人去查权限。
        if ($login.StatusCode -eq 429) {
            $retryAfter = $login.Headers['Retry-After']
            $wait = if ($retryAfter) { "最多还需 $retryAfter 秒（配置上限，实际可能更短）" } else { '约 10 分钟后自动解封' }
            Report-Check '演示账号登录' $false "被登录限流（HTTP 429），$wait；或删 Redis 键 lingsql:ratelimit:login:block:<ip> 立即解封"
            Write-Host ''
            Write-Host '  后续检查已跳过：现在的失败都是限流导致的假象，不是功能问题。' -ForegroundColor Yellow
            exit 1
        }

        Report-Check '演示账号登录' ([bool]$token) "code=$($loginJson.code)"

        if ($token) {
            # 模型熔断时提问会返回 code=200 但 rows=0——界面上看着像"没数据"，
            # 实问检查也只会报 rows=0，容易被当成数据或 SQL 问题（2026-10-27 实际踩过一次）。
            # 先看熔断状态，把病因说清楚。
            $fusedReason = $null
            try {
                $modelRows = & $mysqlClient --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
                    --database=lingsql_demo_control "--execute=SELECT is_active, IFNULL(fuse_reason,'-') FROM t_lingsql_engine_ai_model_config WHERE id=1;" 2>&1
                $modelLine = @($modelRows | Where-Object { "$_".Trim() -match '\S' })[0]
                if ($modelLine) {
                    $parts = "$modelLine".Split("`t")
                    if ($parts.Count -ge 1 -and "$($parts[0])".Trim() -eq '0') {
                        $fusedReason = if ($parts.Count -ge 2) { "$($parts[1])".Trim() } else { '未记录原因' }
                    }
                }
            }
            catch { }

            if ($fusedReason) {
                Report-Check '模型状态' $false "模型熔断中（is_active=0，原因=$fusedReason）——提问会返回 200 但 0 行，看起来像'查不到数据'。等自动恢复，或 POST /api/v1/admin/config/model/1/probe"
                Write-Host ''
                Write-Host '  已跳过实问检查：这时问什么都会返回空，不是数据问题。' -ForegroundColor Yellow
            }
            else {
            foreach ($question in @('2026年9月已完成订单的销售额是多少？', '哪些商品当前库存低于安全库存？')) {
                $body = @{ question = $question } | ConvertTo-Json -Compress
                $ask = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/api/v1/chat/ask" -Method POST -TimeoutSec 90 -SkipHttpErrorCheck `
                    -Headers @{ Authorization = "Bearer $token"; 'X-Tenant-Id' = 'demo_sales' } `
                    -ContentType 'application/json; charset=UTF-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
                $askJson = [System.Text.Encoding]::UTF8.GetString($ask.RawContentStream.ToArray()) | ConvertFrom-Json
                $rows = @($askJson.data.data).Count
                $label = if ($question.Length -gt 12) { $question.Substring(0, 12) + '…' } else { $question }
                # 失败时把服务端的话原样带出来。只报 code 会让人去猜原因——
                # 2026-10-31 就因为只看到 code=403，先后误判成"权限丢了""模型熔断"，
                # 实际是每日调用额度耗尽（message 里写得清清楚楚）。
                $detail = if ($askJson.code -eq 200) {
                    "code=$($askJson.code) rows=$rows"
                } else {
                    "code=$($askJson.code) rows=$rows message=$($askJson.message)"
                }
                Report-Check "实问：$label" (($askJson.code -eq 200) -and ($rows -gt 0)) $detail
            }
            }
        }
    }
    catch {
        Report-Check '问答链路' $false $_.Exception.Message
    }

    # 租户隔离不变量：共享表里同时有 iso_a 与 iso_b 的数据，以 iso_a 身份提问必须只看得到自己。
    # 这是最不能出错的一条，所以每次演示前都验。
    try {
        $isoLoginBody = @{ tenantId = 'iso_a'; username = 'iso_admin'; password = $secrets.app.demoAdminFinal } | ConvertTo-Json -Compress
        $isoLogin = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/api/v1/auth/login" -Method POST -TimeoutSec 30 -SkipHttpErrorCheck `
            -ContentType 'application/json; charset=UTF-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($isoLoginBody))
        $isoToken = ([System.Text.Encoding]::UTF8.GetString($isoLogin.RawContentStream.ToArray()) | ConvertFrom-Json).data.token
        if (-not $isoToken) {
            Report-Check '租户隔离（LOGICAL）' $false 'iso_a 登录失败'
        } else {
            $isoAskBody = @{ question = 'demo_shared_orders 表里各租户的订单金额分别是多少？' } | ConvertTo-Json -Compress
            $isoAsk = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/api/v1/chat/ask" -Method POST -TimeoutSec 90 -SkipHttpErrorCheck `
                -Headers @{ Authorization = "Bearer $isoToken"; 'X-Tenant-Id' = 'iso_a' } `
                -ContentType 'application/json; charset=UTF-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($isoAskBody))
            $isoJson = [System.Text.Encoding]::UTF8.GetString($isoAsk.RawContentStream.ToArray())
            $leaked = $isoJson -match 'iso_b'
            $saw = $isoJson -match 'iso_a'
            Report-Check '租户隔离（LOGICAL）' ((-not $leaked) -and $saw) $(if ($leaked) { '结果里出现 iso_b —— 隔离失效' } elseif ($saw) { '只看到 iso_a' } else { '结果里没有租户列' })
        }
    }
    catch {
        Report-Check '租户隔离（LOGICAL）' $false $_.Exception.Message
    }

    # 演示账号的菜单可见性：权限 = 角色权限 ∩ 租户权限池，两者缺一菜单就会被过滤掉。
    # 2026-10-27 实际只有 3 项（智能问答/问答历史/管理后台），仪表盘等都看不到。
    try {
        $menuResp = Invoke-WebRequest -Uri "http://127.0.0.1:$AppPort/api/v1/auth/menus" -TimeoutSec 30 -SkipHttpErrorCheck `
            -Headers @{ Authorization = "Bearer $token"; 'X-Tenant-Id' = 'demo_sales' }
        $menuJson = [System.Text.Encoding]::UTF8.GetString($menuResp.RawContentStream.ToArray()) | ConvertFrom-Json
        $nodes = 0
        $walk = {
            param($items)
            foreach ($it in @($items)) {
                $script:nodes++
                if ($it.children) { & $walk $it.children }
            }
        }
        $script:nodes = 0
        & $walk @($menuJson.data)
        Report-Check '演示账号菜单' ($script:nodes -ge 20) ("菜单节点=$($script:nodes)（应 ≥20；少了查 grant-demo-permissions.ps1）")
    }
    catch {
        Report-Check '演示账号菜单' $false $_.Exception.Message
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
