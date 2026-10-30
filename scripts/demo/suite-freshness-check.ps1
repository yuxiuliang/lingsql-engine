<#
.SYNOPSIS
    校验黄金问题 Suite 的期望值是否还对得上当前数据（量具是否过期）。

.DESCRIPTION
    起因（2026-10-31）：仓库里那套 40 题验收草稿
    `.local-private/commercial/evidence/internal-rehearsal/demo-sales-40-draft.json`
    的期望值是针对某一版演示数据算出来的。演示数据会随刷新变化，
    但 Suite 里的数字不会自己更新——**量具过期时，失败看起来像产品坏了**。

    本脚本不发送任何提问、不评判产品，只做一件事：
    用 Suite 自带的 truthSql 直接查库，和 Suite 里写的期望值比对。

    - SCALAR_NUMBER：比较 truthSql 首格数值与 expected.value（允许 absoluteTolerance）
    - ROWSET：比较 truthSql 返回行数与 expected.rows 条数

    判定为"过期"时**不要**拿这套 Suite 去做验收或复测，先重新生成期望值。

.EXAMPLE
    pwsh -File scripts/demo/suite-freshness-check.ps1
#>
[CmdletBinding()]
param(
    [string]$SuiteFile = '.local-private/commercial/evidence/internal-rehearsal/demo-sales-40-draft.json',
    # 按当前数据重算期望值并写回（改前自动备份）。只在**冻结数据集**上使用：
    # 数据集一变，期望值又会过期。
    [switch]$Refresh,
    [string]$Database = 'lingsql_demo_sales',
    [int]$MysqlPort = 3407
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$suitePath = if ([System.IO.Path]::IsPathRooted($SuiteFile)) { $SuiteFile } else { Join-Path $root $SuiteFile }
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'

if (-not (Test-Path -LiteralPath $suitePath)) {
    Write-Host "找不到 Suite：$suitePath" -ForegroundColor Red
    exit 1
}

function Invoke-Sql {
    param([string]$Statement)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            "--database=$Database" "--execute=$Statement" 2>&1)
}

$suite = Get-Content -LiteralPath $suitePath -Raw | ConvertFrom-Json
$cases = @($suite.businessCases)
Write-Host ''
Write-Host "Suite=$($suite.suiteId) 版本=$($suite.suiteVersion) 状态=$($suite.draftStatus)" -ForegroundColor Cyan
Write-Host "用例=$($cases.Count)  数据库=$Database" -ForegroundColor Cyan
Write-Host ''

if ($Refresh) {
    $backup = "$suitePath.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Copy-Item -LiteralPath $suitePath -Destination $backup
    Write-Host "已备份原 Suite：$([System.IO.Path]::GetFileName($backup))" -ForegroundColor Yellow
}

$stale = 0
$checked = 0
$refreshed = 0
$unanswerable = 0
foreach ($c in $cases) {
    $kind = $c.expected.kind
    if (-not $c.truthSql) { continue }
    $sql = $c.truthSql.TrimEnd(';')
    $out = @(Invoke-Sql $sql)
    if ($out.Count -gt 0 -and "$($out[0])" -match 'ERROR') {
        $stale++
        Write-Host ("[ERROR] {0,-12} truthSql 执行失败：{1}" -f $c.id, "$($out[0])".Substring(0, [Math]::Min(60, "$($out[0])".Length))) -ForegroundColor Red
        continue
    }
    $checked++

    if ($kind -eq 'SCALAR_NUMBER') {
        $actualText = "$($out[0])".Trim()
        if ($actualText -in @('NULL', '')) {
            # truthSql 查不出值 = 题目针对的数据在当前数据集里不存在（例如问了一个已不存在的品类），
            # 这不是"值变了"，改期望值也救不了，只能改题或换数据集。
            $unanswerable++
            Write-Host ("[不可答] {0,-12} truthSql 返回 NULL —— 题目针对的数据已不存在" -f $c.id) -ForegroundColor Magenta
            continue
        }
        $actual = 0.0
        $parsed = [double]::TryParse($actualText, [ref]$actual)
        $expected = [double]$c.expected.value
        $tol = if ($null -ne $c.expected.absoluteTolerance) { [double]$c.expected.absoluteTolerance } else { 0.0 }
        if ($parsed -and [Math]::Abs($actual - $expected) -le $tol) {
            Write-Host ("[新鲜] {0,-12} {1}" -f $c.id, $actualText) -ForegroundColor Green
        }
        else {
            if ($Refresh -and $parsed) {
                $c.expected.value = $actual
                $refreshed++
                Write-Host ("[重算] {0,-12} {1} → {2}" -f $c.id, $expected, $actualText) -ForegroundColor Yellow
            }
            else {
                $stale++
                Write-Host ("[过期] {0,-12} 期望={1} 实际={2}" -f $c.id, $expected, $actualText) -ForegroundColor Red
            }
        }
    }
    elseif ($kind -eq 'ROWSET') {
        $actualRows = $out.Count
        $expectedRows = @($c.expected.rows).Count
        if ($actualRows -eq $expectedRows) {
            Write-Host ("[新鲜] {0,-12} 行数={1}" -f $c.id, $actualRows) -ForegroundColor Green
        }
        else {
            if ($Refresh) {
                $cols = @($c.expected.columns)
                $newRows = @()
                foreach ($line in $out) {
                    $cells = "$line" -split "`t"
                    $row = [ordered]@{}
                    for ($i = 0; $i -lt $cols.Count; $i++) {
                        $cell = if ($i -lt $cells.Count) { $cells[$i] } else { '' }
                        $num = 0.0
                        $row[$cols[$i]] = if ([double]::TryParse($cell, [ref]$num)) { $num } else { $cell }
                    }
                    $newRows += [pscustomobject]$row
                }
                $c.expected.rows = $newRows
                $refreshed++
                Write-Host ("[重算] {0,-12} 行数 {1} → {2}" -f $c.id, $expectedRows, $actualRows) -ForegroundColor Yellow
            }
            else {
                $stale++
                Write-Host ("[过期] {0,-12} 期望行数={1} 实际={2}" -f $c.id, $expectedRows, $actualRows) -ForegroundColor Red
            }
        }
    }
    else {
        Write-Host ("[跳过] {0,-12} 未知期望类型 {1}" -f $c.id, $kind) -ForegroundColor DarkGray
    }
}

if ($Refresh) {
    $suite | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $suitePath -Encoding utf8
    Write-Host ''
    Write-Host "已按当前数据重算 $refreshed 项期望值并写回。" -ForegroundColor Green
    Write-Host '注意：数据集一旦刷新，本 Suite 会再次过期，需重新执行 -Refresh。' -ForegroundColor Yellow
    exit 0
}

Write-Host ''
if ($unanswerable -gt 0) {
    Write-Host "另有 $unanswerable 项**不可回答**：题目针对的数据在当前数据集里不存在，改期望值救不了。" -ForegroundColor Magenta
}
if ($stale -eq 0 -and $unanswerable -eq 0) {
    Write-Host "Suite 与当前数据一致（$checked 项）。" -ForegroundColor Green
    exit 0
}
if ($stale -gt 0) {
    Write-Host "**$stale / $checked 项期望值已过期** —— 先按当前数据重新生成（加 -Refresh），否则失败会看起来像产品坏了。" -ForegroundColor Red
}
else {
    Write-Host "期望值已与当前数据对齐，但仍有 $unanswerable 项题目不可回答。" -ForegroundColor Red
    Write-Host '这套 Suite 现在不能用于验收或复测：先处理不可回答的题目（改题或换数据集）。' -ForegroundColor Yellow
}
exit 1
