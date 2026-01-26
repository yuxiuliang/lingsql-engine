package com.lingsql.context;

import org.springframework.util.StringUtils;

/**
 * 会话信息模型，包含租户与用户维度。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public record SessionInfo(String tenantId, Long userId) {

    /**
     * 获取组合会话 ID。
     *
     * @return 组合会话 ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    public String getCombinedSessionId() {
        if (!StringUtils.hasText(tenantId) || userId == null) {
            return null;
        }
        return tenantId + ":" + userId;
    }
}
