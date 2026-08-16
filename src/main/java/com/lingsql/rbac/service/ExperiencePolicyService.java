package com.lingsql.rbac.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.rbac.entity.RbacUserExperiencePolicy;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.authorization.AuthorizationChange;
import com.lingsql.rbac.authorization.AuthorizationChangeKind;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import com.lingsql.rbac.model.ExperiencePolicyRequest;
import com.lingsql.rbac.model.ExperiencePolicyView;
import com.lingsql.rbac.repository.RbacUserExperiencePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 体验账号策略服务。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Service
@RequiredArgsConstructor
public class ExperiencePolicyService {

    private final RbacUserExperiencePolicyRepository repository;
    private final ObjectMapper objectMapper;
    private final IAuthorizationSessionLifecycle authorizationSessionLifecycle;

    /**
     * 查询体验账号策略。
     *
     * @param userId 用户ID
     * @return 策略视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    public ExperiencePolicyView getPolicy(Long userId) {
        ExperiencePolicyView view = new ExperiencePolicyView();
        view.setUserId(userId);
        Optional<RbacUserExperiencePolicy> policyOpt = repository.findByUserId(userId);
        if (policyOpt.isEmpty()) {
            return view;
        }
        RbacUserExperiencePolicy policy = policyOpt.get();
        view.setStatus(policy.getStatus());
        view.setMenuWhitelist(parseWhitelist(policy.getMenuWhitelist()));
        view.setDataScopeOverride(parseDataScope(policy.getDataScopeOverride()));
        return view;
    }

    /**
     * 保存体验账号策略。
     *
     * @param userId  用户ID
     * @param request 请求
     * @return 策略视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    public ExperiencePolicyView savePolicy(Long userId, ExperiencePolicyRequest request) {
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.user(userId, AuthorizationChangeKind.EXPERIENCE_POLICY),
                () -> savePolicyInsidePublication(userId, request)
        );
    }

    /**
     * 在授权发布事务内保存体验账号策略。
     *
     * @author 余炘洋
     * @date 2026-08-16
     */
    private ExperiencePolicyView savePolicyInsidePublication(Long userId, ExperiencePolicyRequest request) {
        RbacUserExperiencePolicy policy = repository.findByUserId(userId)
                .orElseGet(RbacUserExperiencePolicy::new);
        policy.setUserId(userId);
        policy.setMenuWhitelist(writeJson(request.getMenuWhitelist()));
        policy.setDataScopeOverride(writeJson(request.getDataScopeOverride()));
        if (request.getEnabled() != null) {
            policy.setStatus(request.getEnabled() ? 1 : 0);
        } else if (policy.getStatus() == null) {
            policy.setStatus(1);
        }
        repository.save(policy);
        return getPolicy(userId);
    }

    /**
     * 解析菜单白名单。
     *
     * @param json JSON
     * @return 白名单
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> parseWhitelist(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 解析数据权限覆盖。
     *
     * @param json JSON
     * @return 数据权限
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule parseDataScope(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DataScopeRule.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 写入 JSON。
     *
     * @param value 对象
     * @return JSON
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "体验账号策略序列化失败", ex);
        }
    }
}
