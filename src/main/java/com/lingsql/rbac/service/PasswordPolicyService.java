package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 密码策略服务。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Service
public class PasswordPolicyService {

    /**
     * 校验密码强度。
     *
     * @param password 密码
     */
    public void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码不能为空");
        }
        String trimmed = password.trim();
        if (trimmed.length() < 8 || trimmed.length() > 32) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码长度需在 8-32 位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码必须包含字母和数字");
        }
    }
}
