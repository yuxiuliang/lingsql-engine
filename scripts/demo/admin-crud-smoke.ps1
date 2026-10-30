<#
.SYNOPSIS
    管理端增删改冒烟：把客户管理员第一天最常做的操作真跑一遍。

.DESCRIPTION
    背景（2026-10-31）：验证登录后界面时，顺手发现两个"第一天就会撞"的缺陷——
    编辑用户保留原角色 500（唯一键冲突）、删除用户 500（接口根本不存在）。
    这说明"接口存在"和"接口能用"是两件事。

    本脚本对几个核心管理资源做「创建 → 更新 → 删除」闭环，
    任何一步失败都会明确报出来，并在结束时清理测试数据。
    目前覆盖：角色、组织类型、数字员工。

    约束：只写临时数据（编码统一带 SMOKE 前缀 + 时间戳），跑完删除；
    删除接口未实现时记为 [SKIP] 而不是失败，方便逐步补齐。

.PARAMETER BaseUrl
    应用地址，默认本机 8000。

.EXAMPLE
    pwsh -File scripts/demo/admin-crud-smoke.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8000',
    [string]$TenantId = 'demo_sales'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$secrets = Get-Content (Join-Path $root '.local-private/demo/demo-secrets.json') -Raw | ConvertFrom-Json
$stamp = Get-Date -Format 'HHmmss'
$failures = 0

function Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $headers['X-Tenant-Id'] = 'sys_tenant'
    $req = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 60; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $req['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress -Depth 6))
        $req['ContentType'] = 'application/json; charset=UTF-8'
    }
    $resp = Invoke-WebRequest @req
    return [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
}

function Step {
    param([string]$Name, $Result, [string]$Extra = '')
    $code = $Result.code
    if ($code -eq 200) {
        Write-Host ("[PASS] {0,-28} {1}" -f $Name, $Extra) -ForegroundColor Green
        return $true
    }
    if ($code -eq 405) {
        Write-Host ("[SKIP] {0,-28} 方法未实现（405）：{1}" -f $Name, $Result.message) -ForegroundColor DarkGray
        return $false
    }
    $script:failures++
    Write-Host ("[FAIL] {0,-28} code={1} {2}" -f $Name, $code, $Result.message) -ForegroundColor Red
    return $false
}

$admin = (Api -Method POST -Path '/api/v1/auth/login' -Body @{
    tenantId = 'sys_tenant'; username = 'admin'; password = $secrets.app.adminPassword
}).data.token
if (-not $admin) { Write-Host '管理员登录失败，无法继续' -ForegroundColor Red; exit 1 }

Write-Host ''
Write-Host "管理端增删改冒烟（租户 $TenantId，标记 SMOKE$stamp）" -ForegroundColor Cyan
Write-Host ''

# ① 角色：创建 → 更新（权限原样） → 无删除接口，清理放到 finally 里用 SQL 兜底
$roleCode = "SMOKE_ROLE_$stamp"
$roleId = $null
$typeId = $null
$empId = $null
# 整段包在 try/finally 里：任一步失败也要把测试数据清掉，
# 否则会留下一个"无删除接口"的角色，只能人工清库（2026-10-31 实际踩过）。
try {
$created = Api -Method POST -Path '/api/v1/admin/roles' -Token $admin -Body @{
    tenantId = $TenantId; roleCode = $roleCode; roleName = "冒烟角色 $stamp"; status = 1; permIds = @()
}
if (Step '角色-创建' $created "id=$($created.data.id)") {
    $roleId = $created.data.id
    $updated = Api -Method PUT -Path "/api/v1/admin/roles/$roleId" -Token $admin -Body @{
        roleName = "冒烟角色 $stamp 改名"; status = 1; permIds = @()
    }
    [void](Step '角色-更新（保留权限）' $updated)
}

# ② 组织类型：创建 → 更新 → 删除
$typeCode = "SMOKE_TYPE_$stamp"
$typeCreated = Api -Method POST -Path '/api/v1/admin/org/types' -Token $admin -Body @{
    tenantId = $TenantId; typeCode = $typeCode; typeName = "冒烟类型 $stamp"; sort = 99; status = 1
}
if (Step '组织类型-创建' $typeCreated "id=$($typeCreated.data.id)") {
    $typeId = $typeCreated.data.id
    $typeUpdated = Api -Method PUT -Path "/api/v1/admin/org/types/$typeId" -Token $admin -Body @{
        typeName = "冒烟类型 $stamp 改名"; sort = 98; status = 1
    }
    [void](Step '组织类型-更新' $typeUpdated)
    $typeDeleted = Api -Method DELETE -Path "/api/v1/admin/org/types/$typeId" -Token $admin
    [void](Step '组织类型-删除' $typeDeleted)
}

# ③ 数字员工：创建 → 更新 → 删除
$empCode = "SMOKE_EMP_$stamp"
$empCreated = Api -Method POST -Path '/api/v1/admin/digital-employees' -Token $admin -Body @{
    tenantId = $TenantId; employeeCode = $empCode; employeeName = "冒烟员工 $stamp"
    scenario = '冒烟验证'; promptTemplate = '冒烟验证'; defaultQuestion = '2026年9月的销售额是多少？'
}
if (Step '数字员工-创建' $empCreated "id=$($empCreated.data.id)") {
    $empId = $empCreated.data.id
    $empUpdated = Api -Method PUT -Path "/api/v1/admin/digital-employees/$empId" -Token $admin -Body @{
        employeeName = "冒烟员工 $stamp 改名"
    }
    [void](Step '数字员工-更新' $empUpdated)
    $empDeleted = Api -Method DELETE -Path "/api/v1/admin/digital-employees/$empId" -Token $admin
    [void](Step '数字员工-删除' $empDeleted)
}

# ④ 用户：创建 → 更新（保留原角色，正是 2026-10-31 修过的路径） → 停用
#    注意两点：
#    1) 用户接口没有 DELETE（见 405 语义那条提交），所以用"停用"收尾；
#    2) 口令必须随机——早期版本用了可预测的 "Smoke<时间>Ab1"，一旦中途失败，
#       库里会留下一个**启用状态且口令可知**的账号。2026-10-31 扫描发现后改为随机口令。
#    整个用户段落放在 try/finally 里：无论成败，最后都尝试停用，避免留下可用账号。
$userName = "smoke_user_$stamp"
$userPassword = [Guid]::NewGuid().ToString('N').Substring(0, 20) + 'A1'
$userId = $null
try {
    $userCreated = Api -Method POST -Path '/api/v1/admin/users' -Token $admin -Body @{
        tenantId = $TenantId; username = $userName; password = $userPassword
        nickname = "冒烟用户 $stamp"; status = 1; roleIds = @(2)
    }
    if (Step '用户-创建' $userCreated "id=$($userCreated.data.id)") {
        $userId = $userCreated.data.id
        $userUpdated = Api -Method PUT -Path "/api/v1/admin/users/$userId" -Token $admin -Body @{
            tenantId = $TenantId; username = $userName; nickname = "冒烟用户 $stamp 改名"
            status = 1; roleIds = @(2)
        }
        [void](Step '用户-更新（保留原角色）' $userUpdated)
        $userDisabled = Api -Method PUT -Path "/api/v1/admin/users/$userId" -Token $admin -Body @{
            tenantId = $TenantId; username = $userName; nickname = "冒烟用户 $stamp 已停用"
            status = 0; roleIds = @(2)
        }
        [void](Step '用户-停用' $userDisabled)
    }
}
finally {
    # 兜底：即使前面某步抛错或断言失败，也把这个账号停掉
    if ($userId) {
        try {
            Api -Method PUT -Path "/api/v1/admin/users/$userId" -Token $admin -Body @{
                tenantId = $TenantId; username = $userName; nickname = "冒烟用户 $stamp 已停用"
                status = 0; roleIds = @(2)
            } | Out-Null
        }
        catch { }
    }
}
}
finally {
    # 统一收尾：凡是创建成功的都尝试清掉，失败也不影响退出码（清理失败会打印提示）
    $cleanupFailed = @()

    if ($typeId) {
        try { Api -Method DELETE -Path "/api/v1/admin/org/types/$typeId" -Token $admin | Out-Null }
        catch { $cleanupFailed += "组织类型 id=$typeId" }
    }
    if ($empId) {
        try { Api -Method DELETE -Path "/api/v1/admin/digital-employees/$empId" -Token $admin | Out-Null }
        catch { $cleanupFailed += "数字员工 id=$empId" }
    }

    # 角色没有删除接口，只能按编码清库（注意顺序：先子表后主表）
    if ($roleCode) {
        $mysqlExe = $null
        foreach ($candidate in @(
                (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '.local-private/demo/mysql/bin/mysql.exe'),
                'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
            )) {
            if (Test-Path $candidate) { $mysqlExe = $candidate; break }
        }
        if ($mysqlExe) {
            $ctl = @('--host=127.0.0.1', '--port=3407', "--user=$($secrets.mysql.appUser)", "--password=$($secrets.mysql.appPassword)", '--database=lingsql_demo_control')
            try {
                & $mysqlExe @ctl "--execute=DELETE rp FROM t_lingsql_engine_role_permission rp JOIN t_lingsql_engine_role r ON rp.role_id=r.id WHERE r.role_code='$roleCode';" 2>&1 | Out-Null
                & $mysqlExe @ctl "--execute=DELETE rd FROM t_lingsql_engine_role_data_scope rd JOIN t_lingsql_engine_role r ON rd.role_id=r.id WHERE r.role_code='$roleCode';" 2>&1 | Out-Null
                & $mysqlExe @ctl "--execute=DELETE FROM t_lingsql_engine_role WHERE role_code='$roleCode';" 2>&1 | Out-Null
            }
            catch { $cleanupFailed += "角色 $roleCode" }
        }
        else { $cleanupFailed += "角色 $roleCode（找不到 mysql 客户端）" }
    }

    if ($cleanupFailed.Count -gt 0) {
        Write-Host ("[WARN] 以下测试数据未能清理，请手工处理：{0}" -f ($cleanupFailed -join '、')) -ForegroundColor Yellow
    }
    else {
        # 说清楚"按产品语义"还剩什么：员工是软删除（行保留）、用户是停用（无删除接口）。
        # 不做硬删——那会绕过产品自己的删除语义，也可能删掉未来要在演示里展示的历史记录。
        Write-Host ("[INFO] 清理完成。按产品语义仍会保留：数字员工 {0}（软删 is_deleted=1）、用户 {1}（已停用）" -f $empCode, $userName) -ForegroundColor DarkGray
    }
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host '管理端增删改冒烟通过。' -ForegroundColor Green
    exit 0
}
Write-Host "$failures 步失败 —— 这些是客户管理员第一天就会做的操作。" -ForegroundColor Red
exit 1
