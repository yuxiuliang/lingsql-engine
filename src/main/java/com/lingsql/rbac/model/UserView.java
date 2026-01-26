package com.lingsql.rbac.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class UserView {

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 账号。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String username;

    /**
     * 昵称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String nickname;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

    /**
     * 是否体验账号。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer isExperience;

    /**
     * 最近登录时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime lastLoginTime;

    /**
     * 角色ID列表。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<Long> roleIds;
}
