package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.RoleCreateRequest;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.model.RoleView;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final RbacRoleDataScopeRepository roleDataScopeRepository;
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
        Map<Long, DataScopeRule> roleScopeMap = resolveRoleScopeMap(roles);
        return roles.stream().map(role -> toView(role, rolePermMap.get(role.getId()), roleScopeMap.get(role.getId()))).toList();
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
            throw new BizException(ErrorCode.INVALID_PARAM, "请求不能为空");
        }
        String roleCode = normalize(request.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "角色编码不能为空");
        }
        String resolvedTenantId = resolveTenantId(request.getTenantId(), tenantId, sysAdmin);
        boolean exists = resolvedTenantId == null
                ? roleRepository.findByTenantIdIsNullAndRoleCode(roleCode).isPresent()
                : roleRepository.findByTenantIdAndRoleCode(resolvedTenantId, roleCode).isPresent();
        if (exists) {
            throw new BizException(ErrorCode.CONFLICT, "角色编码已存在");
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
        if (request.getDataScope() != null) {
            applyRoleDataScope(role, request.getDataScope());
        }
        List<Long> permIds = rolePermissionRepository.findPermIdsByRoleIds(List.of(role.getId()));
        return toView(role, permIds, resolveRoleDataScope(role.getId()));
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
            throw new BizException(ErrorCode.INVALID_PARAM, "角色ID不能为空");
        }
        RbacRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BizException(ErrorCode.ROLE_NOT_FOUND, "角色不存在"));
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
        if (request != null && request.getDataScope() != null) {
            applyRoleDataScope(role, request.getDataScope());
        }
        if (request != null && shouldInvalidateRoleSessions(request, originalStatus)) {
            revokeRoleSessions(role.getId());
        }
        List<Long> permIds = rolePermissionRepository.findPermIdsByRoleIds(List.of(role.getId()));
        return toView(role, permIds, resolveRoleDataScope(role.getId()));
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
            throw new BizException(ErrorCode.FORBIDDEN, "无权限修改全局角色");
        }
        if (!sysAdmin && !tenantPermissionService.isWithinTenantPool(tenantId, resolvedPermIds)) {
            throw new BizException(ErrorCode.FORBIDDEN, "权限超出租户授权范围");
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
     * 解析角色数据权限映射。
     *
     * @param roles 角色列表
     * @return 数据权限映射
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Map<Long, DataScopeRule> resolveRoleScopeMap(List<RbacRole> roles) {
        if (CollectionUtils.isEmpty(roles)) {
            return Map.of();
        }
        List<Long> roleIds = roles.stream().map(RbacRole::getId).filter(id -> id != null).toList();
        if (CollectionUtils.isEmpty(roleIds)) {
            return Map.of();
        }
        Map<Long, DataScopeRule> mapping = new HashMap<>();
        for (RbacRoleDataScope scope : roleDataScopeRepository.findActiveByRoleIds(roleIds)) {
            if (scope.getRoleId() != null && !mapping.containsKey(scope.getRoleId())) {
                mapping.put(scope.getRoleId(), toDataScopeRule(scope));
            }
        }
        for (Long roleId : roleIds) {
            mapping.putIfAbsent(roleId, createDefaultDataScope());
        }
        return mapping;
    }

    /**
     * 查询单个角色的数据权限配置。
     *
     * @param roleId 角色ID
     * @return 数据权限规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule resolveRoleDataScope(Long roleId) {
        if (roleId == null) {
            return createDefaultDataScope();
        }
        return roleDataScopeRepository.findActiveByRoleIds(List.of(roleId)).stream()
                .findFirst()
                .map(this::toDataScopeRule)
                .orElseGet(this::createDefaultDataScope);
    }

    /**
     * 应用角色数据权限配置。
     *
     * @param role 角色
     * @param rule 数据权限规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void applyRoleDataScope(RbacRole role, DataScopeRule rule) {
        if (role == null || role.getId() == null) {
            return;
        }
        roleDataScopeRepository.deleteByRoleId(role.getId());
        DataScopeRule normalizedRule = normalizeDataScope(rule);
        if (RbacDataScopeType.TENANT.equals(normalizedRule.getScopeType())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        RbacRoleDataScope entity = new RbacRoleDataScope();
        entity.setRoleId(role.getId());
        entity.setScopeType(normalizedRule.getScopeType());
        entity.setScopeColumn(normalizedRule.getScopeColumn());
        entity.setScopeRule(resolvePersistedScopeRule(normalizedRule));
        entity.setStatus(1);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        roleDataScopeRepository.save(entity);
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
        boolean dataScopeChanged = request.getDataScope() != null;
        return permChanged || statusChanged || dataScopeChanged;
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
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        if (!tenantId.trim().equals(role.getTenantId().trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
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
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
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
    private RoleView toView(RbacRole role, List<Long> permIds, DataScopeRule dataScope) {
        RoleView view = new RoleView();
        view.setId(role.getId());
        view.setTenantId(role.getTenantId());
        view.setRoleCode(role.getRoleCode());
        view.setRoleName(role.getRoleName());
        view.setStatus(role.getStatus());
        view.setPermIds(permIds == null ? List.of() : permIds);
        view.setDataScope(dataScope == null ? createDefaultDataScope() : dataScope);
        return view;
    }

    /**
     * 转换角色数据权限实体为规则模型。
     *
     * @param entity 数据权限实体
     * @return 规则模型
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule toDataScopeRule(RbacRoleDataScope entity) {
        if (entity == null) {
            return createDefaultDataScope();
        }
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(StringUtils.hasText(entity.getScopeType())
                ? entity.getScopeType().trim().toUpperCase()
                : RbacDataScopeType.TENANT);
        rule.setScopeColumn(StringUtils.hasText(entity.getScopeColumn()) ? entity.getScopeColumn().trim() : "");
        if (isRelationScopeType(rule.getScopeType())) {
            rule.setScopeRule("");
            rule.setRelationTypes(parseRelationTypes(entity.getScopeRule()));
        } else {
            rule.setScopeRule(StringUtils.hasText(entity.getScopeRule()) ? entity.getScopeRule().trim() : "");
            rule.setRelationTypes(List.of());
        }
        return rule;
    }

    /**
     * 规范化角色数据权限规则。
     *
     * @param rule 原始规则
     * @return 规范化后的规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule normalizeDataScope(DataScopeRule rule) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return createDefaultDataScope();
        }
        String scopeType = rule.getScopeType().trim().toUpperCase();
        if (!RbacDataScopeType.ALL.equals(scopeType)
                && !RbacDataScopeType.TENANT.equals(scopeType)
                && !RbacDataScopeType.SELF.equals(scopeType)
                && !RbacDataScopeType.ORG.equals(scopeType)
                && !RbacDataScopeType.ORG_SUBTREE.equals(scopeType)
                && !RbacDataScopeType.RELATION.equals(scopeType)
                && !RbacDataScopeType.RELATION_SUBTREE.equals(scopeType)
                && !RbacDataScopeType.CUSTOM.equals(scopeType)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数据权限范围不支持");
        }
        DataScopeRule normalized = new DataScopeRule();
        normalized.setScopeType(scopeType);
        normalized.setRelationTypes(List.of());
        if (RbacDataScopeType.CUSTOM.equals(scopeType)) {
            String scopeRule = StringUtils.hasText(rule.getScopeRule()) ? rule.getScopeRule().trim() : "";
            if (!StringUtils.hasText(scopeRule)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "自定义数据权限规则不能为空");
            }
            normalized.setScopeColumn("");
            normalized.setScopeRule(scopeRule);
            return normalized;
        }
        if (isRelationScopeType(scopeType)) {
            List<String> relationTypes = normalizeRelationTypes(rule.getRelationTypes());
            if (CollectionUtils.isEmpty(relationTypes)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "关系型数据权限至少选择一个关系类型");
            }
            normalized.setScopeColumn(StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn().trim() : "");
            normalized.setScopeRule("");
            normalized.setRelationTypes(relationTypes);
            return normalized;
        }
        normalized.setScopeColumn(StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn().trim() : "");
        normalized.setScopeRule("");
        return normalized;
    }

    /**
     * 创建默认租户级数据权限。
     *
     * @return 默认规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule createDefaultDataScope() {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.TENANT);
        rule.setScopeColumn("");
        rule.setScopeRule("");
        rule.setRelationTypes(List.of());
        return rule;
    }

    /**
     * 判断是否为关系型数据权限。
     *
     * @param scopeType 范围类型
     * @return 是否关系型
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean isRelationScopeType(String scopeType) {
        return RbacDataScopeType.RELATION.equals(scopeType)
                || RbacDataScopeType.RELATION_SUBTREE.equals(scopeType);
    }

    /**
     * 规范化关系类型列表。
     *
     * @param relationTypes 原始关系类型
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> normalizeRelationTypes(List<String> relationTypes) {
        if (CollectionUtils.isEmpty(relationTypes)) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String relationType : relationTypes) {
            if (StringUtils.hasText(relationType)) {
                normalized.add(relationType.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 解析持久化后的关系类型列表。
     *
     * @param rawValue 原始配置
     * @return 关系类型列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> parseRelationTypes(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        String normalized = rawValue.replace('，', ',').replace('；', ',');
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : normalized.split(",")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * 解析持久化用的 scopeRule。
     *
     * @param rule 数据权限规则
     * @return 持久化值
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolvePersistedScopeRule(DataScopeRule rule) {
        if (rule == null) {
            return "";
        }
        if (isRelationScopeType(rule.getScopeType())) {
            return String.join(",", normalizeRelationTypes(rule.getRelationTypes()));
        }
        return rule.getScopeRule();
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
