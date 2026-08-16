package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.model.PasswordState;
import com.lingsql.rbac.model.RequiredPasswordChangeRequest;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 密码凭据状态变更服务。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Service
@RequiredArgsConstructor
public class PasswordCredentialService {

    private final RbacUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;

    /**
     * 在独立事务内完成首次登录改密，确保返回时数据库已提交。
     *
     * @param session 受限会话
     * @param request 改密请求
     * @return 已更新用户
     */
    @Transactional
    public RbacUser completeRequiredChange(RbacUserSession session, RequiredPasswordChangeRequest request) {
        if (session == null || session.getUserId() == null || request == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        RbacUser user = userRepository.findByIdForUpdate(session.getUserId())
                .orElseThrow(() -> new BadCredentialsException("账号不存在"));
        if (!Objects.equals(user.getTenantId(), session.getTenantId())
                || resolveCredentialVersion(user) != resolveCredentialVersion(session.getCredentialVersion())) {
            throw new BadCredentialsException("会话凭据已失效");
        }
        if (resolvePasswordState(user) != PasswordState.CHANGE_REQUIRED) {
            throw new BizException(ErrorCode.CONFLICT, "当前账号不需要首次登录改密");
        }
        if (!StringUtils.hasText(request.getCurrentPassword())
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("当前密码错误");
        }
        passwordPolicyService.validate(request.getNewPassword());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "新密码不能与当前密码相同");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordState(PasswordState.ACTIVE);
        user.setPasswordChangedAt(now);
        user.setCredentialVersion(resolveCredentialVersion(user) + 1L);
        user.setFailedLoginCount(0);
        user.setLockUntil(null);
        user.setUpdateTime(now);
        return userRepository.save(user);
    }

    private PasswordState resolvePasswordState(RbacUser user) {
        return user == null || user.getPasswordState() == null ? PasswordState.ACTIVE : user.getPasswordState();
    }

    private long resolveCredentialVersion(RbacUser user) {
        return user == null ? 1L : resolveCredentialVersion(user.getCredentialVersion());
    }

    private long resolveCredentialVersion(Long credentialVersion) {
        return credentialVersion == null || credentialVersion < 1L ? 1L : credentialVersion;
    }
}
