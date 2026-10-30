<#
.SYNOPSIS
    检查脚本的自检：证明这些"检查"真的会红，而不是永远绿。

.DESCRIPTION
    背景（2026-10-31）：本仓库的检查脚本已经不少，但"它会红吗"这件事一直是靠人工一次性证明的
    （每次临时造一条坏数据，看检查报不报，再删掉）。人会忘，这个性质也就丢了。

    本脚本把那些**可以安全制造、且能可靠清除**的故障固化成用例：
    ① 审计留痕：插入一条"成功但没记 SQL"的提问 → 期望 audit-trail-check 失败；
    ② 计费与调用一致：插入一条"成功但没有任何调用"的提问 → 期望 billing 检查失败。

    每个用例都在 try/finally 里插入并清理探针记录，失败也不会留下脏数据。

    不能自动化的检查（需要改权限、改构建、停进程的）不在此列——那些仍靠人工证明，
    见《验证证据汇总》里"哪些检查真的红过"一节。

.EXAMPLE
    pwsh -File scripts/demo/checks-self-test.ps1
#>
[CmdletBinding()]
param(
    [int]$MysqlPort = 3407,
    [string]$Database = 'lingsql_demo_control'
)

$ErrorActionPreference = 'Stop'
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$failures = 0

function Sql {
    param([string]$Statement)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            "--database=$Database" "--execute=$Statement" 2>&1)
}

function Run-Check {
    param([string]$Script)
    $out = pwsh -NoProfile -File (Join-Path $root $Script) 2>&1
    return @{ Code = $LASTEXITCODE; Output = ($out -join "`n") }
}

function Assert-Green {
    param([string]$Name, [string]$Script)
    $r = Run-Check $Script
    if ($r.Code -eq 0) { Write-Host ("[PASS] {0,-34} 正常状态下通过" -f $Name) -ForegroundColor Green }
    else { $script:failures++; Write-Host ("[FAIL] {0,-34} 正常状态却失败了" -f $Name) -ForegroundColor Red; Write-Host $r.Output -ForegroundColor DarkGray }
}

function Assert-Red {
    param([string]$Name, [string]$Script)
    $r = Run-Check $Script
    if ($r.Code -ne 0) { Write-Host ("[PASS] {0,-34} 注入故障后确实报错" -f $Name) -ForegroundColor Green }
    else { $script:failures++; Write-Host ("[FAIL] {0,-34} 注入故障后仍然通过——这条检查是恒真的" -f $Name) -ForegroundColor Red }
}

Write-Host ''
Write-Host '检查脚本自检：确认它们会红' -ForegroundColor Cyan
Write-Host ''

# ① 审计留痕
$probeAudit = 'SELFTEST-AUDIT-0001'
try {
    Assert-Green '审计留痕（正常）' 'scripts/demo/audit-trail-check.ps1'
    # 注意：探针**不能**带 executed_sql——审计检查的不变量正是"成功就必须有 SQL"，
    # 带上就等于没注入故障（第一版就踩了这个坑，被自检本身抓出来）。
    Sql "INSERT INTO t_lingsql_engine_sql_execute_log (request_id, tenant_id, user_id, question, success, create_time)
         VALUES ('$probeAudit','demo_sales',1,'自检探针：成功但没记 SQL',1,NOW());" | Out-Null
    Assert-Red '审计留痕（注入故障）' 'scripts/demo/audit-trail-check.ps1'
}
finally {
    Sql "DELETE FROM t_lingsql_engine_sql_execute_log WHERE request_id='$probeAudit';" | Out-Null
}

# ② 计费与调用一致
$probeBill = 'SELFTEST-BILL-0001'
try {
    Assert-Green '计费一致（正常）' 'scripts/demo/billing-call-consistency-check.ps1'
    Sql "INSERT INTO t_lingsql_engine_sql_execute_log (request_id, tenant_id, user_id, question, success, executed_sql, create_time)
         VALUES ('$probeBill','demo_sales',1,'自检探针：成功但无任何调用',1,'SELECT 1',NOW());" | Out-Null
    Assert-Red '计费一致（注入故障）' 'scripts/demo/billing-call-consistency-check.ps1'
}
finally {
    Sql "DELETE FROM t_lingsql_engine_sql_execute_log WHERE request_id='$probeBill';" | Out-Null
}

# 清理后确认恢复正常
Assert-Green '审计留痕（清理后）' 'scripts/demo/audit-trail-check.ps1'
Assert-Green '计费一致（清理后）' 'scripts/demo/billing-call-consistency-check.ps1'

# ③ 界面路由可用：往前端路由里塞一条后端没放行的路径 → 期望 ui-routes-check 失败
$routerFile = Join-Path $root 'web_src/src/router/index.ts'
$routerBackup = Get-Content -LiteralPath $routerFile -Raw
$routerHash = (Get-FileHash $routerFile -Algorithm SHA256).Hash
try {
    Assert-Green '界面路由（正常）' 'scripts/demo/ui-routes-check.ps1'
    $probeRoute = $routerBackup -replace "(path: '/login',)", "path: '/zz-selftest-route',`n  },`n  {`n    `$1"
    Set-Content -LiteralPath $routerFile -Value $probeRoute -Encoding utf8 -NoNewline
    Assert-Red '界面路由（注入故障）' 'scripts/demo/ui-routes-check.ps1'
}
finally {
    Set-Content -LiteralPath $routerFile -Value $routerBackup -Encoding utf8 -NoNewline
}

# ④ 前后端契约：新增一个引用不存在接口的前端文件 → 期望 api-contract-check 失败
$probeTs = Join-Path $root 'web_src/src/__selftest.ts'
try {
    Assert-Green '前后端契约（正常）' 'scripts/demo/api-contract-check.ps1'
    Set-Content -LiteralPath $probeTs -Value "export const url = '/api/v1/admin/zz-selftest-not-exists'" -Encoding utf8
    Assert-Red '前后端契约（注入故障）' 'scripts/demo/api-contract-check.ps1'
}
finally {
    Remove-Item -LiteralPath $probeTs -Force -ErrorAction SilentlyContinue
}

# ⑤ 文档步数：把手册里的"八步"改成"七步" → 期望文档一致性检查失败
$manualFile = Join-Path $root 'docs/Windows本机演示手册.md'
$manualBackup = Get-Content -LiteralPath $manualFile -Raw
$manualHash = (Get-FileHash $manualFile -Algorithm SHA256).Hash
try {
    Assert-Green '文档一致性（正常）' 'scripts/demo/docs-consistency-check.ps1'
    Set-Content -LiteralPath $manualFile -Value ($manualBackup.Replace('一键总验八步', '一键总验七步')) -Encoding utf8 -NoNewline
    Assert-Red '文档一致性（注入故障）' 'scripts/demo/docs-consistency-check.ps1'
}
finally {
    Set-Content -LiteralPath $manualFile -Value $manualBackup -Encoding utf8 -NoNewline
}

# ⑥ 构建对齐：把 jar 的修改时间改到进程启动之后 → 期望 preflight 报"构建对齐"失败
$jar = Get-ChildItem (Join-Path $root 'target') -Filter 'lingsql-engine-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'original' } | Select-Object -First 1
if ($jar) {
    $jarMtime = $jar.LastWriteTime
    try {
        Assert-Green '构建对齐（正常）' 'scripts/demo/demo-preflight.ps1'
        (Get-Item $jar.FullName).LastWriteTime = (Get-Date).AddMinutes(5)
        Assert-Red '构建对齐（时间戳被改）' 'scripts/demo/demo-preflight.ps1'
    }
    finally {
        (Get-Item $jar.FullName).LastWriteTime = $jarMtime
    }
    Write-Host '[INFO] jar 时间戳已还原' -ForegroundColor DarkGray
}

# ⑦ 文档结构：临时放一份"表格被空行截断 + 章节编号重复"的文档 → 期望文档一致性检查失败
$probeDoc = Join-Path $root '.local-private/commercial/docs/zz-selftest-structure.md'
$probeLines = @(
    '# 自检探针',
    '',
    '## 一、第一节',
    '',
    '| 列1 | 列2 |',
    '| --- | --- |',
    '| a | b |',
    '',
    '| c | d |',
    '',
    '## 一、重复的编号'
)
try {
    Set-Content -LiteralPath $probeDoc -Value $probeLines -Encoding utf8
    Assert-Red '文档结构（注入故障）' 'scripts/demo/docs-consistency-check.ps1'
}
finally {
    Remove-Item -LiteralPath $probeDoc -Force -ErrorAction SilentlyContinue
}
Assert-Green '文档结构（清理后）' 'scripts/demo/docs-consistency-check.ps1'

# 清理后确认恢复正常，并核对被改过的文件已按原样还原（哈希一致）
Assert-Green '界面路由（清理后）' 'scripts/demo/ui-routes-check.ps1'
Assert-Green '前后端契约（清理后）' 'scripts/demo/api-contract-check.ps1'
Assert-Green '文档一致性（清理后）' 'scripts/demo/docs-consistency-check.ps1'

$routerHashAfter = (Get-FileHash $routerFile -Algorithm SHA256).Hash
$manualHashAfter = (Get-FileHash $manualFile -Algorithm SHA256).Hash
if ($routerHashAfter -eq $routerHash) { Write-Host '[PASS] 路由文件已原样还原' -ForegroundColor Green }
else { $failures++; Write-Host '[FAIL] 路由文件哈希与改动前不一致！' -ForegroundColor Red }
if ($manualHashAfter -eq $manualHash) { Write-Host '[PASS] 手册文件已原样还原' -ForegroundColor Green }
else { $failures++; Write-Host '[FAIL] 手册文件哈希与改动前不一致！' -ForegroundColor Red }
if (-not (Test-Path -LiteralPath $probeTs)) { Write-Host '[PASS] 探针文件已删除' -ForegroundColor Green }
else { $failures++; Write-Host '[FAIL] 探针文件仍存在！' -ForegroundColor Red }

Write-Host ''
if ($failures -eq 0) {
    Write-Host '全部通过：这些检查确实会红，且跑完不留痕迹。' -ForegroundColor Green
    exit 0
}
Write-Host "$failures 项不符合预期。" -ForegroundColor Red
exit 1
