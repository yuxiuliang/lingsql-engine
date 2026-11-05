<#
.SYNOPSIS
    仓库卫生检查：推送前的最后一道自检（提交格式、垃圾文件、大文件、凭据字面量、忽略规则）。

.DESCRIPTION
    2026-10-31 手工做过一轮，结论是干净的；手工检查的问题在于只做一次。
    本脚本把它固化，任何一次推送前都跑一遍。

    检查项：
    ① 提交信息符合 `type(scope): 说明`（合并提交豁免）；
    ② 没有被跟踪的垃圾文件（日志、临时文件、jar、zip、node_modules、target、.local-private）；
    ③ 没有被跟踪的超大文件（默认 >500KB 告警）；
    ④ 源码里没有固化的口令/密钥字面量（`openssl rand` 生成、`NO_AUTH` 占位符豁免）；
    ⑤ `.gitignore` 覆盖关键目录（否则下一个人的机器上会误提交）。

.PARAMETER BaseRef
    比较基线，默认 `master`。

.PARAMETER LargeFileKb
    大文件阈值（KB），默认 500。

.EXAMPLE
    pwsh -File scripts/demo/repo-hygiene-check.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseRef = 'master',
    [int]$LargeFileKb = 500
)

$ErrorActionPreference = 'Stop'
$failures = 0

function Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) { Write-Host ("[PASS] {0,-24} {1}" -f $Name, $Detail) -ForegroundColor Green }
    else { $script:failures++; Write-Host ("[FAIL] {0,-24} {1}" -f $Name, $Detail) -ForegroundColor Red }
}
function Warn {
    param([string]$Name, [string]$Detail)
    Write-Host ("[WARN] {0,-24} {1}" -f $Name, $Detail) -ForegroundColor Yellow
}

Write-Host ''
Write-Host "仓库卫生检查（基线 $BaseRef）" -ForegroundColor Cyan
Write-Host ''

# ① 提交信息格式（合并提交豁免）
$commits = @(git log "$BaseRef..HEAD" --pretty=format:'%h %s' 2>$null)
$badCommits = @($commits | Where-Object { $_ -notmatch '^[0-9a-f]{7,} (feat|fix|test|docs|chore|refactor|perf|style|build|ci)(\([a-z0-9\-]+\))?: .+' -and $_ -notmatch 'Merge ' })
Check '提交信息格式' ($badCommits.Count -eq 0) ("共 $($commits.Count) 个提交，不合规 $($badCommits.Count)")
if ($badCommits.Count -gt 0) { $badCommits | Select-Object -First 5 | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray } }

# ② 垃圾文件
$tracked = @(git ls-files)
$junk = @($tracked | Where-Object { $_ -match '(\.log$|\.tmp$|\.bak|\.zip$|\.jar$|node_modules/|^target/|\.local-private/)' })
Check '无垃圾文件入库' ($junk.Count -eq 0) ("命中 $($junk.Count)")
if ($junk.Count -gt 0) { $junk | Select-Object -First 5 | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray } }

# ③ 大文件
$large = @()
foreach ($f in $tracked) {
    if (Test-Path -LiteralPath $f) {
        $size = (Get-Item -LiteralPath $f).Length
        if ($size -gt ($LargeFileKb * 1024)) { $large += [pscustomobject]@{ Path = $f; KB = [math]::Round($size / 1KB, 1) } }
    }
}
Check '无超大文件入库' ($large.Count -eq 0) ("超过 ${LargeFileKb}KB 的 $($large.Count) 个")
if ($large.Count -gt 0) { $large | Select-Object -First 5 | ForEach-Object { Write-Host "        $($_.Path)  $($_.KB) KB" -ForegroundColor DarkGray } }

# ④ 凭据字面量（豁免运行时随机与本地桩占位符）
$textFiles = @($tracked | Where-Object { $_ -match '\.(java|yml|yaml|properties|ps1|md|json|ts|vue|sql)$' })
$secretHits = @()
foreach ($f in $textFiles) {
    if (-not (Test-Path -LiteralPath $f)) { continue }
    $lines = Select-String -Path $f -Pattern '(password|secret|api[-_]?key)\s*[=:]\s*["''][^"'']{3,}' -ErrorAction SilentlyContinue
    foreach ($h in $lines) {
        $text = $h.Line
        if ($text -match 'openssl rand' -or $text -match 'NO_AUTH') { continue }
        $secretHits += "$([System.IO.Path]::GetFileName($h.Path)):$($h.LineNumber)"
    }
}
Check '无固化口令字面量' ($secretHits.Count -eq 0) ("命中 $($secretHits.Count)（已豁免 openssl rand / NO_AUTH）")
if ($secretHits.Count -gt 0) { $secretHits | Select-Object -First 5 | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray } }

# ⑤ .gitignore 覆盖
$ignore = if (Test-Path '.gitignore') { Get-Content '.gitignore' -Raw } else { '' }
$needed = @('.local-private', 'target', 'node_modules')
$missing = @($needed | Where-Object { $ignore -notmatch [regex]::Escape($_) })
Check '.gitignore 覆盖关键目录' ($missing.Count -eq 0) $(if ($missing.Count -gt 0) { "缺少：$($missing -join '、')" } else { '已覆盖' })

Write-Host ''
if ($failures -eq 0) {
    Write-Host '仓库卫生通过。' -ForegroundColor Green
    exit 0
}
Write-Host "$failures 项不通过 —— 推送前请先修。" -ForegroundColor Red
exit 1
