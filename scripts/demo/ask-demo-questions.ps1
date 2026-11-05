# 演示 10 问：逐个调用 /api/v1/chat/ask，记录返回行数、答案与实际执行的 SQL。
#
# 与旧版的区别：不再写死「答得出 / 答不了」预期，而是按**实际返回**判定结果类型。
# 原因：演示数据集有两套（生成器数据集只到 9 月底，刷新数据集覆盖到当天），
# 写死的预期会在换数据后与事实直接矛盾，报告反而变成误导。
#
# 结果类型：
#   有数据   —— 返回行数 > 0 且答案不是拒答
#   明确拒答 —— 答案命中拒答特征（库里没有这份数据 / 算不出 / 查不到 …）
#   空结果   —— 返回 0 行且不是拒答
#   失败     —— 接口返回非 200 或带 failureCode
# 另会标注「行数>0 但拒答」的异常：聚合查询无匹配时返回一行 NULL，属正常现象。

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
    # 报告名带租户与时间戳：此前是固定文件名，换个租户试跑会把演示报告覆盖掉。
    $ReportPath = Join-Path $root ('.local-private/demo/demo-10q-{0}-{1}.md' -f $TenantId, (Get-Date -Format 'yyyyMMdd-HHmmss'))
}

$questions = @(
    @{ Id = 'Q1'; Text = '2026年9月已完成订单的销售额是多少？' }
    @{ Id = 'Q2'; Text = '哪些商品当前库存低于安全库存？' }
    @{ Id = 'Q3'; Text = '按商品统计9月销售额，从高到低排列。' }
    @{ Id = 'Q4'; Text = '华东地区客户9月贡献了多少销售额？' }
    @{ Id = 'Q5'; Text = '各销售渠道的订单数量分别是多少？' }
    @{ Id = 'Q6'; Text = '哪个商品的毛利率最高？' }
    @{ Id = 'Q7'; Text = '2026年9月有多少个客户下过单？' }
    @{ Id = 'Q8'; Text = '2026年10月的销售额是多少？' }
    @{ Id = 'Q9'; Text = '和2025年9月相比销售额增长了多少？' }
    @{ Id = 'Q10'; Text = '各客户的满意度评分是多少？' }
)

# 拒答特征：说明「库里没有这份数据 / 算不出来」，而不是给出数字。
$refusalPattern = '没有导入|算不出|查不到|无法|没有「|没有该|需要接入|为空|不存在|没有相关数据|答不了'

function Get-ResultKind {
    param([int]$RowCount, [string]$Answer, [bool]$Failed)
    if ($Failed) { return '失败' }
    if ($Answer -match $refusalPattern) { return '明确拒答' }
    if ($RowCount -gt 0) { return '有数据' }
    return '空结果'
}

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
    $kind = Get-ResultKind -RowCount $rowCount -Answer $answer -Failed ([bool]$failed)
    $results += [pscustomobject]@{
        Id        = $question.Id
        Question  = $question.Text
        RowCount  = $rowCount
        Kind      = $kind
        Answer    = $answer
        Sql       = if ($data) { $data.sql } else { '' }
        Failed    = [bool]$failed
        # 行数>0 却拒答：聚合查询无匹配时返回一行 NULL，不是缺陷，但必须在报告里说清
        NullRow   = ($kind -eq '明确拒答' -and $rowCount -gt 0)
    }
    $flag = switch ($kind) { '有数据' { 'OK   ' } '明确拒答' { 'REFUSE' } '空结果' { 'EMPTY ' } default { 'FAIL  ' } }
    Write-Host ("      [{0}] {1} 行={2}  {3}" -f $flag, $question.Id, $rowCount, $answer)
    if ($QuestionDelayMs -gt 0) { Start-Sleep -Milliseconds $QuestionDelayMs }
}

$withData = @($results | Where-Object { $_.Kind -eq '有数据' })
$refused = @($results | Where-Object { $_.Kind -eq '明确拒答' })
$empty = @($results | Where-Object { $_.Kind -eq '空结果' })
$failed = @($results | Where-Object { $_.Kind -eq '失败' })
$nullRows = @($results | Where-Object { $_.NullRow })

# 依据 Q1（9 月）与 Q8（10 月）是否真有数据，反推当前数据集覆盖了哪些月份。
$q1 = $results | Where-Object { $_.Id -eq 'Q1' } | Select-Object -First 1
$q8 = $results | Where-Object { $_.Id -eq 'Q8' } | Select-Object -First 1
$september = if ($q1 -and $q1.Kind -eq '有数据') { '有数据' } else { '无数据' }
$october = if ($q8 -and $q8.Kind -eq '有数据') { '有数据' } else { '无数据' }

$lines = @()
$lines += '# LingSQL 演示 10 问结果'
$lines += ''
$lines += "- 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += "- 环境：$BaseUrl（租户 $TenantId）"
$lines += "- 汇总：有数据 $($withData.Count) 问，明确拒答 $($refused.Count) 问，空结果 $($empty.Count) 问，失败 $($failed.Count) 问"
$lines += "- 数据覆盖推断（依据 Q1／Q8 是否真有返回行，不是配置）：2026 年 9 月$september，2026 年 10 月$october"
$lines += ''
$lines += '> 本报告只记录**实际发生的结果**，不再写死"预期答得出/答不了"。'
$lines += '> 换演示数据集后无需改脚本，直接重跑即可，报告不会与事实矛盾。'
$lines += ''
$lines += '| # | 问题 | 返回行数 | 结果类型 | 回答 |'
$lines += '|---|---|---|---|---|'
foreach ($row in $results) {
    $answer = ($row.Answer -replace '\|', '\|' -replace "`r?`n", ' ')
    $kindLabel = if ($row.NullRow) { '明确拒答（返回 1 行 NULL）' } else { $row.Kind }
    $lines += "| $($row.Id) | $($row.Question) | $($row.RowCount) | $kindLabel | $answer |"
}
$lines += ''

if ($nullRows.Count -gt 0) {
    $lines += '## 说明：行数大于 0 但属于拒答'
    $lines += ''
    $lines += "涉及的题号：$((($nullRows | ForEach-Object { $_.Id }) -join '、'))。"
    $lines += '这类问题的 SQL 是聚合查询，无匹配行时数据库仍会返回一行 NULL，因此"返回行数=1"并不代表有数据。'
    $lines += '判定以答案是否给出具体数值为准，这是正常现象，不是缺陷。'
    $lines += ''
}

$lines += '## 实际执行的 SQL'
$lines += ''
foreach ($row in $results) {
    $sql = ($row.Sql -replace "`r?`n", ' ')
    $lines += "- **$($row.Id)**：$sql"
}
$lines += ''
$lines += '## 判定口径'
$lines += ''
$lines += "拒答特征（正则）：``$refusalPattern``。命中即判为「明确拒答」，优先于行数判断。"
$lines += ''

$directory = Split-Path -Parent $ReportPath
if ($directory -and -not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
$lines | Set-Content -LiteralPath $ReportPath -Encoding utf8

Write-Host ''
Write-Host "汇总：有数据 $($withData.Count) 问 / 明确拒答 $($refused.Count) 问 / 空结果 $($empty.Count) 问 / 失败 $($failed.Count) 问"
if ($nullRows.Count -gt 0) { Write-Host "注意：$((($nullRows | ForEach-Object { $_.Id }) -join '、')) 返回行数>0 但属拒答（聚合无匹配返回 NULL 行，正常）" }
Write-Host "数据覆盖推断：9 月$september / 10 月$october"
Write-Host "报告：$ReportPath"

# 退出码：出现「失败」即非零，便于把它当门禁用；空结果与拒答都是合法结果，不判失败。
if ($failed.Count -gt 0) {
    Write-Host "存在 $($failed.Count) 个失败问题（非零退出）" -ForegroundColor Red
    exit 1
}
if ($empty.Count -gt 0) {
    Write-Host "注意：$($empty.Count) 个问题返回空结果——若按当前数据集不该为空，请检查演示数据是否过期" -ForegroundColor Yellow
}
exit 0
