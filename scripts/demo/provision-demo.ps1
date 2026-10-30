# 用管理端 API 完成演示开户：租户 → 只读数据源 → Schema 同步 → 模型探测/启用 → 租户模型授权。
# 与 scripts/ops/bootstrap_demo_pilot.sh 的受控顺序一致，只是把 bash/curl/jq 换成 PowerShell，便于 Windows 本机执行。

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8000',
    [string]$DataSourceJdbcUrl = 'jdbc:mysql://127.0.0.1:3407/lingsql_demo_sales?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',
    [string]$TenantId = 'demo_sales',
    [string]$TenantName = '销售库存演示租户',
    [string]$PlanName = '标准版',
    [string]$DemoAdminUsername = 'demo_admin',
    [string]$DemoAdminNickname = '演示管理员',
    [string]$ModelProvider = 'LOCAL_STUB',
    [string]$ModelName = 'lingsql-demo-stub',
    [string]$ModelVersion = '2026-10-25',
    [string]$ModelBaseUrl = 'http://127.0.0.1:18082',
    [string]$ModelApiKey = 'NO_AUTH',
    # 隔离模式与白名单：默认值对应演示库；指向其他库（如共享表做行级隔离验证）时必须一并替换，
    # 否则 verifyAllowlistReadable 会因白名单里的表在新库中不存在而拒绝开户。
    [string]$IsolationMode = 'PHYSICAL',
    [string]$WhiteListTables = 'demo_customer,demo_product,demo_sales_order,demo_sales_order_item,demo_inventory_snapshot',
    [string]$WhiteListColumns = 'customer_id,customer_name,customer_level,region_name,product_id,product_name,category_name,unit_price,unit_cost,order_id,order_no,order_date,order_status,sales_channel,item_id,quantity,sale_price,snapshot_date,warehouse_name,available_quantity,safety_quantity'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/DemoSecrets.ps1')

$secrets = Get-DemoSecrets
$adminTenantId = 'sys_tenant'
$adminUsername = 'admin'

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = '',
        [string]$TenantHeader = $adminTenantId,
        [switch]$AllowHttpError
    )
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token -ne '') { $headers['Authorization'] = "Bearer $Token" }
    if ($TenantHeader -ne '') { $headers['X-Tenant-Id'] = $TenantHeader }
    $parameters = @{
        Uri                = "$($BaseUrl.TrimEnd('/'))$Path"
        Method             = $Method
        Headers            = $headers
        TimeoutSec         = 90
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } elseif ($Body -is [array]) { $Body | ConvertTo-Json -Depth 8 -Compress -AsArray } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $parameters['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $parameters['ContentType'] = 'application/json; charset=UTF-8'
    }
    $response = Invoke-WebRequest @parameters
    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    $parsed = $null
    try { $parsed = $text | ConvertFrom-Json } catch { }
    if (-not $AllowHttpError) {
        $code = if ($parsed) { $parsed.code } else { $response.StatusCode }
        if ("$code" -ne '200') {
            throw "$Method $Path 失败：code=$code message=$(if ($parsed) { $parsed.message } else { $text })"
        }
    }
    return $parsed
}

Write-Host '[1/9] 管理端登录'
$login = Invoke-Api -Method POST -Path '/api/v1/auth/login' -TenantHeader '' -Body @{
    tenantId = $adminTenantId
    username = $adminUsername
    password = $secrets.app.adminPassword
}
$controlToken = $login.data.token
if (-not $controlToken) { throw '管理员登录未返回 Token' }
Write-Host '      OK'

Write-Host '[2/9] 读取租户列表'
$tenantList = Invoke-Api -Method GET -Path '/api/v1/admin/tenants' -Token $controlToken
$existing = @($tenantList.data | Where-Object { $_.tenantId -eq $TenantId })
$demoToken = $null
if ($existing.Count -eq 0) {
    Write-Host '      租户不存在，创建演示租户'
    $createBody = @{
        tenantId   = $TenantId
        tenantName = $TenantName
        planName   = $PlanName
        dataSource = @{
            jdbcUrl          = $DataSourceJdbcUrl
            username         = $secrets.mysql.readerUser
            password         = $secrets.mysql.readerPassword
            driverClass      = 'com.mysql.cj.jdbc.Driver'
            isolationMode    = $IsolationMode
            logicColumn      = 'tenant_id'
            whiteListTables  = $WhiteListTables
            whiteListColumns = $WhiteListColumns
            sensitiveColumns = ''
        }
        adminAccount = @{
            username = $DemoAdminUsername
            password = $secrets.app.demoAdminInitial
            nickname = $DemoAdminNickname
        }
    }
    $created = Invoke-Api -Method POST -Path '/api/v1/admin/tenants' -Token $controlToken -Body $createBody
    Write-Host "      OK 租户已创建: $TenantId"

    Write-Host '[3/9] 演示管理员首次登录并强制改密'
    $firstLogin = Invoke-Api -Method POST -Path '/api/v1/auth/login' -TenantHeader '' -Body @{
        tenantId = $TenantId
        username = $DemoAdminUsername
        password = $secrets.app.demoAdminInitial
    }
    if ($firstLogin.data.authFlow -ne 'PASSWORD_CHANGE_REQUIRED') {
        throw "新建演示管理员未进入强制改密流程: $($firstLogin.data.authFlow)"
    }
    $changed = Invoke-Api -Method PUT -Path '/api/v1/auth/password' -Token $firstLogin.data.token -TenantHeader $TenantId -Body @{
        currentPassword = $secrets.app.demoAdminInitial
        newPassword     = $secrets.app.demoAdminFinal
    }
    if ($changed.data.authFlow -ne 'AUTHENTICATED') { throw '改密后未取得完整会话' }
    $demoToken = $changed.data.token
    Write-Host '      OK'
}
else {
    Write-Host '      租户已存在，跳过创建'
    $loginDemo = Invoke-Api -Method POST -Path '/api/v1/auth/login' -TenantHeader '' -Body @{
        tenantId = $TenantId
        username = $DemoAdminUsername
        password = $secrets.app.demoAdminFinal
    }
    $demoToken = $loginDemo.data.token
}

Write-Host '[4/9] 收敛数据源配置并同步 Schema'
$configList = Invoke-Api -Method GET -Path '/api/v1/admin/config/tenant' -Token $controlToken
$configs = @($configList.data | Where-Object { $_.tenantId -eq $TenantId })
if ($configs.Count -ne 1) { throw "演示租户应恰有一个数据源配置，实际 $($configs.Count)" }
$configId = $configs[0].id
$updateBody = @{
    tenantId         = $TenantId
    jdbcUrl          = $DataSourceJdbcUrl
    username         = $secrets.mysql.readerUser
    password         = $secrets.mysql.readerPassword
    driverClass      = 'com.mysql.cj.jdbc.Driver'
    isolationMode    = $IsolationMode
    logicColumn      = 'tenant_id'
    whiteListTables  = $WhiteListTables
    whiteListColumns = $WhiteListColumns
    sensitiveColumns = ''
    status           = 1
}
Invoke-Api -Method PUT -Path "/api/v1/admin/config/tenant/$configId" -Token $controlToken -Body $updateBody | Out-Null
$probe = Invoke-Api -Method POST -Path '/api/v1/admin/config/tenant/test-connection' -Token $controlToken -Body $updateBody
Write-Host "      连接验证 OK: $($probe.data)"
Invoke-Api -Method POST -Path "/api/v1/admin/sync-schema/$TenantId" -Token $controlToken -Body @{} | Out-Null
Write-Host '      Schema 同步 OK'

Write-Host '[5/9] 准备并验证模型'
$modelList = Invoke-Api -Method GET -Path '/api/v1/admin/config/model' -Token $controlToken
$model = @($modelList.data | Where-Object { $_.providerName -eq $ModelProvider -and $_.modelName -eq $ModelName }) | Select-Object -First 1
$modelBody = @{
    providerName    = $ModelProvider
    modelName       = $ModelName
    modelVersion    = $ModelVersion
    baseUrl         = $ModelBaseUrl
    apiKey          = $(if ($ModelApiKey -eq 'NO_AUTH') { '' } else { $ModelApiKey })
    weight          = 100
    priority        = 1
    autoRecover     = 1
    cooldownSeconds = 300
}
if (-not $model) {
    $created = Invoke-Api -Method POST -Path '/api/v1/admin/config/model' -Token $controlToken -Body $modelBody
    $modelId = $created.data.id
}
else {
    $modelId = $model.id
    Invoke-Api -Method PUT -Path "/api/v1/admin/config/model/$modelId" -Token $controlToken -Body $modelBody | Out-Null
}
$probeResult = Invoke-Api -Method POST -Path "/api/v1/admin/model/$modelId/probe" -Token $controlToken -Body @{}
if ($probeResult.data.status -ne 'HEALTHY') { throw "模型探测未通过: $($probeResult.data.status)" }
Invoke-Api -Method POST -Path "/api/v1/admin/model/enable/$modelId" -Token $controlToken -Body @{} | Out-Null
Write-Host "      OK 模型 $modelId 已探测 HEALTHY 并启用"

Write-Host '[6/9] 授权演示租户使用该模型'
Invoke-Api -Method PUT -Path "/api/v1/admin/tenant-model-routes/$TenantId" -Token $controlToken -Body @(
    @{ modelConfigId = $modelId; priority = 1; weight = 100 }
) | Out-Null
Write-Host '      OK'

# 权限 = 角色权限 ∩ 租户权限池。两者默认只预置了少量条目，管理端菜单会大面积隐藏，
# 演示时看不到仪表盘/数字员工/语义字典等页面。这里为演示租户补齐（幂等）。
# 真实客户不应照抄，应按最小权限发放。
Write-Host '[7/9] 补齐演示租户权限（否则管理端菜单只有 3 项）'
& (Join-Path $PSScriptRoot 'grant-demo-permissions.ps1') -TenantId $TenantId | Out-Null
Write-Host '      OK'

# 演示环境放宽"每日调用次数"上限。
# 默认策略来自 BillingPolicy 内置的等级表（FREE 等级约 100 次/天），
# 计数口径是当日 sql_execute_log 行数——自检、演练、反复提问很快会把它耗光，
# 一旦耗尽，提问会返回 403「今日调用次数已达上限」，看起来像权限问题。
# 2026-10-31 实测：一天下来演示租户累计 500 条执行日志，把默认额度打满。
# 这里写入一个策略版本覆盖等级限额（幂等：按 version_code 判断）。
Write-Host '[8/9] 放宽演示环境的每日调用上限（默认约 100 次/天，演示不够用）'
$policyJson = '{"FREE":{"dailyCallLimit":5000,"dailyTokenLimit":0},"BASIC":{"dailyCallLimit":5000,"dailyTokenLimit":0},"PRO":{"dailyCallLimit":5000,"dailyTokenLimit":0}}'
$mysqlExe = $null
foreach ($candidate in @(
        (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '.local-private/demo/mysql/bin/mysql.exe'),
        'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
    )) {
    if (Test-Path $candidate) { $mysqlExe = $candidate; break }
}
if (-not $mysqlExe) {
    $found = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($found) { $mysqlExe = $found.Source }
}
$ctlArgs = @('--host=127.0.0.1', '--port=3407', "--user=$($secrets.mysql.appUser)", "--password=$($secrets.mysql.appPassword)", '--database=lingsql_demo_control')
$existsPolicy = & $mysqlExe @ctlArgs --batch --skip-column-names "--execute=SELECT COUNT(*) FROM t_lingsql_engine_billing_policy_version WHERE version_code='DEMO_DAY1';" 2>&1
if ("$existsPolicy".Trim() -eq '0') {
    & $mysqlExe @ctlArgs "--execute=INSERT INTO t_lingsql_engine_billing_policy_version (version_code, status, effective_time, model_unit_cost, service_level_limit, tenant_type_weight, default_unit_cost, default_daily_limit, default_daily_token_limit, default_tenant_weight, description, create_time, update_time) VALUES ('DEMO_DAY1', 1, NOW(), '{}', '$policyJson', '{}', 1, 5000, 0, 1, '演示环境放宽每日调用上限', NOW(), NOW());" 2>&1 | Out-Null
}
Write-Host '      OK'

# 演示夹具：3 个"并行验证"日报员工。
# 没有它们，preflight 的「日报 cron」检查会红、日报演示也跑不起来；
# 此前它们是手工创建的，全新环境因此缺失（2026-10-31 实测）。
# 夹具 SQL 幂等：按 (tenant_id, employee_name) 判断，已存在则跳过。
Write-Host '[9/9] 补齐演示夹具：3 个日报员工'
# 本脚本没有 $root 变量，用 PSScriptRoot 往上退两级拿仓库根
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixtureSql = Join-Path $repoRoot 'sql/demo_fixtures_daily_report.sql'
if (-not (Test-Path -LiteralPath $fixtureSql)) {
    Write-Host '      [警告] 未找到 sql/demo_fixtures_daily_report.sql，跳过' -ForegroundColor Yellow
}
else {
    # 用 cmd 重定向喂脚本：mysql 的 source 只在交互模式可靠
    & cmd /c "`"$mysqlExe`" --host=127.0.0.1 --port=3407 --user=$($secrets.mysql.appUser) --password=$($secrets.mysql.appPassword) --default-character-set=utf8mb4 --database=lingsql_demo_control < `"$fixtureSql`"" | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host '      [警告] 夹具 SQL 应用失败，请手工执行' -ForegroundColor Yellow }
    $fixtureCount = (& $mysqlExe @ctlArgs --batch --skip-column-names "--execute=SELECT COUNT(*) FROM t_lingsql_engine_digital_employee WHERE tenant_id='$TenantId' AND employee_name LIKE '并行验证%' AND is_deleted=0;" 2>$null | Select-Object -First 1)
    Write-Host "      日报员工数量：$fixtureCount（期望 >= 3）"
}

Write-Host ''
Write-Host '开户完成。演示租户登录信息（口令在 .local-private/demo/demo-secrets.json）：'
Write-Host "  租户: $TenantId"
Write-Host "  账号: $DemoAdminUsername"
Write-Host "  模型: $ModelProvider / $ModelName (id=$modelId)"
