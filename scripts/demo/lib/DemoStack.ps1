# 演示栈进程管理：端口探测、后台拉起、日志落盘、PID 记录与停止。
# 仅服务本机演示；不修改系统服务、不写注册表、不占用 3306/6379 等常用端口。

function Get-DemoRoot {
    return (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
}

function Get-DemoRuntimeDirectory {
    $directory = Join-Path (Get-DemoRoot) '.local-private/demo'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    return $directory
}

function Test-DemoPort {
    param([int]$Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(400) -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-DemoPort {
    param([int]$Port, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DemoPort -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-DemoHttp {
    param([string]$Url, [int]$TimeoutSeconds = 180)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) { return $true }
        }
        catch {
            # 尚未就绪，继续等待
        }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

function Start-DemoProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = ''
    )
    $runtime = Get-DemoRuntimeDirectory
    $logDirectory = Join-Path $runtime 'logs'
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    $stdout = Join-Path $logDirectory "$Name.out.log"
    $stderr = Join-Path $logDirectory "$Name.err.log"
    $parameters = @{
        FilePath               = $FilePath
        ArgumentList           = $Arguments
        RedirectStandardOutput = $stdout
        RedirectStandardError  = $stderr
        PassThru               = $true
        WindowStyle            = 'Hidden'
    }
    if ($WorkingDirectory -ne '') { $parameters['WorkingDirectory'] = $WorkingDirectory }
    $process = Start-Process @parameters
    $pidDirectory = Join-Path $runtime 'pids'
    New-Item -ItemType Directory -Force -Path $pidDirectory | Out-Null
    Set-Content -LiteralPath (Join-Path $pidDirectory "$Name.pid") -Value $process.Id -Encoding ascii
    return $process
}

function Stop-DemoProcess {
    param([Parameter(Mandatory = $true)][string]$Name)
    $runtime = Get-DemoRuntimeDirectory
    $pidFile = Join-Path $runtime "pids/$Name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) { return }
    $processId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        Write-Host "      已停止 $Name (pid $processId)"
    }
    Remove-Item -LiteralPath $pidFile -ErrorAction SilentlyContinue
}
