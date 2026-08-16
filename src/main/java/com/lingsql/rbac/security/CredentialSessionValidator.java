package com.lingsql.rbac.security;

import com.lingsql.rbac.authorization.AuthorizationDecision;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.model.PasswordState;
import com.lingsql.rbac.repository.RbacUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 逐请求复核数据库中的凭据权威状态，Redis 会话不能单独决定授权。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialSessionValidator {

    private final RbacUserRepository userRepository;
    private final IAuthorizationSessionLifecycle authorizationSessionLifecycle;

    /**
     * 校验会话用途、凭据版本和用户状态。
     *
     * @param session Redis 会话
     * @return 是否仍可用于当前用途
     */
    public boolean isValid(RbacUserSession session) {
        if (session == null || session.getUserId() == null || session.getPurpose() == null
                || session.getCredentialVersion() == null || session.getCredentialVersion() < 1L) {
            return false;
        }
        try {
            RbacUser user = userRepository.findById(session.getUserId()).orElse(null);
            if (user == null || !Objects.equals(user.getTenantId(), session.getTenantId())
                    || user.getStatus() == null || user.getStatus() != 1
                    || user.getCredentialVersion() == null
                    || !user.getCredentialVersion().equals(session.getCredentialVersion())) {
                return false;
            }
            PasswordState passwordState = user.getPasswordState() == null
                    ? PasswordState.ACTIVE : user.getPasswordState();
            boolean credentialValid = switch (session.getPurpose()) {
                case FULL_ACCESS -> passwordState == PasswordState.ACTIVE;
                case PASSWORD_CHANGE_ONLY -> passwordState == PasswordState.CHANGE_REQUIRED;
            };
            if (!credentialValid) {
                return false;
            }
            AuthorizationDecision authorizationDecision = authorizationSessionLifecycle.validate(
                    session.getUserId(),
                    session.getTenantId(),
                    session.getAuthorizationStamp()
            );
            return authorizationDecision != null && authorizationDecision.permitted();
        } catch (RuntimeException ex) {
            log.error(
                    "凭据权威状态复核失败，拒绝会话: tenantId={}, userId={}, credentialVersion={}",
                    session.getTenantId(),
                    session.getUserId(),
                    session.getCredentialVersion(),
                    ex
            );
            return false;
        }
    }
}
