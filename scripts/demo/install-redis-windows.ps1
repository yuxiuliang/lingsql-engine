# 下载并解压便携版 Redis（Windows 版 tporadowski/redis），仅解压到 .local-private/demo/redis。
# 不安装系统服务、不修改注册表、不写入 Program Files。
# 若已有 redis-server.exe 则直接跳过。

[CmdletBinding()]
param(
    [string]$Version = '5.0.14.1'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/DemoStack.ps1')

$runtime = Get-DemoRuntimeDirectory
$target = Join-Path $runtime 'redis'
$server = Join-Path $target 'redis-server.exe'
if (Test-Path -LiteralPath $server) {
    Write-Host "已存在：$server"
    exit 0
}

$zip = Join-Path $runtime 'redis.zip'
$url = "https://github.com/tporadowski/redis/releases/download/v$Version/Redis-x64-$Version.zip"
Write-Host "下载 $url"
Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 300
Write-Host '解压'
Expand-Archive -LiteralPath $zip -DestinationPath $target -Force
if (-not (Test-Path -LiteralPath $server)) {
    throw '解压后未找到 redis-server.exe'
}
Write-Host "完成：$server"
