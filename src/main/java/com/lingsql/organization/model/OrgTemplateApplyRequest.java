package com.lingsql.organization.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织模板初始化请求。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgTemplateApplyRequest {

    /**
     * 租户ID（系统管理员可指定）。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String tenantId;

    /**
     * 模板编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;
}
