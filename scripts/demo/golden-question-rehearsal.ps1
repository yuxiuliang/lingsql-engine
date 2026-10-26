<#
.SYNOPSIS
    黄金问题「内部演练」执行器（Windows／PowerShell）。

.DESCRIPTION
    为什么存在：仓库里的正式验收器 scripts/ops/golden_question_acceptance.sh 依赖 bash + jq + curl，
    在未安装 jq 的 Windows 机器上无法运行。本脚本用 PowerShell 实现同一套「逐题提问 → 与期望对比 →
    产出逐题结果」的核心动作，用于**内部演练**。

    ⚠️ 这不是客户验收证据：客户验收必须由客户冻结题目后，用 golden_question_acceptance.sh
    产出并带 SHA-256 锁定的 suite。本脚本产出的文件带 rehearsalOnly=true 标记。

.PARAMETER SuitePath
    suite JSON 路径，默认 docs/examples/golden-question-suite.demo-rehearsal.json。

.PARAMETER TenantId
    提问所用租户，默认 demo_sales。

.EXAMPLE
    pwsh -File scripts/demo/golden-question-rehearsal.ps1
#>
[CmdletBinding()]
param(
    [string]$SuitePath = '',
    [string]$TenantId = 'demo_sales',
    [string]$Username = 'demo_admin',
    [string]$BaseUrl = 'http://127.0.0.1:8000'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $SuitePath) {
    $SuitePath = Join-Path $root 'docs/examples/golden-question-suite.demo-rehearsal.json'
}
$secrets = Get-Content -LiteralPath (Join-Path $root '.local-private/demo/demo-secrets.json') -Raw | ConvertFrom-Json

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [string]$Tenant)
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    if ($Tenant) { $headers['X-Tenant-Id'] = $Tenant }
    $req = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 120; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $req['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6 -Compress))
        $req['ContentType'] = 'application/json; charset=UTF-8'
    }
    $resp = Invoke-WebRequest @req
    return [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
}

$suite = Get-Content -LiteralPath $SuitePath -Raw | ConvertFrom-Json
Write-Host ''
Write-Host "演练套件: $($suite.suiteId) / $($suite.suiteVersion)  共 $($suite.businessCases.Count) 题" -ForegroundColor Cyan
Write-Host '⚠️ 内部演练，不是客户验收证据' -ForegroundColor Yellow

$login = (Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    tenantId = $TenantId; username = $Username; password = $secrets.app.demoAdminFinal
}) | ConvertFrom-Json
if (-not $login.data.token) { throw "登录失败: $($login.message)" }
$token = $login.data.token

$results = @()
$pass = 0
$fail = 0
foreach ($case in $suite.businessCases) {
    $ask = (Invoke-Api -Method POST -Path '/api/v1/chat/ask' -Token $token -Tenant $TenantId -Body @{ question = $case.question }) | ConvertFrom-Json
    $rows = @($ask.data.data)
    $kind = $case.expected.kind
    $ok = $false
    $actual = ''
    switch ($kind) {
        'SCALAR_NUMBER' {
            $col = $case.expected.column
            $raw = if ($rows.Count -gt 0) { $rows[0].$col } else { $null }
            $actual = "$raw"
            if ($null -ne $raw) {
                $diff = [Math]::Abs([double]$raw - [double]$case.expected.value)
                $ok = $diff -le [double]$case.expected.absoluteTolerance
            }
        }
        'ROWSET' {
            $actual = "rows=$($rows.Count)"
            $ok = ($rows.Count -eq [int]$case.expected.rowCount)
        }
        'REFUSAL' {
            $keywords = '没有导入|算不出|查不到|没有「|没有该|需要接入|为空|不存在|没有相关数据|答不了'
            $actual = "rows=$($rows.Count)"
            $ok = ($ask.data.answer -match $keywords)
        }
        default { $actual = "unsupported kind=$kind" }
    }
    if ($ok) { $pass++ } else { $fail++ }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1,-6} {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $case.id, $actual) -ForegroundColor $color
    $results += [pscustomobject]@{
        id = $case.id
        priority = $case.priority
        question = $case.question
        expectedKind = $kind
        actual = $actual
        passed = $ok
        answer = $ask.data.answer
    }
}

$outDir = Join-Path $root '.local-private/demo'
$outFile = Join-Path $outDir ("golden-rehearsal-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
$payload = [pscustomobject]@{
    rehearsalOnly = $true
    note = '内部演练结果，不是客户验收证据；客户验收请用 scripts/ops/golden_question_acceptance.sh'
    suiteId = $suite.suiteId
    suiteVersion = $suite.suiteVersion
    tenantId = $TenantId
    executedAt = (Get-Date).ToString('o')
    total = $results.Count
    passed = $pass
    failed = $fail
    cases = $results
}
$payload | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $outFile -Encoding utf8

Write-Host ''
Write-Host ("汇总：通过 $pass / $($results.Count)，失败 $fail") -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "证据：$outFile"
if ($fail -gt 0) { exit 1 }
exit 0
