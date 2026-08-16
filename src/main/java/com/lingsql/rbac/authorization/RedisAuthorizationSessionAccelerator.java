package com.lingsql.rbac.authorization;

import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.rbac.service.RefreshTokenService;
import com.lingsql.rbac.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的授权会话失效加速器。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Component
@RequiredArgsConstructor
class RedisAuthorizationSessionAccelerator implements IAuthorizationSessionAccelerator {

    private final RbacUserRepository userRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public void expire(AuthorizationChange change) {
        for (Long userId : resolveAffectedUserIds(change)) {
            tokenService.revokeUserTokens(userId);
            refreshTokenService.revokeByUserId(userId);
        }
    }

    private Set<Long> resolveAffectedUserIds(AuthorizationChange change) {
        return switch (change.targetType()) {
            case USER -> Set.of(parseLongTarget(change));
            case ROLE -> new LinkedHashSet<>(userRoleRepository.findUserIdsByRoleId(parseLongTarget(change)));
            case TENANT -> collectUserIds(userRepository.findByTenantIdOrderByIdAsc(change.targetId()));
        };
    }

    private Long parseLongTarget(AuthorizationChange change) {
        try {
            return Long.valueOf(change.targetId());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("授权变更目标ID格式错误", ex);
        }
    }

    private Set<Long> collectUserIds(List<RbacUser> users) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (users == null) {
            return userIds;
        }
        for (RbacUser user : users) {
            if (user != null && user.getId() != null) {
                userIds.add(user.getId());
            }
        }
        return userIds;
    }
}
