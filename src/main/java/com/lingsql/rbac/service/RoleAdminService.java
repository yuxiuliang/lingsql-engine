package com.lingsql.rbac.service;

import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.model.RoleCreateRequest;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.model.RoleView;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final RbacRoleRepository roleRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;
    private final RbacPermissionRepository permissionRepository;
    private final TenantPermissionService tenantPermissionService;
    private final RbacUserRoleRepository userRoleRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户ID
     * @param sysAdmin 是否系统管理员
     * @return 角色列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public List<RoleView> listRoles(String tenantId, boolean sysAdmin) {
        List<RbacRole> roles;
        if (sysAdmin) {
            roles = StringUtils.hasText(tenantId) ? roleRepository.findByTenantId(tenantId.trim()) : roleRepository.findAll();
        } else {
            roles = roleRepository.findByTenantId(tenantId);
        }
        Map<Long, List<Long>> rolePermMap = resolveRolePermMap(roles);
        return roles.stream().map(role -> toView(role, rolePermMap.get(role.getId()))).toList();
    }

    /**
     * 创建角色。
     *
     * @param request   创建请求
     * @param tenantId  租户ID
     * @param sysAdmin  是否系统管理员
     * @return 角色信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    public RoleView createRole(RoleCreateRequest request, String tenantId, boolean sysAdmin) {
        if (request == null) {
            throw new RuntimeException("请求不能为空");
        }
        String roleCode = normalize(request.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            throw new RuntimeException("角色编码不能为空");
        }
        String resolvedTenantId = resolveTenantId(request.getTenantId(), tenantId, sysAdmin);
        boolean exists = resolvedTenantId == null
                ? roleRepository.findByTenantIdIsNullAndRoleCode(roleCode).isPresent()
                : roleRepository.findByTenantIdAndRoleCode(resolvedTenantId, roleCode).isPresent();
        if (exists) {
            throw new RuntimeException("角色编码已存在");
        }
        RbacRole role = new RbacRole();
        role.setTenantId(resolvedTenantId);
        role.setRoleCode(roleCode);
        role.setRoleName(normalize(request.getRoleName()));
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        role = roleRepository.save(role);
        if (request.getPermIds() != null) {
            applyRolePermissions(role, request.getPermIds(), sysAdmin, resolvedTenantId);
        }
        List<Long> permIds = rolePermissionRepository.findPermIdsByRoleIds(List.of(role.getId()));
        return toView(role, permIds);
    }

    /**
     * 更新角色。
     *
     * @param roleId   角色ID
     * @param request  更新请求
     * @param tenantId 租户ID
     * @param sysAdmin 是否系统管理员
     * @return 角色信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    public RoleView updateRole(Long roleId, RoleUpdateRequest request, String tenantId, boolean sysAdmin) {
        if (roleId == null) {
            throw new RuntimeException("角色ID不能为空");
        }
        RbacRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        validateRoleAccess(role, tenantId, sysAdmin);
        Integer originalStatus = role.getStatus();
        if (request != null) {
            if (StringUtils.hasText(request.getRoleName())) {
                role.setRoleName(normalize(request.getRoleName()));
            }
            if (request.getStatus() != null) {
                role.setStatus(request.getStatus());
            }
        }
        role.setUpdateTime(LocalDateTime.now());
        roleRepository.save(role);
        if (request != null && request.getPermIds() != null) {
            applyRolePermissions(role, request.getPermIds(), sysAdmin, role.getTenantId());
        }
        if (request != null && shouldInvalidateRoleSessions(request, originalStatus)) {
            revokeRoleSessions(role.getId());
        }
        List<Long> permIds = rolePermissionRepository.findPermIdsByRoleIds(List.of(role.getId()));
        return toView(role, permIds);
    }

    /**
     * 应用角色权限。
     *
     * @param role       角色
     * @param permIds    权限ID列表
     * @param sysAdmin   是否系统管理员
     * @param tenantId   租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void applyRolePermissions(RbacRole role, List<Long> permIds, boolean sysAdmin, String tenantId) {
        if (role == null) {
            return;
        }
        List<Long> resolvedPermIds = CollectionUtils.isEmpty(permIds) ? List.of() : permIds;
        if (!sysAdmin && role.getTenantId() == null) {
            throw new RuntimeException("无权限修改全局角色");
        }
        if (!sysAdmin && !tenantPermissionService.isWithinTenantPool(tenantId, resolvedPermIds)) {
            throw new RuntimeException("权限超出租户授权范围");
        }
        if (!CollectionUtils.isEmpty(resolvedPermIds)) {
            List<RbacPermission> permissions = permissionRepository.findByIdIn(resolvedPermIds);
            Set<Long> validIds = permissions.stream().map(RbacPermission::getId).collect(Collectors.toSet());
            resolvedPermIds = resolvedPermIds.stream().filter(validIds::contains).toList();
        }
        rolePermissionRepository.deleteByRoleId(role.getId());
        if (CollectionUtils.isEmpty(resolvedPermIds)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RbacRolePermission> relations = new ArrayList<>();
        for (Long permId : resolvedPermIds) {
            RbacRolePermission relation = new RbacRolePermission();
            relation.setRoleId(role.getId());
            relation.setPermId(permId);
            relation.setCreateTime(now);
            relations.add(relation);
        }
        rolePermissionRepository.saveAll(relations);
    }

    /**
     * 解析角色权限映射。
     *
     * @param roles 角色列表
     * @return 权限映射
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Map<Long, List<Long>> resolveRolePermMap(List<RbacRole> roles) {
        if (CollectionUtils.isEmpty(roles)) {
            return Map.of();
        }
        List<Long> roleIds = roles.stream().map(RbacRole::getId).filter(id -> id != null).toList();
        if (CollectionUtils.isEmpty(roleIds)) {
            return Map.of();
        }
        List<RbacRolePermission> relations = rolePermissionRepository.findByRoleIdIn(roleIds);
        Map<Long, List<Long>> mapping = new HashMap<>();
        for (RbacRolePermission relation : relations) {
            mapping.computeIfAbsent(relation.getRoleId(), key -> new ArrayList<>()).add(relation.getPermId());
        }
        return mapping;
    }

    /**
     * 判断是否需要失效角色绑定用户的会话。
     *
     * @param request        更新请求
     * @param originalStatus 原状态
     * @return 是否需要失效
     * @author 余炘洋
     * @date 2026-01-28
     */
    private boolean shouldInvalidateRoleSessions(RoleUpdateRequest request, Integer originalStatus) {
        if (request == null) {
            return false;
        }
        boolean permChanged = request.getPermIds() != null;
        boolean statusChanged = request.getStatus() != null && !request.getStatus().equals(originalStatus);
        return permChanged || statusChanged;
    }

    /**
     * 失效角色绑定用户的会话。
     *
     * @param roleId 角色ID
     * @author 余炘洋
     * @date 2026-01-28
     */
    private void revokeRoleSessions(Long roleId) {
        if (roleId == null) {
            return;
        }
        List<Long> userIds = userRoleRepository.findUserIdsByRoleId(roleId);
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        for (Long userId : userIds) {
            tokenService.revokeUserTokens(userId);
            refreshTokenService.revokeByUserId(userId);
        }
    }

    /**
     * 校验角色访问权限。
     *
     * @param role      角色
     * @param tenantId  租户ID
     * @param sysAdmin  是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void validateRoleAccess(RbacRole role, String tenantId, boolean sysAdmin) {
        if (sysAdmin) {
            return;
        }
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(role.getTenantId())) {
            throw new RuntimeException("无权限访问");
        }
        if (!tenantId.trim().equals(role.getTenantId().trim())) {
            throw new RuntimeException("无权限访问");
        }
    }

    /**
     * 解析租户ID。
     *
     * @param requestTenant 请求租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveTenantId(String requestTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            return StringUtils.hasText(requestTenant) ? requestTenant.trim() : sessionTenant;
        }
        if (!StringUtils.hasText(sessionTenant)) {
            throw new RuntimeException("租户信息缺失");
        }
        return sessionTenant.trim();
    }

    /**
     * 转换视图。
     *
     * @param role    角色
     * @param permIds 权限ID列表
     * @return 视图对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private RoleView toView(RbacRole role, List<Long> permIds) {
        RoleView view = new RoleView();
        view.setId(role.getId());
        view.setTenantId(role.getTenantId());
        view.setRoleCode(role.getRoleCode());
        view.setRoleName(role.getRoleName());
        view.setStatus(role.getStatus());
        view.setPermIds(permIds == null ? List.of() : permIds);
        return view;
    }

    /**
     * 规范化字符串。
     *
     * @param value 输入值
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
