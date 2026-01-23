package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.ExperiencePolicyRequest;
import com.lingsql.rbac.model.ExperiencePolicyView;
import com.lingsql.rbac.service.ExperiencePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 体验账号策略管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/api/v1/rbac/experience")
@RequiredArgsConstructor
public class ExperiencePolicyController {

    private final ExperiencePolicyService experiencePolicyService;

    /**
     * 查询体验账号策略。
     *
     * @param userId 用户ID
     * @return 策略视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('RBAC:EXPERIENCE:READ')")
    public ApiResponse<ExperiencePolicyView> getPolicy(@PathVariable Long userId) {
        return ApiResponse.success(experiencePolicyService.getPolicy(userId));
    }

    /**
     * 更新体验账号策略。
     *
     * @param userId  用户ID
     * @param request 请求
     * @return 策略视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('RBAC:EXPERIENCE:WRITE')")
    public ApiResponse<ExperiencePolicyView> savePolicy(@PathVariable Long userId, @RequestBody ExperiencePolicyRequest request) {
        return ApiResponse.success(experiencePolicyService.savePolicy(userId, request));
    }
}
