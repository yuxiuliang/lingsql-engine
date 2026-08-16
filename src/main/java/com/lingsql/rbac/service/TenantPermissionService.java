package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.authorization.AuthorizationChange;
import com.lingsql.rbac.authorization.AuthorizationChangeKind;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacTenantPermission;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户权限池服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
public class TenantPermissionService {

    private final RbacTenantPermissionRepository tenantPermissionRepository;
    private final RbacPermissionRepository permissionRepository;
    private final IAuthorizationSessionLifecycle authorizationSessionLifecycle;

    /**
     * 获取租户权限ID列表。
     *
     * @param tenantId 租户ID
     * @return 权限ID列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public List<Long> listTenantPermIds(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        return tenantPermissionRepository.findPermIdsByTenantId(tenantId.trim());
    }

    /**
     * 获取租户权限列表。
     *
     * @param tenantId 租户ID
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public List<RbacPermission> listTenantPermissions(String tenantId) {
        List<Long> permIds = listTenantPermIds(tenantId);
        if (CollectionUtils.isEmpty(permIds)) {
            return List.of();
        }
        return permissionRepository.findByIdIn(permIds);
    }

    /**
     * 更新租户权限池。
     *
     * @param tenantId 租户ID
     * @param permIds  权限ID列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void updateTenantPermissions(String tenantId, List<Long> permIds) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        String normalizedTenantId = tenantId.trim();
        authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(
                        normalizedTenantId,
                        AuthorizationChangeKind.TENANT_PERMISSION_POOL
                ),
                () -> {
                    replaceTenantPermissions(normalizedTenantId, permIds);
                    return null;
                }
        );
    }

    /**
     * 在授权发布事务内替换租户权限池。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    private void replaceTenantPermissions(String normalizedTenantId, List<Long> permIds) {
        tenantPermissionRepository.deleteByTenantId(normalizedTenantId);
        if (CollectionUtils.isEmpty(permIds)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RbacTenantPermission> entities = new ArrayList<>();
        for (Long permId : permIds) {
            if (permId == null) {
                continue;
            }
            RbacTenantPermission entity = new RbacTenantPermission();
            entity.setTenantId(normalizedTenantId);
            entity.setPermId(permId);
            entity.setCreateTime(now);
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            tenantPermissionRepository.saveAll(entities);
        }
    }

    /**
     * 判断权限是否在租户池内。
     *
     * @param tenantId 租户ID
     * @param permIds  权限ID列表
     * @return 是否允许
     * @author 余炘洋
     * @date 2026-01-26
     */
    public boolean isWithinTenantPool(String tenantId, List<Long> permIds) {
        if (CollectionUtils.isEmpty(permIds)) {
            return true;
        }
        List<Long> pool = listTenantPermIds(tenantId);
        if (CollectionUtils.isEmpty(pool)) {
            return false;
        }
        Set<Long> poolSet = pool.stream().filter(id -> id != null).collect(Collectors.toSet());
        return permIds.stream().filter(id -> id != null).allMatch(poolSet::contains);
    }
}
