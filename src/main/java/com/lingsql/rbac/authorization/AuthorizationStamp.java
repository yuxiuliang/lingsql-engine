package com.lingsql.rbac.authorization;

import org.springframework.util.StringUtils;

import java.io.Serializable;

/**
 * 会话签发时捕获的授权版本指纹。
 *
 * @param fingerprint SHA-256 指纹
 * @author 余炘洋
 * @date 2026-08-16
 */
public record AuthorizationStamp(String fingerprint) implements Serializable {

    public boolean isValid() {
        return StringUtils.hasText(fingerprint) && fingerprint.length() == 64;
    }
}
