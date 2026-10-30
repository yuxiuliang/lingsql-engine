<#
.SYNOPSIS
    一键总验：依次跑单元测试、演示前自检、文档一致性、黄金问题演练，输出总表。

.DESCRIPTION
    四个检查各自的职责：
      ① 单元测试        —— 代码有没有被改坏（较慢，默认跑，可用 -SkipTests 跳过）
      ② 演示前自检      —— 端口、就绪、数据新鲜度、语义字典、cron、实问两题
      ③ 文档一致性      —— 文档里的常量/清单/索引是否与代码和数据库一致
      ④ 黄金问题演练    —— 10 道题的实际产出是否与直接查库一致

    任一失败即整体非零退出。演示前跑这一条就够。

.PARAMETER SkipTests
    跳过 mvn 单测（仅在确认代码未改动时使用）。

.EXAMPLE
    pwsh -File scripts/demo/verify-all.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$steps = @()

function Invoke-Step {
    param([string]$Name, [string]$Command, [string]$WorkDir)
    Write-Host ''
    Write-Host "==== $Name ====" -ForegroundColor Cyan
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $output = & pwsh -NoProfile -Command $Command 2>&1
    $code = $LASTEXITCODE
    $sw.Stop()
    $output | Select-Object -Last 4 | ForEach-Object { Write-Host "  $_" }
    $script:steps += [pscustomobject]@{
        Name     = $Name
        ExitCode = $code
        Seconds  = [math]::Round($sw.Elapsed.TotalSeconds, 1)
    }
    if ($code -ne 0) {
        Write-Host "  → 失败（exit $code）" -ForegroundColor Red
    } else {
        Write-Host "  → 通过" -ForegroundColor Green
    }
}

Push-Location $root
try {
    if (-not $SkipTests) {
        Invoke-Step -Name '① 单元测试' -Command 'mvn -B -o test 2>&1 | Select-String -Pattern "Tests run:.*Failures|BUILD" | Select-Object -Last 2'
    } else {
        $steps += [pscustomobject]@{ Name = '① 单元测试'; ExitCode = -1; Seconds = 0 }
        Write-Host ''
        Write-Host '==== ① 单元测试 ====' -ForegroundColor DarkGray
        Write-Host '  （已跳过 -SkipTests）' -ForegroundColor DarkGray
    }
    Invoke-Step -Name '② 演示前自检' -Command 'pwsh -NoProfile -File scripts/demo/demo-preflight.ps1'
    Invoke-Step -Name '③ 文档一致性' -Command 'pwsh -NoProfile -File scripts/demo/docs-consistency-check.ps1'
    Invoke-Step -Name '④ 黄金问题演练' -Command 'pwsh -NoProfile -File scripts/demo/golden-question-rehearsal.ps1'
    Invoke-Step -Name '⑤ 界面路由可用' -Command 'pwsh -NoProfile -File scripts/demo/ui-routes-check.ps1'
    Invoke-Step -Name '⑥ 前后端契约' -Command 'pwsh -NoProfile -File scripts/demo/api-contract-check.ps1'
    Invoke-Step -Name '⑦ 审计留痕' -Command 'pwsh -NoProfile -File scripts/demo/audit-trail-check.ps1'
    Invoke-Step -Name '⑧ 计费与调用一致' -Command 'pwsh -NoProfile -File scripts/demo/billing-call-consistency-check.ps1'
}
finally {
    Pop-Location
}

Write-Host ''
Write-Host '======== 总表 ========' -ForegroundColor Cyan
foreach ($s in $steps) {
    $mark = if ($s.ExitCode -eq 0) { 'PASS' } elseif ($s.ExitCode -eq -1) { 'SKIP' } else { 'FAIL' }
    $color = switch ($mark) { 'PASS' { 'Green' } 'SKIP' { 'DarkGray' } default { 'Red' } }
    Write-Host ("[{0}] {1,-14} {2,6}s" -f $mark, $s.Name, $s.Seconds) -ForegroundColor $color
}

$failed = @($steps | Where-Object { $_.ExitCode -gt 0 })
Write-Host ''
if ($failed.Count -eq 0) {
    Write-Host '全部通过 —— 可以演示。' -ForegroundColor Green
    exit 0
}
Write-Host "$($failed.Count) 项未通过：$((($failed | ForEach-Object { $_.Name }) -join '、'))" -ForegroundColor Red
Write-Host '逐项排查见上方各节输出。' -ForegroundColor Yellow
exit 1
