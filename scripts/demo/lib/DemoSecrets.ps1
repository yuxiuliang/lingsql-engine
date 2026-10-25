# 演示环境口令与端口的唯一来源。文件位于 .local-private/demo/demo-secrets.json（已被 .gitignore 排除）。
# 口令只在本机生成一次，脚本之间复用；不写入仓库、不打印明文。

function Get-DemoSecretsPath {
    $root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return (Join-Path $root '.local-private/demo/demo-secrets.json')
}

function New-RandomSecret {
    param([int]$Bytes = 24)
    $buffer = [byte[]]::new($Bytes)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return ([Convert]::ToBase64String($buffer) -replace '[^A-Za-z0-9]', 'X')
}

function Get-DemoSecrets {
    [CmdletBinding()]
    param([switch]$Force)

    $path = Get-DemoSecretsPath
    if ((Test-Path -LiteralPath $path) -and -not $Force) {
        return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
    }

    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null

    $secrets = [ordered]@{
        mysql    = [ordered]@{
            appUser        = 'lingsql_demo_app'
            appPassword    = (New-RandomSecret)
            readerUser     = 'lingsql_demo_reader'
            readerPassword = (New-RandomSecret)
        }
        redis    = [ordered]@{ password = (New-RandomSecret) }
        app      = [ordered]@{
            jwtSecret           = (New-RandomSecret -Bytes 32)
            dataEncryptionKey   = (New-RandomSecret -Bytes 36)
            adminPassword       = (New-RandomSecret -Bytes 12)
            demoAdminInitial    = (New-RandomSecret -Bytes 12)
            demoAdminFinal      = (New-RandomSecret -Bytes 12)
        }
        ports    = [ordered]@{ mysql = 3407; redis = 6380; stub = 18082; app = 8000 }
    }

    ($secrets | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $path -Encoding utf8
    Write-Host "      已生成本机演示口令：$path（请勿提交）"
    return ($secrets | ConvertTo-Json -Depth 6 | ConvertFrom-Json)
}
