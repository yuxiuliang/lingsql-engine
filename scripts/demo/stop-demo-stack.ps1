# 停止由 start-demo-stack.ps1 拉起的演示进程（应用 → 模型桩 → Redis → MySQL）。
# 只按 .local-private/demo/pids 下的 PID 文件停止自己拉起的进程，不影响系统 MySQL 服务或其它 Redis。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/DemoStack.ps1')

Write-Host '停止演示栈：'
foreach ($name in @('app', 'stub', 'redis', 'mysql')) {
    Stop-DemoProcess -Name $name
}
Write-Host '完成。'
