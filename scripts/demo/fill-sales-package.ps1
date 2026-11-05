<#
.SYNOPSIS
    填写销售包清单并立刻校验：签约当天减少手工改 JSON 的出错机会。

.DESCRIPTION
    `sales-package-manifest.example.json` 里有 5 类占位值，每类出现多次：
    服务方名称、服务方统一社会信用代码、客户名称、客户统一社会信用代码、内部审批记录号。
    手工替换容易漏（`SalesPackageValidator` 会以"仍是模板占位值"拒绝，退出码 2）。

    本脚本：读取示例清单 → 替换全部占位值 → 写到 `out/<时间戳>/` → 立刻调用校验器。
    原示例文件**不会被修改**，输出目录每次新建，便于留档与对比。

    注意：脚本只负责"填"和"校验"，不判断这些信息是否真实/合规——那是签约方的责任。

.PARAMETER CustomerName
    客户签约全称（必填）。

.PARAMETER CustomerUscc
    客户统一社会信用代码（必填，18 位）。

.PARAMETER VendorName
    服务方签约全称（必填）。

.PARAMETER VendorUscc
    服务方统一社会信用代码（必填，18 位）。

.PARAMETER ApprovalRef
    内部审批记录号（必填）。

.PARAMETER ReleaseCommit
    发布提交号（40 位十六进制），默认全 0 表示"尚未冻结"。

.EXAMPLE
    pwsh -File scripts/demo/fill-sales-package.ps1 `
        -CustomerName "某某有限公司" -CustomerUscc "91320000XXXXXXXXXX" `
        -VendorName "某某科技（苏州）有限公司" -VendorUscc "91320500YYYYYYYYYY" `
        -ApprovalRef "AP-2026-001"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CustomerName,
    [Parameter(Mandatory = $true)][string]$CustomerUscc,
    [Parameter(Mandatory = $true)][string]$VendorName,
    [Parameter(Mandatory = $true)][string]$VendorUscc,
    [Parameter(Mandatory = $true)][string]$ApprovalRef,
    # 审核状态：默认全部关闭。校验器只能校验"你是否声明"，无法核实真伪，
    # 所以必须由人显式打开——没做完审核就不要打开。
    [switch]$LegalApproved,
    [switch]$FinanceApproved,
    [switch]$DataProtectionApproved,
    [string]$ReleaseCommit = ('0' * 40),
    [string]$SuiteSha256 = ('0' * 64),
    [string]$EvidenceSha256 = ('0' * 64),
    [string]$ReportSha256 = ('0' * 64)
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $root '.local-private/commercial/docs/examples/sales-package-manifest.example.json'

if (-not (Test-Path -LiteralPath $source)) {
    Write-Host "找不到示例清单：$source" -ForegroundColor Red
    exit 1
}

# 防呆：不允许把占位值当输入传进来，也不接受明显的测试名
$banned = @('请替换', 'TODO', '示例', '测试客户', 'XXX')
foreach ($pair in @(
        @{ Name = 'CustomerName'; Value = $CustomerName },
        @{ Name = 'VendorName'; Value = $VendorName },
        @{ Name = 'ApprovalRef'; Value = $ApprovalRef }
    )) {
    foreach ($b in $banned) {
        if ($pair.Value -like "*$b*") {
            Write-Host "参数 $($pair.Name) 含疑似占位/测试内容「$b」：$($pair.Value)" -ForegroundColor Red
            Write-Host '签约清单请使用真实全称与真实审批号。' -ForegroundColor Yellow
            exit 1
        }
    }
}
foreach ($pair in @(
        @{ Name = 'CustomerUscc'; Value = $CustomerUscc },
        @{ Name = 'VendorUscc'; Value = $VendorUscc }
    )) {
    if ($pair.Value -notmatch '^[0-9A-Z]{18}$') {
        Write-Host "参数 $($pair.Name) 不是 18 位大写字母/数字：$($pair.Value)" -ForegroundColor Red
        exit 1
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $root ".local-private/commercial/out/$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$manifestPath = Join-Path $outDir 'sales-package-manifest.json'

$text = Get-Content -LiteralPath $source -Raw
$text = $text.Replace('请替换为服务方签约全称', $VendorName)
$text = $text.Replace('请替换为服务方统一社会信用代码', $VendorUscc)
$text = $text.Replace('请替换为客户签约全称', $CustomerName)
$text = $text.Replace('请替换为客户统一社会信用代码', $CustomerUscc)
$text = $text.Replace('请替换为内部审批记录号', $ApprovalRef)
# 审核状态只在显式要求时打开；JSON 里这三项默认 false
if ($LegalApproved) { $text = $text.Replace('"legalApproved": false', '"legalApproved": true') }
if ($FinanceApproved) { $text = $text.Replace('"financeApproved": false', '"financeApproved": true') }
if ($DataProtectionApproved) { $text = $text.Replace('"dataProtectionApproved": false', '"dataProtectionApproved": true') }
$text = $text.Replace('0000000000000000000000000000000000000000', $ReleaseCommit)
Set-Content -LiteralPath $manifestPath -Value $text -Encoding utf8 -NoNewline

# 自检：替换后不该再残留占位值
$leftover = @(Select-String -Path $manifestPath -Pattern '请替换为' -AllMatches)
if ($leftover.Count -gt 0) {
    Write-Host "清单里仍有 $($leftover.Count) 处占位值，请手工检查：$manifestPath" -ForegroundColor Red
    exit 1
}

Write-Host "已生成清单：$manifestPath" -ForegroundColor Cyan
# 把"声明了什么"打在屏幕上，避免清单里写着 true 而没人记得是谁开的
$declared = @()
if ($LegalApproved) { $declared += '法务审核=已通过' } else { $declared += '法务审核=未声明' }
if ($FinanceApproved) { $declared += '财务审核=已通过' } else { $declared += '财务审核=未声明' }
if ($DataProtectionApproved) { $declared += '数据保护审核=已通过' } else { $declared += '数据保护审核=未声明' }
Write-Host ("审核声明：{0}" -f ($declared -join '；')) -ForegroundColor DarkGray
if ($LegalApproved -or $FinanceApproved -or $DataProtectionApproved) {
    Write-Host '提醒：这些是**事实声明**，校验器无法核实。只有在相应审核确实完成后才应打开。' -ForegroundColor Yellow
}

# 立刻校验（复用既有包装脚本，保持参数语义一致）
& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'run-sales-package-validator.ps1') `
    -Manifest $manifestPath `
    -ReleaseCommit $ReleaseCommit `
    -SuiteSha256 $SuiteSha256 `
    -EvidenceSha256 $EvidenceSha256 `
    -ReportSha256 $ReportSha256
exit $LASTEXITCODE
