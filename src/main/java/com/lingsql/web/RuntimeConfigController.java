package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.web.dto.ChatRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时配置查询接口。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class RuntimeConfigController {

    private final LingSqlProperties lingSqlProperties;

    /**
     * 查询聊天运行时配置。
     *
     * @return 配置信息
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/chat")
    @PreAuthorize("hasAuthority('CONFIG:CHAT:READ')")
    public ApiResponse<ChatRuntimeConfig> chatConfig() {
        int maxLength = lingSqlProperties.getChat().getMaxQuestionLength();
        return ApiResponse.success(new ChatRuntimeConfig(maxLength));
    }
}
