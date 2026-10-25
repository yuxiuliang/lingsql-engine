# 演示 10 问：逐个调用 /api/v1/chat/ask，输出「答得出 / 说明原因」的分类结果与证据摘要。
# 期望形态：7 问有数据，3 问明确说明「库里没有这个数据」并给出澄清问题。

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8000',
    [string]$TenantId = 'demo_sales',
    [string]$Username = 'demo_admin',
    [string]$ReportPath = '',
    [int]$QuestionDelayMs = 300
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/DemoSecrets.ps1')

$secrets = Get-DemoSecrets
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($ReportPath -eq '') {
    $ReportPath = Join-Path $root '.local-private/demo/demo-10q-report.md'
}

$questions = @(
    @{ Id = 'Q1'; Kind = '答得出'; Text = '2026年9月已完成订单的销售额是多少？' }
    @{ Id = 'Q2'; Kind = '答得出'; Text = '哪些商品当前库存低于安全库存？' }
    @{ Id = 'Q3'; Kind = '答得出'; Text = '按商品统计9月销售额，从高到低排列。' }
    @{ Id = 'Q4'; Kind = '答得出'; Text = '华东地区客户9月贡献了多少销售额？' }
    @{ Id = 'Q5'; Kind = '答得出'; Text = '各销售渠道的订单数量分别是多少？' }
    @{ Id = 'Q6'; Kind = '答得出'; Text = '哪个商品的毛利率最高？' }
    @{ Id = 'Q7'; Kind = '答得出'; Text = '2026年9月有多少个客户下过单？' }
    # Q8 的预期取决于当前加载的演示数据集：
    #   - 刷新数据集（sql/demo_sales_inventory_refresh.sql）覆盖到当天，10 月有数据 → 答得出；
    #   - 生成器数据集（scripts/demo/seed/generate-demo-data.mjs）只到 9 月底 → 答不了。
    @{ Id = 'Q8'; Kind = '答得出'; Text = '2026年10月的销售额是多少？' }
    @{ Id = 'Q9'; Kind = '答不了'; Text = '和2025年9月相比销售额增长了多少？' }
    @{ Id = 'Q10'; Kind = '答不了'; Text = '各客户的满意度评分是多少？' }
)

function Invoke-DemoApi {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token = '', [string]$Tenant = '')
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token -ne '') { $headers['Authorization'] = "Bearer $Token" }
    if ($Tenant -ne '') { $headers['X-Tenant-Id'] = $Tenant }
    $parameters = @{
        Uri                = "$($BaseUrl.TrimEnd('/'))$Path"
        Method             = $Method
        Headers            = $headers
        TimeoutSec         = 180
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } elseif ($Body -is [array]) { $Body | ConvertTo-Json -Depth 8 -Compress -AsArray } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $parameters['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $parameters['ContentType'] = 'application/json; charset=UTF-8'
    }
    $response = Invoke-WebRequest @parameters
    return ([System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json)
}

Write-Host "[1/2] 登录演示账号 $TenantId/$Username"
$login = Invoke-DemoApi -Method POST -Path '/api/v1/auth/login' -Body @{
    tenantId = $TenantId
    username = $Username
    password = $secrets.app.demoAdminFinal
}
if (-not $login.data.token) { throw "登录失败：$($login.message)" }
$token = $login.data.token

Write-Host '[2/2] 逐个提问'
$results = @()
foreach ($question in $questions) {
    $response = Invoke-DemoApi -Method POST -Path '/api/v1/chat/ask' -Token $token -Tenant $TenantId -Body @{ question = $question.Text }
    $data = $response.data
    $rowCount = if ($data -and $data.data) { @($data.data).Count } else { 0 }
    $answer = if ($data) { $data.answer } else { $response.message }
    $failed = ($response.code -ne 200) -or ($data -and $data.failureCode)
    $results += [pscustomobject]@{
        Id       = $question.Id
        Kind     = $question.Kind
        Question = $question.Text
        RowCount = $rowCount
        Answer   = $answer
        Sql      = if ($data) { $data.sql } else { '' }
        Failed   = [bool]$failed
    }
    $flag = if ($failed) { 'FAIL' } elseif ($rowCount -gt 0) { 'OK  ' } else { 'EMPTY' }
    Write-Host ("      [{0}] {1} 行={2}  {3}" -f $flag, $question.Id, $rowCount, $answer)
    if ($QuestionDelayMs -gt 0) { Start-Sleep -Milliseconds $QuestionDelayMs }
}

$answered = @($results | Where-Object { $_.RowCount -gt 0 })
$explained = @($results | Where-Object { $_.RowCount -eq 0 -and -not $_.Failed })
$failed = @($results | Where-Object { $_.Failed })

$lines = @()
$lines += '# LingSQL 演示 10 问结果'
$lines += ''
$lines += "- 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += "- 环境：$BaseUrl（租户 $TenantId）"
$lines += "- 汇总：有数据 $($answered.Count) 问，说明原因 $($explained.Count) 问，执行失败 $($failed.Count) 问"
$lines += ''
$lines += '| # | 预期 | 问题 | 返回行数 | 回答 |'
$lines += '|---|---|---|---|---|'
foreach ($row in $results) {
    $answer = ($row.Answer -replace '\|', '\|' -replace "`r?`n", ' ')
    $lines += "| $($row.Id) | $($row.Kind) | $($row.Question) | $($row.RowCount) | $answer |"
}
$lines += ''
$lines += '## 实际执行的 SQL'
$lines += ''
foreach ($row in $results) {
    $sql = ($row.Sql -replace "`r?`n", ' ')
    $lines += "- **$($row.Id)**：$sql"
}

$directory = Split-Path -Parent $ReportPath
if ($directory -and -not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
$lines | Set-Content -LiteralPath $ReportPath -Encoding utf8

Write-Host ''
Write-Host "汇总：有数据 $($answered.Count) 问 / 说明原因 $($explained.Count) 问 / 失败 $($failed.Count) 问"
Write-Host "报告：$ReportPath"
