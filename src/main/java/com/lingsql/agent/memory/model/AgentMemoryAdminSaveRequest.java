package com.lingsql.agent.memory.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端写入长期记忆请求。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@Data
public class AgentMemoryAdminSaveRequest {

    /**
     * 租户标识，非系统管理员时仅允许本租户。
     */
    private String tenantId;

    /**
     * 用户标识，程序记忆必须为空。
     */
    private Long userId;

    /**
     * 记忆层级：PROCEDURAL 程序记忆 / PERMANENT 永久记忆。
     */
    @NotBlank(message = "记忆层级不能为空")
    private String tier;

    /**
     * 记忆正文。
     */
    @NotBlank(message = "记忆内容不能为空")
    @Size(max = 500, message = "记忆内容长度不能超过 500 个字符")
    private String factText;

    /**
     * 保留天数，为空使用层级默认值。
     */
    @Min(value = 1, message = "保留天数不能小于 1")
    @Max(value = 3650, message = "保留天数不能大于 3650")
    private Integer retentionDays;
}
