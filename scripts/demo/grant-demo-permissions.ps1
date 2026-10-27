<#
.SYNOPSIS
    给演示租户补齐权限池与角色权限，让管理端菜单与页面可用。

.DESCRIPTION
    背景（2026-10-27）：第一次真正用浏览器看界面时发现，demo_admin 登录后侧边栏只有 3 项——
    因为权限 = 「角色权限」∩「租户权限池」，而两者都只预置了 13 条（只有问答与组织管理）。
    仪表盘、数字员工、语义字典、计费、审计等页面因此既看不到、也调不通。

    本脚本把演示租户的权限池与其 TENANT_ADMIN 角色都补到「全部权限」，幂等可重复执行。

    注意：这是**演示环境**的便利设置，不是产品默认行为。真实客户应按最小权限发放。

.PARAMETER TenantId
    演示租户，默认 demo_sales。

.EXAMPLE
    pwsh -File scripts/demo/grant-demo-permissions.ps1
#>
[CmdletBinding()]
param(
    [string]$TenantId = 'demo_sales',
    [int]$MysqlPort = 3407
)

$ErrorActionPreference = 'Stop'
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'

function Sql {
    param([string]$Statement)
    return (& $mysql --host=127.0.0.1 "--port=$MysqlPort" --user=root --batch --skip-column-names `
            --database=lingsql_demo_control "--execute=$Statement" 2>&1)
}

Write-Host ''
Write-Host "==== 补齐演示租户权限（$TenantId） ====" -ForegroundColor Cyan

# 1) 租户权限池
Sql "INSERT INTO t_lingsql_engine_tenant_permission (tenant_id, perm_id, create_time)
     SELECT '$TenantId', p.id, NOW() FROM t_lingsql_engine_permission p
     WHERE NOT EXISTS (SELECT 1 FROM t_lingsql_engine_tenant_permission tp
                       WHERE tp.tenant_id='$TenantId' AND tp.perm_id=p.id);" | Out-Null
$pool = (Sql "SELECT COUNT(*) FROM t_lingsql_engine_tenant_permission WHERE tenant_id='$TenantId';").Trim()
Write-Host "  租户权限池 = $pool" -ForegroundColor Green

# 2) 该租户下用户所属角色的权限（角色权限是权限池的上限，缺了同样看不到菜单）
$roleIds = @(Sql "SELECT DISTINCT ur.role_id FROM t_lingsql_engine_user_role ur
                  JOIN t_lingsql_engine_user u ON u.id = ur.user_id
                  WHERE u.tenant_id = '$TenantId';" | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\d+$' })
if ($roleIds.Count -eq 0) {
    Write-Host '  未找到该租户的角色，跳过角色授权' -ForegroundColor Yellow
    exit 1
}
foreach ($rid in $roleIds) {
    Sql "INSERT INTO t_lingsql_engine_role_permission (role_id, perm_id, create_time)
         SELECT $rid, p.id, NOW() FROM t_lingsql_engine_permission p
         WHERE NOT EXISTS (SELECT 1 FROM t_lingsql_engine_role_permission rp
                           WHERE rp.role_id=$rid AND rp.perm_id=p.id);" | Out-Null
    $cnt = (Sql "SELECT COUNT(*) FROM t_lingsql_engine_role_permission WHERE role_id=$rid;").Trim()
    Write-Host "  角色 $rid 权限数 = $cnt" -ForegroundColor Green
}

Write-Host ''
Write-Host '完成。重新登录后菜单应显示全部管理页（权限在登录时解析，需重新登录）。' -ForegroundColor Cyan
exit 0
