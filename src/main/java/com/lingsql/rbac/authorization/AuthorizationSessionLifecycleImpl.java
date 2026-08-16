package com.lingsql.rbac.authorization;

import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 授权会话生命周期深模块实现。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AuthorizationSessionLifecycleImpl implements IAuthorizationSessionLifecycle {

    private static final int ACTIVE_STATUS = 1;
    private static final int NOT_DELETED = 0;

    private final AuthorizationVersionStore versionStore;
    private final RbacUserRepository userRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final TenantRepository tenantRepository;
    private final IAuthorizationSessionAccelerator sessionAccelerator;

    @Override
    @Transactional
    public <T> T publish(AuthorizationChange change, Supplier<T> mutation) {
        Objects.requireNonNull(change, "授权变更不能为空");
        Objects.requireNonNull(mutation, "授权事实写操作不能为空");
        T result = mutation.get();
        versionStore.bumpRequired(change.targetType(), change.targetId());
        registerAccelerationAfterCommit(change);
        return result;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuthorizationStamp capture(Long userId, String tenantId) {
        validateSubject(userId, tenantId);
        List<Long> roleIds = new ArrayList<>(userRoleRepository.findRoleIdsByUserId(userId));
        roleIds.removeIf(Objects::isNull);
        roleIds.sort(Comparator.naturalOrder());

        List<AuthorizationVersionStore.VersionKey> keys = new ArrayList<>();
        keys.add(new AuthorizationVersionStore.VersionKey(AuthorizationTargetType.USER, String.valueOf(userId)));
        keys.add(new AuthorizationVersionStore.VersionKey(AuthorizationTargetType.TENANT, tenantId.trim()));
        for (Long roleId : roleIds) {
            keys.add(new AuthorizationVersionStore.VersionKey(AuthorizationTargetType.ROLE, String.valueOf(roleId)));
        }
        Map<AuthorizationVersionStore.VersionKey, Long> versions = versionStore.requireVersions(keys);
        return new AuthorizationStamp(hashCanonicalVersions(keys, versions));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuthorizationDecision validate(Long userId, String tenantId, AuthorizationStamp stamp) {
        if (stamp == null || !stamp.isValid()) {
            return AuthorizationDecision.deny("AUTHORIZATION_STAMP_MISSING");
        }
        try {
            AuthorizationStamp current = capture(userId, tenantId);
            boolean matches = MessageDigest.isEqual(
                    stamp.fingerprint().getBytes(StandardCharsets.US_ASCII),
                    current.fingerprint().getBytes(StandardCharsets.US_ASCII)
            );
            return matches
                    ? AuthorizationDecision.permit()
                    : AuthorizationDecision.deny("AUTHORIZATION_VERSION_CHANGED");
        } catch (RuntimeException ex) {
            log.error("授权会话权威复核失败，拒绝会话: tenantId={}, userId={}", tenantId, userId, ex);
            return AuthorizationDecision.deny("AUTHORIZATION_STATE_UNAVAILABLE");
        }
    }

    private void validateSubject(Long userId, String tenantId) {
        if (userId == null || !StringUtils.hasText(tenantId)) {
            throw new AuthorizationStateUnavailableException("授权主体不能为空");
        }
        String normalizedTenantId = tenantId.trim();
        RbacUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationStateUnavailableException("授权用户不存在"));
        if (!normalizedTenantId.equals(user.getTenantId())
                || user.getStatus() == null || user.getStatus() != ACTIVE_STATUS) {
            throw new AuthorizationStateUnavailableException("授权用户状态不可用");
        }
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeleted(normalizedTenantId, NOT_DELETED)
                .orElseThrow(() -> new AuthorizationStateUnavailableException("授权租户不存在"));
        if (tenant.getStatus() == null || tenant.getStatus() != ACTIVE_STATUS) {
            throw new AuthorizationStateUnavailableException("授权租户状态不可用");
        }
        if (tenant.getExpireTime() != null && !tenant.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new AuthorizationStateUnavailableException("授权租户已过期");
        }
    }

    private String hashCanonicalVersions(List<AuthorizationVersionStore.VersionKey> keys,
                                         Map<AuthorizationVersionStore.VersionKey, Long> versions) {
        StringBuilder canonical = new StringBuilder();
        for (AuthorizationVersionStore.VersionKey key : keys) {
            canonical.append(key.targetType().name())
                    .append('|')
                    .append(key.targetId())
                    .append('|')
                    .append(versions.get(key))
                    .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new AuthorizationStateUnavailableException("运行环境不支持授权指纹算法", ex);
        }
    }

    private void registerAccelerationAfterCommit(AuthorizationChange change) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("授权变更必须在事务中执行");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sessionAccelerator.expire(change);
                } catch (RuntimeException ex) {
                    log.error(
                            "授权会话 Redis 加速失效失败: targetType={}, targetId={}, kind={}",
                            change.targetType(),
                            change.targetId(),
                            change.kind(),
                            ex
                    );
                }
            }
        });
    }
}
