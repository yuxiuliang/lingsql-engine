# 以文件重定向方式执行 MySQL 脚本，避免 PowerShell 管道对 UTF-8 中文注释做二次编码。
# 仅由演示脚本调用，不参与生产部署。

function Invoke-MysqlScript {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$MysqlBin,
        [Parameter(Mandatory = $true)][string]$HostName,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$User,
        [string]$Password = '',
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$ScriptPath
    )

    if (-not (Test-Path -LiteralPath $ScriptPath)) {
        throw "SQL 脚本不存在: $ScriptPath"
    }

    $arguments = @(
        "--host=$HostName",
        "--port=$Port",
        "--user=$User",
        '--default-character-set=utf8mb4',
        '--show-warnings',
        "--database=$Database"
    )
    if ($Password -ne '') {
        $arguments += "--password=$Password"
    }

    $stdoutFile = [System.IO.Path]::GetTempFileName()
    $stderrFile = [System.IO.Path]::GetTempFileName()
    try {
        $process = Start-Process -FilePath $MysqlBin -ArgumentList $arguments `
            -RedirectStandardInput $ScriptPath `
            -RedirectStandardOutput $stdoutFile `
            -RedirectStandardError $stderrFile `
            -NoNewWindow -Wait -PassThru
        $errorText = Get-Content -LiteralPath $stderrFile -Raw -ErrorAction SilentlyContinue
        if ($process.ExitCode -ne 0) {
            throw "执行 $([System.IO.Path]::GetFileName($ScriptPath)) 失败（exit=$($process.ExitCode)）：$errorText"
        }
        if ($errorText -and $errorText.Trim() -ne '') {
            Write-Verbose $errorText
        }
        return $process.ExitCode
    }
    finally {
        Remove-Item -LiteralPath $stdoutFile, $stderrFile -ErrorAction SilentlyContinue
    }
}
