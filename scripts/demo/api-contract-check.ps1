<#
.SYNOPSIS
    前后端接口契约核对：前端调用的每个 /api/v1 路径，后端都必须有对应映射。

.DESCRIPTION
    起因：界面层的问题在接口测试里完全看不见（例如路由清单漂移导致刷新变 JSON）。
    这里换一个角度——静态比对「前端引用的接口路径」与「后端控制器映射」，
    找出前端在调、后端却没有的路径。不会发请求，无副作用。

    匹配规则偏宽松：后端 {param} 视为单段通配，{*path} 视为任意；前端因模板字符串
    被截断的路径按前缀匹配，宁可漏报，不制造假警报。

.EXAMPLE
    pwsh -File scripts/demo/api-contract-check.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# 1) 后端：类级前缀 + 方法级路径 → 完整路径
$backend = @()
$files = Get-ChildItem (Join-Path $root 'src/main/java') -Recurse -File -Filter '*.java'
foreach ($f in $files) {
    $lines = Get-Content $f.FullName
    $prefix = ''
    foreach ($line in $lines) {
        if ($line -match '@RequestMapping\("([^"]*)"\)') { $prefix = $Matches[1] }
        if ($line -match '@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?\{?\s*"([^"]*)"') {
            $sub = $Matches[2]
            $full = ("$prefix$sub") -replace '/+', '/'
            if (-not $full.StartsWith('/')) { $full = "/$full" }
            $backend += $full
        }
    }
}
$backend = $backend | Sort-Object -Unique

function ConvertTo-Regex {
    param([string]$Path)
    $escaped = [regex]::Escape($Path)
    $escaped = $escaped -replace '\\\{[^}]*\\\}', '[^/]+'
    return "^$escaped/?$"
}

$backendRegexes = $backend | ForEach-Object { ConvertTo-Regex $_ }

# 2) 前端：引用的接口路径
$frontend = Get-ChildItem (Join-Path $root 'web_src/src') -Recurse -File -Include '*.ts', '*.vue' |
    Select-String -Pattern "'(/api/v1/[^']*)'" -AllMatches |
    ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value } |
    ForEach-Object { ($_ -split '\?')[0] } |
    ForEach-Object { $_.TrimEnd('/') } |
    Sort-Object -Unique

Write-Host ''
Write-Host "后端映射 $($backend.Count) 条 / 前端引用 $($frontend.Count) 条" -ForegroundColor Cyan

$missing = @()
foreach ($p in $frontend) {
    # 模板字符串里带变量的路径会被截断，用前缀匹配兜住
    $hit = $backendRegexes | Where-Object { $p -match $_ }
    if (-not $hit) {
        $hit = $backend | Where-Object { $_.StartsWith($p + '/') -or $_.StartsWith("$p{") }
    }
    if (-not $hit) { $missing += $p }
}

Write-Host ''
if ($missing.Count -eq 0) {
    Write-Host "全部命中：前端引用的接口后端都有对应映射。" -ForegroundColor Green
    exit 0
}
Write-Host "后端没有对应映射的前端路径（$($missing.Count) 条）：" -ForegroundColor Red
$missing | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
Write-Host ''
Write-Host '注意：动态拼接的路径可能被误判，请人工确认后再改。' -ForegroundColor Yellow
exit 1
