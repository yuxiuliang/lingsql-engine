<#
.SYNOPSIS
    只重启应用（不动 MySQL / Redis / 模型桩），并确保脚本会退出。

.DESCRIPTION
    为什么需要这个脚本：`start-demo-stack.ps1` 打印完成后**不会退出**（它要持有整栈的子进程），
    因此不能写进「停止 → 启动 → 等待 → 验证」这类自动化里——第二次调用会永远挂住，
    取消后台作业时还会连带把应用杀掉（2026-10-31 实测两次）。

    本脚本只做一件事：把 8000 端口上的应用停掉，用与整栈脚本一致的参数重新拉起，
    等到 readiness=200 后返回（0 成功 / 1 失败）。
    MySQL、Redis、模型桩保持不动——它们本来就不需要重启。

    **重要（2026-10-31 实测）**：脚本自己会正常结束，但**调用它的命令行工具不会返回**——
    因为工具会等待整棵进程树，而 java 应用是长驻子进程。所以：
    - 交互使用时，看到"应用就绪"即可按 Ctrl+C；
    - 自动化使用时，请把它放进后台作业，不要放在同步命令里（否则永远挂住，
      取消作业还会连带杀掉应用）。
    这也是 `start-demo-stack.ps1` "看起来不退出"的真实原因，不是脚本缺 exit。

.PARAMETER TimeoutSeconds
    等待 readiness 的上限，默认 180 秒。

.EXAMPLE
    pwsh -File scripts/demo/restart-demo-app.ps1
#>
[CmdletBinding()]
param(
    [int]$AppPort = 8000,
    [int]$MysqlPort = 3407,
    [int]$RedisPort = 6380,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/DemoStack.ps1')
. (Join-Path $PSScriptRoot 'lib/DemoSecrets.ps1')

$root = Get-DemoRoot
$jar = Join-Path $root 'target/lingsql-engine-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    Write-Host "找不到 jar：$jar（先构建）" -ForegroundColor Red
    exit 1
}

$secrets = Get-DemoSecrets

# ① 停掉当前占用 8000 的进程（只认端口，不猜进程名）
$existing = Get-NetTCPConnection -State Listen -LocalPort $AppPort -ErrorAction SilentlyContinue | Select-Object -First 1
if ($existing) {
    Write-Host "停止应用 pid=$($existing.OwningProcess)" -ForegroundColor Cyan
    Stop-Process -Id $existing.OwningProcess -Force -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 500
        if (-not (Get-NetTCPConnection -State Listen -LocalPort $AppPort -ErrorAction SilentlyContinue)) { break }
    }
}

# ② 依赖必须先就绪，否则应用起来也会连不上
foreach ($dep in @(@{ Port = $MysqlPort; Name = 'MySQL' }, @{ Port = $RedisPort; Name = 'Redis' })) {
    if (-not (Test-DemoPort -Port $dep.Port)) {
        Write-Host "$($dep.Name) 未监听 $($dep.Port) —— 请先跑 start-demo-stack.ps1" -ForegroundColor Red
        exit 1
    }
}

# ③ 与 start-demo-stack.ps1 保持一致的环境变量（改那边时记得同步这里）
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SERVER_PORT = "$AppPort"
$env:LINGSQL_DB_URL = "jdbc:mysql://127.0.0.1:$MysqlPort/lingsql_demo_control?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
$env:LINGSQL_DB_USERNAME = $secrets.mysql.appUser
$env:LINGSQL_DB_PASSWORD = $secrets.mysql.appPassword
$env:LINGSQL_REDIS_HOST = '127.0.0.1'
$env:LINGSQL_REDIS_PORT = "$RedisPort"
$env:LINGSQL_REDIS_PASSWORD = $secrets.redis.password
$env:LINGSQL_JWT_SECRET = $secrets.app.jwtSecret
$env:LINGSQL_RATE_LIMIT_LOGIN_MAX_REQUESTS = '300'
$env:LINGSQL_DATA_ENCRYPTION_KEY = $secrets.app.dataEncryptionKey
$env:LINGSQL_ADMIN_ENABLED = 'true'
$env:LINGSQL_ADMIN_TENANT_ID = 'sys_tenant'
$env:LINGSQL_ADMIN_TENANT_NAME = '系统租户'

Write-Host "启动应用（jar=$(Split-Path -Leaf $jar)）" -ForegroundColor Cyan
Start-DemoProcess -Name 'app' -FilePath 'java' -Arguments @('-jar', $jar) -WorkingDirectory $root | Out-Null

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$ok = $false
while ((Get-Date) -lt $deadline) {
    if (Wait-DemoHttp -Url "http://127.0.0.1:$AppPort/actuator/health/readiness" -TimeoutSeconds 5) { $ok = $true; break }
    Start-Sleep -Seconds 3
}

if ($ok) {
    Write-Host "应用就绪：http://127.0.0.1:$AppPort" -ForegroundColor Green
    exit 0
}

Write-Host "等待 $TimeoutSeconds 秒后仍未就绪，请看 .local-private/demo/logs/app.err.log" -ForegroundColor Red
exit 1
