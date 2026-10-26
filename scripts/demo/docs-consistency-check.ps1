<#
.SYNOPSIS
    文档一致性检查：把文档里的清单和常量与代码／数据库对一遍。

.DESCRIPTION
    起因：文档里反复出现「凭记忆写的清单」——员工写成 6 个实际 8 个、常量改了文案没改、
    索引计数过期。这些错误不会报错，只会在演示或交接时误导人。

    本脚本只做机械核对，不改任何东西：
    ① 常量：关键数字口径里写的平台限制，是否与源码一致；
    ② 清单：租户／数字员工清单是否与数据库一致；
    ③ 索引：文档数、链接失效、声明计数；
    ④ 过期引用：是否还有地方引用已废弃的固定报告名。

.EXAMPLE
    pwsh -File scripts/demo/docs-consistency-check.ps1
#>
[CmdletBinding()]
param(
    [int]$MysqlPort = 3407
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$docs = Join-Path $root '.local-private/commercial/docs'
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
$failures = 0
$checks = 0

function Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    $script:checks++
    if ($Ok) {
        Write-Host ("[PASS] {0,-34} {1}" -f $Name, $Detail) -ForegroundColor Green
    } else {
        $script:failures++
        Write-Host ("[FAIL] {0,-34} {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

function Sql {
    param([string]$Sql)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            --default-character-set=utf8mb4 --database=lingsql_demo_control "--execute=$Sql" 2>&1)
}

if (-not (Test-Path $docs)) {
    Write-Host "未找到商业文档目录：$docs" -ForegroundColor Red
    exit 1
}
$numbersDoc = Join-Path $docs '关键数字口径.md'
$numbersText = if (Test-Path $numbersDoc) { Get-Content $numbersDoc -Raw } else { '' }

Write-Host ''
Write-Host '==== ① 常量与源码 ====' -ForegroundColor Cyan
$constChecks = @(
    @{ Name = '白名单表数上限'; Doc = 'MAX_ALLOWLIST_TABLES'; File = 'src/main/java/com/lingsql/datasource/TenantDataSourceConfigService.java' },
    @{ Name = '展开表数上限'; Doc = 'DEFAULT_MAX_FULL_TABLES'; File = 'src/main/java/com/lingsql/schema/SchemaTableSelector.java' },
    @{ Name = '表结构字符上限'; Doc = 'MAX_SCHEMA_CHARS'; File = 'src/main/java/com/lingsql/schema/MetadataCacheService.java' }
)
foreach ($c in $constChecks) {
    $src = Join-Path $root $c.File
    if (-not (Test-Path $src)) { Check $c.Name $false "源码不存在: $($c.File)"; continue }
    $line = (Select-String -Path $src -Pattern "$($c.Doc)\s*=" | Select-Object -First 1).Line
    if (-not $line) { Check $c.Name $false "未找到常量 $($c.Doc)"; continue }
    $value = ([regex]::Match($line, '=\s*([\d_]+)')).Groups[1].Value -replace '_', ''
    # 文档按中文习惯写成 48,000，比对前统一去掉千分位与下划线，避免误报
    $docNormalized = $numbersText -replace '[_ ,]', ''
    $docHas = $docNormalized -match [regex]::Escape($value)
    Check $c.Name $docHas "$($c.Doc)=$value；文档中$(if($docHas){'一致'}else{'未出现或不同'})"
}

Write-Host ''
Write-Host '==== ② 租户清单 ====' -ForegroundColor Cyan
$dbTenants = Sql "SELECT GROUP_CONCAT(tenant_id ORDER BY tenant_id) FROM t_lingsql_engine_tenant_db_config;"
$dbTenantSet = ("$dbTenants".Trim() -split ',') | Where-Object { $_ }
foreach ($t in $dbTenantSet) {
    $inDoc = $numbersText -match [regex]::Escape($t)
    Check "租户 $t" $inDoc $(if ($inDoc) { '文档已登记' } else { '文档未登记 <<< 请补进第五节' })
}

Write-Host ''
Write-Host '==== ③ 数字员工清单 ====' -ForegroundColor Cyan
$empRows = Sql "SELECT CONCAT(id,':',IFNULL(schedule_cron,'NULL'),':',schedule_enabled) FROM t_lingsql_engine_digital_employee ORDER BY id;"
$empIds = @($empRows | ForEach-Object { ("$_".Trim() -split ':')[0] } | Where-Object { $_ -match '^\d+$' })
$docEmpIds = [regex]::Matches($numbersText, '^\|\s*(\d+)\s*\|', 'Multiline') | ForEach-Object { $_.Groups[1].Value }
$docEmpRange = [regex]::Matches($numbersText, '^\|\s*(\d+)[–-](\d+)\s*\|', 'Multiline')
$covered = New-Object System.Collections.Generic.HashSet[string]
foreach ($id in $docEmpIds) { [void]$covered.Add($id) }
foreach ($m in $docEmpRange) {
    $from = [int]$m.Groups[1].Value; $to = [int]$m.Groups[2].Value
    for ($i = $from; $i -le $to; $i++) { [void]$covered.Add("$i") }
}
$missing = @($empIds | Where-Object { -not $covered.Contains($_) })
Check '员工清单覆盖' ($missing.Count -eq 0) $(if ($missing.Count -eq 0) { "共 $($empIds.Count) 个，全部登记" } else { "未登记: $($missing -join ',')" })

Write-Host ''
Write-Host '==== ④ 索引完整性 ====' -ForegroundColor Cyan
$idx = Join-Path $docs 'README-商业资料索引.md'
if (Test-Path $idx) {
    $idxText = Get-Content $idx -Raw
    $files = Get-ChildItem $docs -File -Filter *.md | Where-Object { $_.Name -ne 'README-商业资料索引.md' }
    $declared = ([regex]::Match($idxText, '共 (\d+) 份')).Groups[1].Value
    Check '索引声明计数' ($declared -eq "$($files.Count)") "声明=$declared 实际=$($files.Count)"
    $links = [regex]::Matches($idxText, '\]\(<?([^)<>]+?\.md)>?\)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    $broken = @($links | Where-Object { -not (Test-Path (Join-Path $docs ([System.Uri]::UnescapeDataString($_)))) })
    Check '索引链接' ($broken.Count -eq 0) $(if ($broken.Count -eq 0) { "$($links.Count) 条全部有效" } else { "失效: $($broken -join ',')" })
} else {
    Check '索引文件' $false 'README-商业资料索引.md 不存在'
}

Write-Host ''
Write-Host '==== ⑤ 过期引用 ====' -ForegroundColor Cyan
$stale = Get-ChildItem $docs -File -Filter *.md | Select-String -Pattern 'demo-10q-report\.md'
$staleReal = @($stale | Where-Object { $_.Line -notmatch '已废弃|不再覆盖|固定名' })
Check '固定报告名引用' ($staleReal.Count -eq 0) $(if ($staleReal.Count -eq 0) { '无残留（说明性提及不算）' } else { "残留 $($staleReal.Count) 处" })

Write-Host ''
Write-Host '==== ⑥ 卖点表的关键结论 ====' -ForegroundColor Cyan
$claimsDoc = Join-Path $docs '卖点核验表.md'
if (Test-Path $claimsDoc) {
    # 结论：账本全部 SETTLED、无 UNKNOWN 残留
    $unknown = (Sql "SELECT COUNT(*) FROM t_lingsql_engine_billing_invocation WHERE state='UNKNOWN';").Trim()
    $settled = (Sql "SELECT COUNT(*) FROM t_lingsql_engine_billing_invocation WHERE state='SETTLED';").Trim()
    Check '账本无 UNKNOWN' ("$unknown" -eq '0') "UNKNOWN=$unknown SETTLED=$settled"

    # 结论：规模夹具 60 / 100 / 200 张表
    foreach ($pair in @(@('scale_a', 60), @('scale_c', 100), @('scale_b', 200))) {
        $tenant = $pair[0]; $expect = $pair[1]
        $actual = (Sql "SELECT IFNULL(schema_table_count,-1) FROM t_lingsql_engine_tenant_db_config WHERE tenant_id='$tenant';").Trim()
        Check "夹具 $tenant" ("$actual" -eq "$expect") "期望 $expect 张表，实际 $actual"
    }

    # 结论：演示租户语义字典可用（文档写 21 条，按 >=10 校验，避免新增术语时误报）
    $lex = (Sql "SELECT COUNT(*) FROM t_lingsql_engine_semantic_lexicon WHERE tenant_id='demo_sales';").Trim()
    Check '语义字典' ([int]$lex -ge 10) "count=$lex（文档写 21，按 >=10 校验）"
} else {
    Check '卖点核验表' $false '文档不存在'
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host "全部一致（$checks 项）。" -ForegroundColor Green
    exit 0
}
Write-Host "发现 $failures / $checks 项不一致 —— 请先修文档或代码。" -ForegroundColor Red
exit 1
