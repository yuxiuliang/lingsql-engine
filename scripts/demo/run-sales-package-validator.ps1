<#
.SYNOPSIS
    在 Windows 上运行"可签约销售包"校验器（替代只能跑 bash 的 sales_package_guard.sh）。

.DESCRIPTION
    背景：.local-private/commercial 下有一套签约门禁——Java 校验器 lingsql-sales-package-validator.jar
    与 bash 包壳 sales_package_guard.sh。本机没有 WSL 发行版、也没有 jq，
    包壳跑不了，但 **jar 本身可以直接用 java 跑**（2026-10-30 实测可行）。

    校验器要求 5 个参数：清单文件、候选发布提交、冻结 Suite 的 SHA-256、
    证据索引的 SHA-256、验收报告的 SHA-256。它会校验文档是否齐全、哈希是否对得上、
    是否还残留模板占位值等。

.PARAMETER Manifest
    销售包清单 JSON 路径（schema 见 .local-private/commercial/docs/schemas/）。

.PARAMETER ReleaseCommit
    候选发布提交，40 位小写 Git SHA。

.PARAMETER SuiteSha256
    双方冻结的黄金问题 Suite 的 SHA-256（64 位小写十六进制）。

.PARAMETER EvidenceSha256
    证据索引的 SHA-256。

.PARAMETER ReportSha256
    验收报告的 SHA-256。

.EXAMPLE
    pwsh -File scripts/demo/run-sales-package-validator.ps1 `
        -Manifest .local-private/commercial/docs/examples/sales-package-manifest.example.json `
        -ReleaseCommit 0000000000000000000000000000000000000000 `
        -SuiteSha256 ('0'*64) -EvidenceSha256 ('0'*64) -ReportSha256 ('0'*64)

    对示例模板的预期结果是**被拒绝**（里面的 customerName 等仍是"请替换为…"占位值），
    退出码 2。这正是它该有的行为。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Manifest,
    [Parameter(Mandatory = $true)][string]$ReleaseCommit,
    [Parameter(Mandatory = $true)][string]$SuiteSha256,
    [Parameter(Mandatory = $true)][string]$EvidenceSha256,
    [Parameter(Mandatory = $true)][string]$ReportSha256
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$jar = Join-Path $root '.local-private/commercial/target/lingsql-sales-package-validator.jar'

if (-not (Test-Path -LiteralPath $jar)) {
    Write-Host "找不到校验器：$jar" -ForegroundColor Red
    Write-Host '先构建：cd .local-private/commercial && mvn -B -DskipTests package' -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path -LiteralPath $Manifest)) {
    Write-Host "找不到清单文件：$Manifest" -ForegroundColor Red
    exit 1
}

# 中文输出：Java 19+ 用 stdout/stderr.encoding 指定，配合控制台 UTF-8
$prevEncoding = [Console]::OutputEncoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try {
    & java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $jar `
        com.lingsql.commercial.SalesPackageValidator `
        $Manifest $ReleaseCommit $SuiteSha256 $EvidenceSha256 $ReportSha256
}
finally {
    [Console]::OutputEncoding = $prevEncoding
}
$code = $LASTEXITCODE

Write-Host ''
switch ($code) {
    0 { Write-Host '校验通过（PASS）——该销售包可以进入签约流程。' -ForegroundColor Green }
    2 { Write-Host '校验未通过（INVALID）——按上面的提示修清单或补文档，不要带病签约。' -ForegroundColor Red }
    default { Write-Host "校验器返回未知退出码：$code" -ForegroundColor Yellow }
}
exit $code
