# 一条命令拉起 Windows 本机演示栈：MySQL(3407) → Redis(6380) → 模型桩(18082) → 应用(8000) → 开户。
# 不依赖 Docker、不需要 WSL；只使用本机已有的 JDK、Maven、Node 与 MySQL 安装目录。
#
# 前置条件：
#   - JDK 25 与 Maven 3.9+（构建 jar 时用；已有 target/*.jar 可加 -SkipBuild）
#   - Node.js 18+（运行模型桩与演示数据生成器）
#   - 本机存在 MySQL 服务端程序（默认取 D:\Software\Tools\Database\mysql-8.4.2-winx64\bin）
#
# 首次运行会在 .local-private/demo 下生成：独立 MySQL 数据目录、随机口令、日志与 PID 文件。
# 该目录已被 .gitignore 排除，不进入仓库。

[CmdletBinding()]
param(
    [string]$MysqlHome = 'D:\Software\Tools\Database\mysql-8.4.2-winx64',
    [switch]$SkipBuild,
    [switch]$SkipDataInit,
    # 演示数据是相对当天生成的，跨天不刷新就会让「昨日日报」变成空结果。
    # 加此开关会在启动时执行 sql/demo_sales_inventory_refresh.sql，把数据对齐到当天。
    [switch]$RefreshDemoData,
    # 需要演示「经营日报」推送时传入；不传则告警通道保持关闭，数字员工能执行但不会投递。
    [string]$AlertWebhookUrl = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/DemoSecrets.ps1')
. (Join-Path $PSScriptRoot 'lib/DemoStack.ps1')

$root = Get-DemoRoot
$runtime = Get-DemoRuntimeDirectory
$secrets = Get-DemoSecrets
$mysqlServer = Join-Path $MysqlHome 'bin/mysqld.exe'
$mysqlClient = Join-Path $MysqlHome 'bin/mysql.exe'
$dataDirectory = Join-Path $runtime 'mysql-data'
$redisServer = Join-Path $runtime 'redis/redis-server.exe'
$stubScript = Join-Path $root 'scripts/demo/stub/ten-question-stub.mjs'
$jar = Join-Path $root 'target/lingsql-engine-1.0.0-SNAPSHOT.jar'

$mysqlPort = [int]$secrets.ports.mysql
$redisPort = [int]$secrets.ports.redis
$stubPort = [int]$secrets.ports.stub
$appPort = [int]$secrets.ports.app

Write-Host '== LingSQL 本机演示栈 =='

Write-Host "[1/6] MySQL :$mysqlPort"
if (Test-DemoPort -Port $mysqlPort) {
    Write-Host '      已在运行，跳过启动'
}
else {
    if (-not (Test-Path -LiteralPath $mysqlServer)) { throw "找不到 mysqld：$mysqlServer" }
    if (-not (Test-Path -LiteralPath (Join-Path $dataDirectory 'mysql'))) {
        Write-Host '      初始化独立数据目录（root 空口令，仅本机监听）'
        & $mysqlServer --no-defaults --initialize-insecure --basedir=$MysqlHome --datadir=$dataDirectory | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'MySQL 数据目录初始化失败' }
    }
    Start-DemoProcess -Name 'mysql' -FilePath $mysqlServer -Arguments @(
        '--no-defaults', "--basedir=$MysqlHome", "--datadir=$dataDirectory",
        "--port=$mysqlPort", '--bind-address=127.0.0.1', '--skip-mysqlx', '--console'
    ) | Out-Null
    if (-not (Wait-DemoPort -Port $mysqlPort -TimeoutSeconds 90)) { throw 'MySQL 未在 90 秒内就绪' }
    Write-Host '      已启动'
}

Write-Host "[2/6] 初始化演示库（控制库 + 演示业务库）"
if ($SkipDataInit) {
    Write-Host '      已按参数跳过'
}
else {
    & (Join-Path $PSScriptRoot 'init-demo-databases.ps1') -MysqlBin $mysqlClient -Port $mysqlPort | Out-Null
    Write-Host '      完成'
}

# 数据新鲜度检查：经营日报取「昨天」，数据必须覆盖到昨天，否则演示时客户问「昨天卖了多少」
# 会得到「昨天没有已完成订单」。这里的检查跑在启动阶段，避免到客户面前才发现。
$salesDb = 'lingsql_demo_sales'
$maxOrderDate = & $mysqlClient --host=127.0.0.1 "--port=$mysqlPort" --user=root --batch --skip-column-names --database=$salesDb --execute='SELECT MAX(order_date) FROM demo_sales_order;' 2>$null
$maxOrderDate = ($maxOrderDate | Select-Object -First 1)
if ($RefreshDemoData) {
    Write-Host '      按 -RefreshDemoData 刷新演示数据（对齐到当天）'
    $refreshSql = Join-Path $root 'sql/demo_sales_inventory_refresh.sql'
    & cmd /c "`"$mysqlClient`" --host=127.0.0.1 --port=$mysqlPort --user=root --default-character-set=utf8mb4 --database=$salesDb < `"$refreshSql`"" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '演示数据刷新失败' }
    $maxOrderDate = & $mysqlClient --host=127.0.0.1 "--port=$mysqlPort" --user=root --batch --skip-column-names --database=$salesDb --execute='SELECT MAX(order_date) FROM demo_sales_order;' 2>$null
    $maxOrderDate = ($maxOrderDate | Select-Object -First 1)
}
Write-Host "      演示数据最新日期：$maxOrderDate（今天 $(Get-Date -Format 'yyyy-MM-dd')）"
if ($maxOrderDate) {
    $latest = [datetime]::Parse($maxOrderDate)
    $yesterday = (Get-Date).Date.AddDays(-1)
    if ($latest -lt $yesterday) {
        Write-Host '      [警告] 昨天没有订单数据：演示「昨日经营快报」会得到空结果。' -ForegroundColor Yellow
        Write-Host '             修复：加 -RefreshDemoData 重新启动，或手工执行 sql/demo_sales_inventory_refresh.sql' -ForegroundColor Yellow
    }
}

Write-Host "[3/6] Redis :$redisPort"
if (Test-DemoPort -Port $redisPort) {
    Write-Host '      已在运行，跳过启动'
}
else {
    if (-not (Test-Path -LiteralPath $redisServer)) {
        throw "找不到 redis-server：$redisServer（首次运行请执行 scripts/demo/install-redis-windows.ps1）"
    }
    # 注意：Start-Process 的 -ArgumentList 会丢弃空字符串参数。若按 @('--save','',...) 传入，
    # Redis 实际只收到光秃秃的 --save，立即以
    # "Invalid argument during startup: Not enough parameters available for --save" 退出，
    # 导致脚本在 [3/6] throw、模型桩与应用根本不会启动。
    # 这里改用单条命令行字符串，用转义双引号表达 --save ""（禁用 RDB 持久化）。
    Start-DemoProcess -Name 'redis' -FilePath $redisServer -Arguments @(
        "--port $redisPort --bind 127.0.0.1 --requirepass $($secrets.redis.password) --save `"`" --appendonly no"
    ) | Out-Null
    if (-not (Wait-DemoPort -Port $redisPort -TimeoutSeconds 30)) { throw 'Redis 未在 30 秒内就绪' }
    Write-Host '      已启动'
}

Write-Host "[4/6] 模型桩 :$stubPort"
if (Test-DemoPort -Port $stubPort) {
    Write-Host '      已在运行，跳过启动'
}
else {
    $env:STUB_PORT = "$stubPort"
    $env:STUB_LOG = Join-Path $runtime 'ten-question-requests.jsonl'
    Start-DemoProcess -Name 'stub' -FilePath 'node' -Arguments @($stubScript) -WorkingDirectory $root | Out-Null
    if (-not (Wait-DemoPort -Port $stubPort -TimeoutSeconds 30)) { throw '模型桩未在 30 秒内就绪' }
    Write-Host '      已启动'
}

Write-Host "[5/6] 应用 :$appPort"
if (-not (Test-DemoPort -Port $appPort)) {
    if ((Test-Path -LiteralPath $jar) -and -not $SkipBuild) {
        Write-Host '      复用已有 jar（如需重建请删除 target 后重跑或去掉此提示）'
    }
    if (-not (Test-Path -LiteralPath $jar)) {
        if ($SkipBuild) { throw "找不到 jar：$jar" }
        Write-Host '      构建 jar（首次会下载依赖，耗时较长）'
        Push-Location $root
        try {
            & mvn -B '-DskipTests' '-Dcyclonedx.skip=true' package | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败' }
        }
        finally {
            Pop-Location
        }
    }
    $env:SPRING_PROFILES_ACTIVE = 'prod'
    $env:SERVER_PORT = "$appPort"
    $env:LINGSQL_DB_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/lingsql_demo_control?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    $env:LINGSQL_DB_USERNAME = $secrets.mysql.appUser
    $env:LINGSQL_DB_PASSWORD = $secrets.mysql.appPassword
    $env:LINGSQL_REDIS_HOST = '127.0.0.1'
    $env:LINGSQL_REDIS_PORT = "$redisPort"
    $env:LINGSQL_REDIS_PASSWORD = $secrets.redis.password
    $env:LINGSQL_JWT_SECRET = $secrets.app.jwtSecret
    $env:LINGSQL_DATA_ENCRYPTION_KEY = $secrets.app.dataEncryptionKey
    $env:LINGSQL_ADMIN_ENABLED = 'true'
    $env:LINGSQL_ADMIN_TENANT_ID = 'sys_tenant'
    $env:LINGSQL_ADMIN_TENANT_NAME = '系统租户'
    $env:LINGSQL_ADMIN_USERNAME = 'admin'
    $env:LINGSQL_ADMIN_PASSWORD = $secrets.app.adminPassword
    $env:LINGSQL_ADMIN_NICKNAME = '系统管理员'
    $env:LINGSQL_CORS_ENABLED = 'false'
    $env:LINGSQL_RETENTION_ENABLED = 'false'
    $env:LINGSQL_REFRESH_COOKIE_SECURE = 'false'
    $env:LINGSQL_RUNTIME_DATA_SNAPSHOT_ID = 'demo-sales-20261025'
    # 演示环境显式暴露健康明细：聚合健康若因审计/计费等待项为 DOWN，能直接看出是哪个组件。
    $env:MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS = 'always'
    # 告警通道只由 OpsAlertService 的全局配置决定，不从租户配置读。要演示经营日报推送，
    # 必须在启动应用前把 webhook 地址灌进环境变量，否则数字员工执行成功但 push_delivery_status 为空。
    if ($AlertWebhookUrl) {
        $env:LINGSQL_ALERT_ENABLED = 'true'
        $env:LINGSQL_ALERT_WEBHOOK_URL = $AlertWebhookUrl
        $env:LINGSQL_ALERT_REQUIRE_ALL_CHANNELS = 'false'
    }
    Start-DemoProcess -Name 'app' -FilePath 'java' -Arguments @('-jar', $jar) -WorkingDirectory $root | Out-Null
    if (-not (Wait-DemoHttp -Url "http://127.0.0.1:$appPort/actuator/health/readiness" -TimeoutSeconds 180)) {
        throw "应用未在 180 秒内就绪，请查看 $runtime/logs/app.out.log"
    }
    Write-Host '      已启动并通过健康检查'
}
else {
    Write-Host '      已在运行，跳过启动'
}

Write-Host '[6/6] 开户（租户 / 数据源 / 模型 / 授权）'
& (Join-Path $PSScriptRoot 'provision-demo.ps1') -BaseUrl "http://127.0.0.1:$appPort" -ModelBaseUrl "http://127.0.0.1:$stubPort" | Out-Null
Write-Host '      完成'

Write-Host ''
Write-Host '演示栈已就绪：'
Write-Host "  应用     http://127.0.0.1:$appPort"
Write-Host "  健康检查 http://127.0.0.1:$appPort/actuator/health"
Write-Host "  管理端   http://127.0.0.1:$appPort/admin"
Write-Host "  模型桩   http://127.0.0.1:$stubPort（确定性离线桩，非真实模型）"
Write-Host "  演示账号 demo_sales / demo_admin"
Write-Host "  口令     $runtime/demo-secrets.json"
Write-Host ''
Write-Host '跑 10 问：pwsh -File scripts/demo/ask-demo-questions.ps1'
Write-Host '停止全部：pwsh -File scripts/demo/stop-demo-stack.ps1'
