package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserDataScope;
import com.lingsql.rbac.entity.RbacUserExperiencePolicy;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserDataScopeRepository;
import com.lingsql.rbac.repository.RbacUserExperiencePolicyRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.tenant.repository.TenantRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String LOGIN_ERROR_MESSAGE = "账号、密码或租户信息错误";
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5.5dQ9K/9e2XcGQnQ8l7uP3BvI6JuaS";

    private final RbacUserRepository userRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final RbacRoleRepository roleRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;
    private final RbacPermissionRepository permissionRepository;
    private final RbacRoleDataScopeRepository roleDataScopeRepository;
    private final RbacUserDataScopeRepository userDataScopeRepository;
    private final RbacUserExperiencePolicyRepository experiencePolicyRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final TenantRepository tenantRepository;
    private final LingSqlProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    /**
     * 登录并生成 Token。
     *
     * @param request 登录请求
     * @return 登录响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    public LoginResponse login(LoginRequest request) {
        String tenantId = request.getTenantId() == null ? null : request.getTenantId().trim();
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        String rawPassword = request.getPassword() == null ? "" : request.getPassword();
        RbacUser user = resolveLoginUser(tenantId, username, rawPassword);
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            passwordEncoder.matches(rawPassword, user.getPassword());
            throw loginFailure();
        }
        if (user.getLockUntil() != null && !user.getLockUntil().isAfter(now)) {
            user.setLockUntil(null);
            user.setFailedLoginCount(0);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int failures = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
            failures++;
            user.setFailedLoginCount(failures);
            if (failures >= MAX_LOGIN_FAILURES) {
                user.setLockUntil(now.plus(LOCK_DURATION));
            }
            userRepository.save(user);
            throw loginFailure();
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw loginFailure();
        }
        RbacUserSession session = buildSessionForUser(user);
        String token = tokenService.createToken(session);
        String refreshToken = null;
        long refreshExpiresIn = 0;
        if (isSuperAdmin(session)) {
            Duration refreshTtl = resolveRefreshTtl();
            refreshToken = refreshTokenService.issueRefreshToken(session, refreshTtl);
            refreshExpiresIn = refreshTtl.getSeconds();
        }

        user.setLastLoginTime(now);
        user.setFailedLoginCount(0);
        user.setLockUntil(null);
        userRepository.save(user);

        return buildLoginResponse(session, token, tokenService.getExpireSeconds(session), refreshToken, refreshExpiresIn);
    }

    /**
     * 刷新 Token。
     *
     * @param refreshToken Refresh Token
     * @return 登录响应
     * @author 余炘洋
     * @date 2026-01-27
     */
    public LoginResponse refresh(String refreshToken) {
        Duration refreshTtl = resolveRefreshTtl();
        RefreshTokenService.RefreshTokenResult result = refreshTokenService.rotateRefreshToken(refreshToken, refreshTtl);
        if (result == null || result.payload() == null) {
            throw new BadCredentialsException("Refresh Token 无效");
        }
        Long userId = result.payload().userId();
        if (userId == null) {
            throw new BadCredentialsException("Refresh Token 无效");
        }
        RbacUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("账号不存在"));
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BadCredentialsException("账号已停用");
        }
        if (StringUtils.hasText(result.payload().tenantId())
                && StringUtils.hasText(user.getTenantId())
                && !result.payload().tenantId().equalsIgnoreCase(user.getTenantId())) {
            throw new BadCredentialsException("账号不存在");
        }
        RbacUserSession session = buildSessionForUser(user);
        String token = tokenService.createToken(session);
        long expiresIn = tokenService.getExpireSeconds(session);
        return buildLoginResponse(session, token, expiresIn, result.refreshToken(), refreshTtl.getSeconds());
    }

    /**
     * 解析登录用户，支持租户ID为空时按账号唯一识别。
     *
     * @param tenantId 租户ID
     * @param username 账号
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private RbacUser resolveLoginUser(String tenantId, String username, String rawPassword) {
        if (StringUtils.hasText(tenantId)) {
            Optional<RbacUser> found = userRepository.findByTenantIdAndUsername(tenantId, username);
            if (found.isEmpty()) {
                absorbTiming(rawPassword);
                throw loginFailure();
            }
            return found.get();
        }
        List<RbacUser> candidates = userRepository.findTop2ByUsername(username);
        if (CollectionUtils.isEmpty(candidates) || candidates.size() > 1) {
            absorbTiming(rawPassword);
            throw loginFailure();
        }
        return candidates.get(0);
    }

    private void absorbTiming(String rawPassword) {
        String password = rawPassword == null ? "" : rawPassword;
        passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
    }

    private BadCredentialsException loginFailure() {
        return new BadCredentialsException(LOGIN_ERROR_MESSAGE);
    }

    /**
     * 构建登录响应。
     *
     * @param session   会话数据
     * @param token     Token
     * @param expiresIn 过期秒数
     * @return 登录响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    private LoginResponse buildLoginResponse(RbacUserSession session, String token, long expiresIn, String refreshToken, long refreshExpiresIn) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(expiresIn);
        response.setUserId(session.getUserId());
        response.setTenantId(session.getTenantId());
        response.setTenantName(resolveTenantName(session.getTenantId()));
        response.setUsername(session.getUsername());
        response.setNickname(session.getNickname());
        response.setExperience(session.isExperience());
        response.setRoles(session.getRoles());
        response.setPermissions(session.getPermissions());
        response.setRefreshToken(refreshToken);
        response.setRefreshExpiresIn(refreshExpiresIn);
        return response;
    }

    /**
     * 解析租户名称。
     *
     * @param tenantId 租户ID
     * @return 租户名称
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveTenantName(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }
        return tenantRepository.findByTenantIdAndIsDeleted(tenantId.trim(), 0)
                .map(item -> item.getTenantName())
                .orElse(null);
    }

    /**
     * 构建会话对象。
     *
     * @param user          用户实体
     * @param roles         角色编码
     * @param permissions   权限编码
     * @param dataScope     数据权限
     * @param menuWhitelist 菜单白名单
     * @return 会话对象
     * @author 余炘洋
     * @date 2026-01-23
     */
    private RbacUserSession buildSession(
            RbacUser user,
            List<String> roles,
            List<String> permissions,
            DataScopeRule dataScope,
            List<String> menuWhitelist
    ) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(user.getId());
        session.setTenantId(user.getTenantId());
        session.setUsername(user.getUsername());
        session.setNickname(user.getNickname());
        session.setExperience(user.getIsExperience() != null && user.getIsExperience() == 1);
        session.setRoles(roles == null ? List.of() : roles);
        session.setPermissions(permissions == null ? List.of() : permissions);
        session.setDataScope(sanitizeDataScope(dataScope));
        session.setMenuWhitelist(menuWhitelist == null ? List.of() : menuWhitelist);
        return session;
    }

    private RbacUserSession buildSessionForUser(RbacUser user) {
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());
        List<RbacRole> roles = roleIds.isEmpty() ? List.of() : roleRepository.findByIdIn(roleIds);
        List<String> roleCodes = roles.stream()
                .filter(role -> role.getStatus() == null || role.getStatus() == 1)
                .map(RbacRole::getRoleCode)
                .toList();

        List<String> permCodes = resolvePermissions(roleIds);
        DataScopeRule dataScope = resolveDataScope(user.getId(), roleIds);
        List<String> menuWhitelist = new ArrayList<>();

        boolean experience = user.getIsExperience() != null && user.getIsExperience() == 1;
        if (experience) {
            applyExperiencePolicy(user.getId(), dataScope, menuWhitelist);
        }
        return buildSession(user, roleCodes, permCodes, dataScope, menuWhitelist);
    }

    /**
     * 解析权限编码。
     *
     * @param roleIds 角色ID列表
     * @return 权限编码列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> resolvePermissions(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        List<Long> permIds = rolePermissionRepository.findPermIdsByRoleIds(roleIds);
        if (CollectionUtils.isEmpty(permIds)) {
            return List.of();
        }
        List<RbacPermission> permissions = permissionRepository.findByIdIn(permIds);
        return permissions.stream()
                .filter(permission -> permission.getStatus() == null || permission.getStatus() == 1)
                .map(RbacPermission::getPermCode)
                .distinct()
                .toList();
    }

    /**
     * 解析数据权限规则。
     *
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     * @return 数据权限规则
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule resolveDataScope(Long userId, List<Long> roleIds) {
        Optional<RbacUserDataScope> userScope = userDataScopeRepository.findByUserId(userId);
        if (userScope.isPresent() && isActive(userScope.get().getStatus())) {
            return toRule(userScope.get().getScopeType(), userScope.get().getScopeColumn(), userScope.get().getScopeRule());
        }
        if (CollectionUtils.isEmpty(roleIds)) {
            return toRule(RbacDataScopeType.TENANT, null, null);
        }
        List<RbacRoleDataScope> scopes = roleDataScopeRepository.findActiveByRoleIds(roleIds);
        if (scopes.isEmpty()) {
            return toRule(RbacDataScopeType.TENANT, null, null);
        }
        return chooseBestScope(scopes);
    }

    /**
     * 按优先级挑选最合适的数据权限。
     *
     * @param scopes 规则列表
     * @return 数据权限规则
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule chooseBestScope(List<RbacRoleDataScope> scopes) {
        List<RbacRoleDataScope> sorted = scopes.stream()
                .sorted(Comparator.comparingInt(this::scopePriority))
                .toList();
        RbacRoleDataScope scope = sorted.get(0);
        return toRule(scope.getScopeType(), scope.getScopeColumn(), scope.getScopeRule());
    }

    /**
     * 范围优先级。
     *
     * @param scope 规则
     * @return 优先级
     * @author 余炘洋
     * @date 2026-01-23
     */
    private int scopePriority(RbacRoleDataScope scope) {
        String type = scope.getScopeType();
        if (RbacDataScopeType.ALL.equalsIgnoreCase(type)) {
            return 1;
        }
        if (RbacDataScopeType.CUSTOM.equalsIgnoreCase(type)) {
            return 2;
        }
        if (RbacDataScopeType.SELF.equalsIgnoreCase(type)) {
            return 3;
        }
        return 4;
    }

    /**
     * 应用体验账号策略。
     *
     * @param userId        用户ID
     * @param dataScope     数据权限
     * @param menuWhitelist 菜单白名单
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void applyExperiencePolicy(Long userId, DataScopeRule dataScope, List<String> menuWhitelist) {
        Optional<RbacUserExperiencePolicy> policyOpt = experiencePolicyRepository.findByUserId(userId);
        if (policyOpt.isEmpty()) {
            return;
        }
        RbacUserExperiencePolicy policy = policyOpt.get();
        if (!isActive(policy.getStatus())) {
            return;
        }
        if (StringUtils.hasText(policy.getMenuWhitelist())) {
            try {
                Set<String> whitelist = objectMapper.readValue(policy.getMenuWhitelist(), objectMapper.getTypeFactory()
                        .constructCollectionType(Set.class, String.class));
                menuWhitelist.addAll(whitelist);
            } catch (Exception ex) {
                // 忽略解析失败
            }
        }
        if (StringUtils.hasText(policy.getDataScopeOverride())) {
            try {
                DataScopeRule overrideRule = objectMapper.readValue(policy.getDataScopeOverride(), DataScopeRule.class);
                if (overrideRule != null && StringUtils.hasText(overrideRule.getScopeType())) {
                    dataScope.setScopeType(overrideRule.getScopeType());
                    dataScope.setScopeColumn(overrideRule.getScopeColumn());
                    dataScope.setScopeRule(overrideRule.getScopeRule());
                }
            } catch (Exception ex) {
                // 忽略解析失败
            }
        }
        sanitizeDataScope(dataScope);
    }

    /**
     * 构建数据权限规则。
     *
     * @param scopeType   范围类型
     * @param scopeColumn 字段
     * @param scopeRule   SQL 条件
     * @return 规则
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule toRule(String scopeType, String scopeColumn, String scopeRule) {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(StringUtils.hasText(scopeType) ? scopeType.toUpperCase() : RbacDataScopeType.TENANT);
        rule.setScopeColumn(StringUtils.hasText(scopeColumn) ? scopeColumn : "");
        rule.setScopeRule(StringUtils.hasText(scopeRule) ? scopeRule : "");
        return rule;
    }

    private DataScopeRule sanitizeDataScope(DataScopeRule rule) {
        if (rule == null) {
            return new DataScopeRule();
        }
        if (!StringUtils.hasText(rule.getScopeType())) {
            rule.setScopeType(RbacDataScopeType.TENANT);
        } else {
            rule.setScopeType(rule.getScopeType().toUpperCase());
        }
        if (!StringUtils.hasText(rule.getScopeColumn())) {
            rule.setScopeColumn("");
        }
        if (!StringUtils.hasText(rule.getScopeRule())) {
            rule.setScopeRule("");
        }
        return rule;
    }

    private boolean isSuperAdmin(RbacUserSession session) {
        return session != null && session.getRoles() != null && session.getRoles().contains(SUPER_ADMIN_ROLE);
    }

    private Duration resolveRefreshTtl() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth != null && auth.getRefreshTtl() != null) {
            return auth.getRefreshTtl();
        }
        return Duration.ofDays(7);
    }

    /**
     * 判断状态是否启用。
     *
     * @param status 状态
     * @return 是否启用
     * @author 余炘洋
     * @date 2026-01-23
     */
    private boolean isActive(Integer status) {
        return status == null || status == 1;
    }
}
