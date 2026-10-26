<#
.SYNOPSIS
    真实模型冒烟 + 成本实测：跑若干问题，从计费账本读真实 token 与金额。

.DESCRIPTION
    用途：接上真实模型（或在客户环境配好模型）后，用一条命令得到「这次提问花了多少 token、多少钱」。
    现在演示环境用本地桩，用量写死为 168 token，脚本会明确提示"当前不是真实用量"。

    做法：记录执行前的账本水位 → 依次提问 → 查询水位之后产生的调用记录 →
    按阶段汇总 token 与金额，并给出单次平均值。

.PARAMETER TenantId
    提问所用租户，默认 demo_sales。

.PARAMETER QuestionCount
    取前多少道题跑，默认 5。

.EXAMPLE
    pwsh -File scripts/demo/real-model-smoke.ps1 -TenantId demo_sales -QuestionCount 5
#>
[CmdletBinding()]
param(
    [string]$TenantId = 'demo_sales',
    [string]$Username = 'demo_admin',
    [string]$BaseUrl = 'http://127.0.0.1:8000',
    [string]$SuitePath = '',
    [int]$QuestionCount = 5,
    [int]$MysqlPort = 3407
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $SuitePath) {
    $SuitePath = Join-Path $root 'docs/examples/golden-question-suite.demo-rehearsal.json'
}
$secrets = Get-Content -LiteralPath (Join-Path $root '.local-private/demo/demo-secrets.json') -Raw | ConvertFrom-Json
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token)
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $headers['X-Tenant-Id'] = $TenantId
    $req = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 180; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $req['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6 -Compress))
        $req['ContentType'] = 'application/json; charset=UTF-8'
    }
    $resp = Invoke-WebRequest @req
    return [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
}

function Get-MaxInvocationId {
    $v = & $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names --database=lingsql_demo_control `
        --execute="SELECT IFNULL(MAX(id),0) FROM t_lingsql_engine_billing_invocation;" 2>&1
    return [long]("$v".Trim())
}

$suite = Get-Content -LiteralPath $SuitePath -Raw | ConvertFrom-Json
$questions = @($suite.businessCases | Select-Object -First $QuestionCount)
Write-Host ''
Write-Host "租户=$TenantId  题目数=$($questions.Count)  模型=按该租户当前路由" -ForegroundColor Cyan

$login = (Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    tenantId = $TenantId; username = $Username; password = $secrets.app.demoAdminFinal
}) | ConvertFrom-Json
if (-not $login.data.token) { throw "登录失败: $($login.message)" }
$token = $login.data.token

$watermark = Get-MaxInvocationId
$results = @()
foreach ($q in $questions) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = (Invoke-Api -Method POST -Path '/api/v1/chat/ask' -Token $token -Body @{ question = $q.question }) | ConvertFrom-Json
    $sw.Stop()
    $results += [pscustomobject]@{
        id       = $q.id
        code     = $r.code
        rows     = @($r.data.data).Count
        elapsed  = [math]::Round($sw.Elapsed.TotalSeconds, 2)
        question = $q.question
    }
}

Start-Sleep -Seconds 2
$rows = & $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names --default-character-set=utf8mb4 --database=lingsql_demo_control `
    --execute="SELECT stage, COUNT(*), SUM(actual_tokens), SUM(actual_amount), MIN(unit_cost), MAX(unit_cost) FROM t_lingsql_engine_billing_invocation WHERE id > $watermark GROUP BY stage ORDER BY stage;" 2>&1

Write-Host ''
Write-Host '==== 本轮产生的物理调用 ====' -ForegroundColor Cyan
$totalTokens = 0; $totalAmount = 0; $totalCalls = 0
foreach ($line in $rows) {
    $p = $line -split "`t"
    if ($p.Count -lt 6) { continue }
    $totalCalls += [int]$p[1]; $totalTokens += [long]$p[2]; $totalAmount += [long]$p[3]
    Write-Host ("  {0,-18} 调用={1,3}  tokens={2,7}  金额={3,10}  单价={4}~{5}" -f $p[0], $p[1], $p[2], $p[3], $p[4], $p[5])
}

Write-Host ''
Write-Host ("合计：调用 {0} 次，tokens {1}，金额 {2}" -f $totalCalls, $totalTokens, $totalAmount)
if ($questions.Count -gt 0 -and $totalCalls -gt 0) {
    Write-Host ("单次提问均值：tokens {0:N0}，金额 {1:N2}" -f ($totalTokens / $questions.Count), ($totalAmount / $questions.Count))
}

$avgTokens = if ($totalCalls -gt 0) { $totalTokens / $totalCalls } else { 0 }
if ([math]::Abs($avgTokens - 168) -lt 0.5 -and $totalCalls -gt 0) {
    Write-Host ''
    Write-Host '⚠️ 每次调用都是 168 token —— 这是本地模型桩写死的用量，不是真实花费。' -ForegroundColor Yellow
    Write-Host '   接上真实模型后重跑本脚本，得到的才是可用于对账的成本。' -ForegroundColor Yellow
}
if ($totalCalls -gt 0 -and $totalAmount -eq $totalTokens) {
    Write-Host '⚠️ 金额恰好等于 token 数：说明单价仍是默认值 1，请在计费策略里配置真实单价。' -ForegroundColor Yellow
}

Write-Host ''
Write-Host '==== 逐题结果 ====' -ForegroundColor Cyan
$results | ForEach-Object { Write-Host ("  [{0}] {1,-6} code={2} rows={3} {4}s" -f $_.id, '', $_.code, $_.rows, $_.elapsed) }
