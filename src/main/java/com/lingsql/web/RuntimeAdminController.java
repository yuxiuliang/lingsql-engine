package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.runtime.RuntimeFingerprintService;
import com.lingsql.runtime.model.RuntimeFingerprintView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布候选运行指纹接口。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@RestController
@RequestMapping("/api/v1/admin/runtime")
@RequiredArgsConstructor
public class RuntimeAdminController {

    private final RuntimeFingerprintService runtimeFingerprintService;

    /**
     * 返回当前进程的制品、Schema、数据快照和模型路由指纹。
     *
     * @return 运行指纹
     */
    @GetMapping("/fingerprint")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<RuntimeFingerprintView> fingerprint() {
        if (!SecureTenantResolver.isSysAdmin(RbacSecurityUtils.currentSession())) {
            throw new AccessDeniedException("无权限访问");
        }
        return ApiResponse.success(runtimeFingerprintService.load());
    }
}
