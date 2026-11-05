<#
.SYNOPSIS
    管理端路由可用性检查：前端声明的每条路由都必须返回界面（HTML），而不是 JSON。

.DESCRIPTION
    起因（2026-10-27）：从菜单点进页面正常，但刷新或直接打开链接时，
    /memory-admin、/plugin、/organization 等 10 条路由返回的是一段裸 JSON——
    因为路由清单在 SecurityConfig 与 AdminSpaController 里各有一份，且都落后于前端。

    本脚本以 **web_src/src/router/index.ts 为唯一事实来源**，不再维护第三份清单：
    解析出全部路由 → 逐条请求 → 断言状态码 200 且响应体是 HTML（含前端入口）。

    正确的结果是：一条不通都不该有。

.EXAMPLE
    pwsh -File scripts/demo/ui-routes-check.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8000',
    [string]$RouterFile = 'web_src/src/router/index.ts'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$router = Join-Path $root $RouterFile
if (-not (Test-Path $router)) {
    Write-Host "未找到路由定义：$RouterFile" -ForegroundColor Red
    exit 1
}

$allRoutes = Select-String -Path $router -Pattern "path:\s*'([^']+)'" -AllMatches |
    ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -notmatch 'pathMatch' } |
    Sort-Object -Unique

# 带参数的路由（如 /chat/:id）不能直接请求：字面量请求会被后端当成未知路径拦掉，
# 而服务端的 SPA 放行清单是**精确匹配**，本来也不支持这类路由。
# 直接跳过并明说，避免将来出现时变成一条永远红的假失败。
$paramRoutes = @($allRoutes | Where-Object { $_ -match ':' })
$routes = @($allRoutes | Where-Object { $_ -notmatch ':' })

Write-Host ''
Write-Host "从 $RouterFile 解析到 $($allRoutes.Count) 条路由（其中 $($paramRoutes.Count) 条带参数）" -ForegroundColor Cyan
if ($paramRoutes.Count -gt 0) {
    Write-Host ("  已跳过带参数路由：{0}" -f ($paramRoutes -join ', ')) -ForegroundColor Yellow
    Write-Host '  提示：后端 SPA 放行清单是精确匹配，带参数的路由目前不会被放行——新增这类页面需要同步扩展服务端策略。' -ForegroundColor Yellow
}
Write-Host ''

$failures = 0
foreach ($route in $routes) {
    try {
        $resp = Invoke-WebRequest -Uri "$($BaseUrl.TrimEnd('/'))$route" -TimeoutSec 15 -SkipHttpErrorCheck
        $body = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
        $isHtml = $body.TrimStart().StartsWith('<')
        $hasEntry = $body -match 'assets/index-'
        $ok = ($resp.StatusCode -eq 200) -and $isHtml -and $hasEntry
        if ($ok) {
            Write-Host ("[PASS] {0,-20} HTML {1} B" -f $route, $body.Length) -ForegroundColor Green
        } else {
            $failures++
            $kind = if ($isHtml) { 'HTML 但缺前端入口' } else { 'JSON/非 HTML' }
            Write-Host ("[FAIL] {0,-20} HTTP {1}  {2}" -f $route, $resp.StatusCode, $kind) -ForegroundColor Red
        }
    }
    catch {
        $failures++
        Write-Host ("[FAIL] {0,-20} {1}" -f $route, $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host "全部通过（$($routes.Count) 条路由都返回界面）。" -ForegroundColor Green
    exit 0
}
Write-Host "$failures / $($routes.Count) 条路由不可用 —— 刷新会看到 JSON 而不是界面。" -ForegroundColor Red
Write-Host '常见原因：SecurityConfig.ADMIN_SPA_ROUTES 或 AdminSpaController 的 @GetMapping 漏了该路由。' -ForegroundColor Yellow
exit 1
