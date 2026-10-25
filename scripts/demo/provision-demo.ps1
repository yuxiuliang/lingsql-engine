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
    [string]$ModelApiKey = 'NO_AUTH'
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

Write-Host '[1/6] 管理端登录'
$login = Invoke-Api -Method POST -Path '/api/v1/auth/login' -TenantHeader '' -Body @{
    tenantId = $adminTenantId
    username = $adminUsername
    password = $secrets.app.adminPassword
}
$controlToken = $login.data.token
if (-not $controlToken) { throw '管理员登录未返回 Token' }
Write-Host '      OK'

Write-Host '[2/6] 读取租户列表'
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
            isolationMode    = 'PHYSICAL'
            logicColumn      = 'tenant_id'
            whiteListTables  = 'demo_customer,demo_product,demo_sales_order,demo_sales_order_item,demo_inventory_snapshot'
            whiteListColumns = 'customer_id,customer_name,customer_level,region_name,product_id,product_name,category_name,unit_price,unit_cost,order_id,order_no,order_date,order_status,sales_channel,item_id,quantity,sale_price,snapshot_date,warehouse_name,available_quantity,safety_quantity'
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

    Write-Host '[3/6] 演示管理员首次登录并强制改密'
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

Write-Host '[4/6] 收敛数据源配置并同步 Schema'
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
    isolationMode    = 'PHYSICAL'
    logicColumn      = 'tenant_id'
    whiteListTables  = 'demo_customer,demo_product,demo_sales_order,demo_sales_order_item,demo_inventory_snapshot'
    whiteListColumns = 'customer_id,customer_name,customer_level,region_name,product_id,product_name,category_name,unit_price,unit_cost,order_id,order_no,order_date,order_status,sales_channel,item_id,quantity,sale_price,snapshot_date,warehouse_name,available_quantity,safety_quantity'
    sensitiveColumns = ''
    status           = 1
}
Invoke-Api -Method PUT -Path "/api/v1/admin/config/tenant/$configId" -Token $controlToken -Body $updateBody | Out-Null
$probe = Invoke-Api -Method POST -Path '/api/v1/admin/config/tenant/test-connection' -Token $controlToken -Body $updateBody
Write-Host "      连接验证 OK: $($probe.data)"
Invoke-Api -Method POST -Path "/api/v1/admin/sync-schema/$TenantId" -Token $controlToken -Body @{} | Out-Null
Write-Host '      Schema 同步 OK'

Write-Host '[5/6] 准备并验证模型'
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

Write-Host '[6/6] 授权演示租户使用该模型'
Invoke-Api -Method PUT -Path "/api/v1/admin/tenant-model-routes/$TenantId" -Token $controlToken -Body @(
    @{ modelConfigId = $modelId; priority = 1; weight = 100 }
) | Out-Null
Write-Host '      OK'

Write-Host ''
Write-Host '开户完成。演示租户登录信息（口令在 .local-private/demo/demo-secrets.json）：'
Write-Host "  租户: $TenantId"
Write-Host "  账号: $DemoAdminUsername"
Write-Host "  模型: $ModelProvider / $ModelName (id=$modelId)"
