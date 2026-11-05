# 初始化 Windows 本机演示所需的两个数据库：
#   1) 控制库：统一应用表结构（按 Flyway 迁移文件的字典序依次执行）；
#   2) 演示业务库：销售＋库存虚构数据，只授权给只读账号。
# 该脚本只服务本地演示，不替代生产迁移流程（生产仍以 docker compose 的 Flyway 任务为准）。

[CmdletBinding()]
param(
    [string]$MysqlBin = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 3407,
    [string]$RootUser = 'root',
    [string]$RootPassword = '',
    [string]$ControlDatabase = 'lingsql_demo_control',
    [string]$SalesDatabase = 'lingsql_demo_sales',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/Invoke-MysqlScript.ps1')
. (Join-Path $PSScriptRoot 'lib/DemoSecrets.ps1')

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Invoke-MysqlQuery {
    param([string]$Sql, [string]$Database = '')
    $arguments = @("--host=$HostName", "--port=$Port", "--user=$RootUser", '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names')
    if ($RootPassword -ne '') { $arguments += "--password=$RootPassword" }
    if ($Database -ne '') { $arguments += "--database=$Database" }
    $arguments += @('--execute', $Sql)
    $output = & $MysqlBin @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL 查询失败: $output"
    }
    $clean = @($output | Where-Object { "$_" -notmatch '^mysql: \[Warning\]' } | ForEach-Object { "$_".Trim() })
    if ($clean.Count -eq 1) { return $clean[0] }
    return $clean
}

$secrets = Get-DemoSecrets -Force:$Force

Write-Host "[1/7] 创建演示数据库 $ControlDatabase / $SalesDatabase"
Invoke-MysqlQuery -Sql "CREATE DATABASE IF NOT EXISTS ``$ControlDatabase`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; CREATE DATABASE IF NOT EXISTS ``$SalesDatabase`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" | Out-Null

Write-Host '[2/7] 执行控制库迁移（B 基线在前，V 补丁按版本升序）'
$migrationDirectory = Join-Path $root 'src/main/resources/db/migration'
$migrations = Get-ChildItem -LiteralPath $migrationDirectory -File |
    Sort-Object -Property Name |
    ForEach-Object {
        $version = if ($_.Name -match '^[BV](\d{8})_(\d+)') { "$($Matches[1])_$($Matches[2])" } else { $_.Name }
        [pscustomobject]@{ Order = $version; File = $_ }
    } |
    Sort-Object -Property Order

$existingTables = Invoke-MysqlQuery -Database $ControlDatabase -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$ControlDatabase'"
if ([int]$existingTables -gt 0 -and -not $Force) {
    Write-Host "      控制库已有 $existingTables 张表，跳过迁移（需要重跑请加 -Force）"
}
else {
    foreach ($migration in $migrations) {
        Invoke-MysqlScript -MysqlBin $MysqlBin -HostName $HostName -Port $Port -User $RootUser `
            -Password $RootPassword -Database $ControlDatabase -ScriptPath $migration.File.FullName | Out-Null
        Write-Host "      OK $($migration.File.Name)"
    }
}

Write-Host '[3/7] 导入商业基线种子（可售套餐 + 数字员工模板）'
Invoke-MysqlScript -MysqlBin $MysqlBin -HostName $HostName -Port $Port -User $RootUser `
    -Password $RootPassword -Database $ControlDatabase `
    -ScriptPath (Join-Path $root 'sql/seed_commercial_baseline.sql') | Out-Null

Write-Host '[4/7] 创建演示表结构'
Invoke-MysqlScript -MysqlBin $MysqlBin -HostName $HostName -Port $Port -User $RootUser `
    -Password $RootPassword -Database $SalesDatabase `
    -ScriptPath (Join-Path $root 'sql/demo_sales_inventory.sql') | Out-Null

Write-Host '[5/7] 生成并导入确定性演示数据'
$generatedDirectory = Join-Path $root '.local-private/demo/generated'
New-Item -ItemType Directory -Force -Path $generatedDirectory | Out-Null
$generatedSeed = Join-Path $generatedDirectory 'seed-demo-data.sql'
$generator = Join-Path $root 'scripts/demo/seed/generate-demo-data.mjs'
$seedText = & node $generator
if ($LASTEXITCODE -ne 0) { throw '演示数据生成失败' }
$seedText | Set-Content -LiteralPath $generatedSeed -Encoding utf8
Invoke-MysqlScript -MysqlBin $MysqlBin -HostName $HostName -Port $Port -User $RootUser `
    -Password $RootPassword -Database $SalesDatabase -ScriptPath $generatedSeed | Out-Null

# 语义字典：让模型知道「销售额／毛利／安全库存」等业务词对应哪张表哪个字段。
# 缺了这份数据，演示租户的语义层就是空的，模型只能靠列名猜。
Write-Host '      导入演示租户语义字典'
Invoke-MysqlScript -MysqlBin $MysqlBin -HostName $HostName -Port $Port -User $RootUser `
    -Password $RootPassword -Database $ControlDatabase `
    -ScriptPath (Join-Path $root 'sql/demo_semantic_lexicon.sql') | Out-Null

Write-Host '[6/7] 创建应用账号与只读演示账号'
$appUser = $secrets.mysql.appUser
$readerUser = $secrets.mysql.readerUser
$sql = @"
CREATE USER IF NOT EXISTS '$appUser'@'%' IDENTIFIED BY '$($secrets.mysql.appPassword)';
ALTER USER '$appUser'@'%' IDENTIFIED BY '$($secrets.mysql.appPassword)';
GRANT ALL PRIVILEGES ON ``$ControlDatabase``.* TO '$appUser'@'%';
CREATE USER IF NOT EXISTS '$readerUser'@'%' IDENTIFIED BY '$($secrets.mysql.readerPassword)';
ALTER USER '$readerUser'@'%' IDENTIFIED BY '$($secrets.mysql.readerPassword)';
GRANT SELECT ON ``$SalesDatabase``.* TO '$readerUser'@'%';
FLUSH PRIVILEGES;
"@
Invoke-MysqlQuery -Sql $sql | Out-Null

Write-Host '[7/7] 校验'
$controlTables = Invoke-MysqlQuery -Database $ControlDatabase -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$ControlDatabase'"
$orderCount = Invoke-MysqlQuery -Database $SalesDatabase -Sql 'SELECT COUNT(*) FROM demo_sales_order'
Write-Host "      控制库表数量: $controlTables"
Write-Host "      演示订单数量: $orderCount"
Write-Host "      只读账号: $readerUser / 应用账号: $appUser（口令见 .local-private/demo/demo-secrets.json）"

