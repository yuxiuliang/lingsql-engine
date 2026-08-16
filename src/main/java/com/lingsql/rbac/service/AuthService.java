package com.lingsql.rbac.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserDataScope;
import com.lingsql.rbac.entity.RbacUserExperiencePolicy;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.AuthFlow;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.model.PasswordState;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.RequiredPasswordChangeRequest;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserDataScopeRepository;
import com.lingsql.rbac.repository.RbacUserExperiencePolicyRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SessionPurpose;
import com.lingsql.tenant.repository.TenantRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
@Slf4j
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String OWNERSHIP_RELATION = "OWNERSHIP";
    private static final String SUPERVISE_RELATION = "SUPERVISE";
    private static final String COOPERATE_RELATION = "COOPERATE";
    private static final String SERVICE_RELATION = "SERVICE";
    private static final String MATRIX_RELATION = "MATRIX";
    private static final Integer ACTIVE_STATUS = 1;
    private static final String LOGIN_ERROR_MESSAGE = "账号、密码或租户信息错误";
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5.5dQ9K/9e2XcGQnQ8l7uP3BvI6JuaS";
    private static final List<String> RELATION_SCOPE_ORDER = List.of(
            SUPERVISE_RELATION,
            COOPERATE_RELATION,
            SERVICE_RELATION,
            OWNERSHIP_RELATION,
            MATRIX_RELATION
    );
    private static final Set<String> SUBTREE_RELATION_TYPES = Set.of(
            OWNERSHIP_RELATION,
            SUPERVISE_RELATION,
            COOPERATE_RELATION,
            SERVICE_RELATION,
            MATRIX_RELATION
    );
    private static final Set<String> SUPPORTED_RELATION_TYPES = Set.of(
            OWNERSHIP_RELATION,
            SUPERVISE_RELATION,
            COOPERATE_RELATION,
            SERVICE_RELATION,
            MATRIX_RELATION
    );

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
    private final PasswordCredentialService passwordCredentialService;
    private final ObjectMapper objectMapper;
    private final OrgUnitRepository orgUnitRepository;
    private final OrgUnitRelationRepository orgUnitRelationRepository;
    private final UserOrgMembershipRepository userOrgMembershipRepository;

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
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
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
        if (resolvePasswordState(user) == PasswordState.CHANGE_REQUIRED) {
            tokenService.revokeUserTokens(user.getId());
            refreshTokenService.revokeByUserId(user.getId());
            RbacUserSession restrictedSession = buildPasswordChangeSession(user);
            String restrictedToken = tokenService.createToken(restrictedSession);
            user.setFailedLoginCount(0);
            user.setLockUntil(null);
            userRepository.save(user);
            return buildLoginResponse(
                    restrictedSession,
                    restrictedToken,
                    tokenService.getExpireSeconds(restrictedSession),
                    null,
                    0
            );
        }
        if (isSingleSessionEnabled()) {
            tokenService.revokeUserTokens(user.getId());
            refreshTokenService.revokeByUserId(user.getId());
        }
        RbacUserSession session = buildSessionForUser(user);
        String token = tokenService.createToken(session);
        String refreshToken = null;
        long refreshExpiresIn = 0;
        Duration refreshTtl = resolveRefreshTtl();
        if (refreshTtl != null && !refreshTtl.isZero() && !refreshTtl.isNegative()) {
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
        if (resolvePasswordState(user) != PasswordState.ACTIVE
                || resolveCredentialVersion(user) != resolveCredentialVersion(result.payload().credentialVersion())) {
            refreshTokenService.revoke(result.refreshToken(), user.getId());
            refreshTokenService.revokeByUserId(user.getId());
            throw new BadCredentialsException("Refresh Token 已失效");
        }
        if (isSingleSessionEnabled()) {
            tokenService.revokeUserTokens(user.getId());
        }
        RbacUserSession session = buildSessionForUser(user);
        String token = tokenService.createToken(session);
        long expiresIn = tokenService.getExpireSeconds(session);
        return buildLoginResponse(session, token, expiresIn, result.refreshToken(), refreshTtl.getSeconds());
    }

    /**
     * 使用受限会话完成首次登录改密，并签发完整会话。
     *
     * @param session 受限会话
     * @param request 改密请求
     * @return 完整登录响应
     */
    public LoginResponse changeRequiredPassword(RbacUserSession session, RequiredPasswordChangeRequest request) {
        if (session == null || session.getPurpose() != SessionPurpose.PASSWORD_CHANGE_ONLY) {
            throw new BadCredentialsException("当前会话不能执行首次登录改密");
        }
        RbacUser user = passwordCredentialService.completeRequiredChange(session, request);
        log.info(
                "首次登录改密完成: tenantId={}, userId={}, credentialVersion={}, requestId={}",
                user.getTenantId(),
                user.getId(),
                user.getCredentialVersion(),
                RequestTraceContext.getRequestId()
        );
        tokenService.revokeUserTokens(user.getId());
        refreshTokenService.revokeByUserId(user.getId());

        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginTime(now);
        userRepository.save(user);
        RbacUserSession fullSession = buildSessionForUser(user);
        String token = tokenService.createToken(fullSession);
        Duration refreshTtl = resolveRefreshTtl();
        String refreshToken = null;
        long refreshExpiresIn = 0L;
        if (refreshTtl != null && !refreshTtl.isZero() && !refreshTtl.isNegative()) {
            refreshToken = refreshTokenService.issueRefreshToken(fullSession, refreshTtl);
            refreshExpiresIn = refreshTtl.getSeconds();
        }
        return buildLoginResponse(
                fullSession,
                token,
                tokenService.getExpireSeconds(fullSession),
                refreshToken,
                refreshExpiresIn
        );
    }

    /**
     * 解析登录用户，要求租户ID与账号同时明确。
     *
     * @param tenantId    租户ID
     * @param username    账号
     * @param rawPassword 原始密码
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private RbacUser resolveLoginUser(String tenantId, String username, String rawPassword) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(username)) {
            absorbTiming(rawPassword);
            throw loginFailure();
        }
        Optional<RbacUser> found = userRepository.findByTenantIdAndUsername(tenantId, username);
        if (found.isEmpty()) {
            absorbTiming(rawPassword);
            throw loginFailure();
        }
        return found.get();
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
        response.setRelationScopes(copyRelationScopes(session.getRelationScopes()));
        response.setRefreshToken(refreshToken);
        response.setRefreshExpiresIn(refreshExpiresIn);
        response.setAuthFlow(session.getPurpose() == SessionPurpose.PASSWORD_CHANGE_ONLY
                ? AuthFlow.PASSWORD_CHANGE_REQUIRED : AuthFlow.AUTHENTICATED);
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
        session.setPurpose(SessionPurpose.FULL_ACCESS);
        session.setCredentialVersion(resolveCredentialVersion(user));
        session.setUserId(user.getId());
        session.setTenantId(user.getTenantId());
        session.setUsername(user.getUsername());
        session.setNickname(user.getNickname());
        session.setExperience(user.getIsExperience() != null && user.getIsExperience() == 1);
        session.setRoles(roles == null ? List.of() : roles);
        session.setPermissions(permissions == null ? List.of() : permissions);
        session.setDataScope(sanitizeDataScope(dataScope));
        List<Long> orgUnitIds = resolveUserOrgUnitIds(user.getId());
        List<OrgUnit> memberOrgUnits = resolveUserOrgUnits(orgUnitIds);
        List<String> memberOrgUnitCodes = collectOrgUnitCodes(memberOrgUnits);
        List<String> memberOrgTypeCodes = collectOrgTypeCodes(memberOrgUnits);
        List<String> memberOrgTags = collectOrgTags(memberOrgUnits);
        List<String> memberOrgSubtreeCodes = resolveUserOrgSubtreeCodes(user.getTenantId(), orgUnitIds);
        session.setOrgUnitIds(orgUnitIds);
        session.setMemberOrgUnitCodes(memberOrgUnitCodes);
        session.setMemberOrgTypeCodes(memberOrgTypeCodes);
        session.setMemberOrgTags(memberOrgTags);
        session.setMemberOrgSubtreeCodes(memberOrgSubtreeCodes);
        ResolvedRelationScopes relationScopes = resolveRelationVisibleCodes(user.getTenantId(), orgUnitIds);
        session.setRelationDirectScopes(relationScopes.directGroupedCodes());
        session.setRelationScopes(relationScopes.groupedCodes());
        session.setOrgUnitCodes(mergeCodes(memberOrgUnitCodes, relationScopes.visibleCodes()));
        session.setOrgSubtreeCodes(mergeCodes(memberOrgSubtreeCodes, relationScopes.visibleCodes()));
        session.setMenuWhitelist(menuWhitelist == null ? List.of() : menuWhitelist);
        return session;
    }

    private RbacUserSession buildPasswordChangeSession(RbacUser user) {
        RbacUserSession session = new RbacUserSession();
        session.setPurpose(SessionPurpose.PASSWORD_CHANGE_ONLY);
        session.setCredentialVersion(resolveCredentialVersion(user));
        session.setUserId(user.getId());
        session.setTenantId(user.getTenantId());
        session.setUsername(user.getUsername());
        session.setNickname(user.getNickname());
        session.setExperience(user.getIsExperience() != null && user.getIsExperience() == 1);
        session.setRoles(List.of());
        session.setPermissions(List.of("AUTH:PASSWORD:CHANGE"));
        session.setMenuWhitelist(List.of());
        return session;
    }

    private PasswordState resolvePasswordState(RbacUser user) {
        return user == null || user.getPasswordState() == null ? PasswordState.ACTIVE : user.getPasswordState();
    }

    private long resolveCredentialVersion(RbacUser user) {
        return user == null ? 1L : resolveCredentialVersion(user.getCredentialVersion());
    }

    private long resolveCredentialVersion(Long credentialVersion) {
        return credentialVersion == null || credentialVersion < 1L ? 1L : credentialVersion;
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
        if (roleCodes.contains(SUPER_ADMIN_ROLE)) {
            dataScope = toRule(RbacDataScopeType.ALL, null, null);
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
        if (RbacDataScopeType.ORG_SUBTREE.equalsIgnoreCase(type)) {
            return 3;
        }
        if (RbacDataScopeType.RELATION_SUBTREE.equalsIgnoreCase(type)) {
            return 4;
        }
        if (RbacDataScopeType.RELATION.equalsIgnoreCase(type)) {
            return 5;
        }
        if (RbacDataScopeType.ORG.equalsIgnoreCase(type)) {
            return 6;
        }
        if (RbacDataScopeType.SELF.equalsIgnoreCase(type)) {
            return 7;
        }
        return 8;
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
        if (isRelationScopeType(rule.getScopeType())) {
            rule.setScopeRule("");
            rule.setRelationTypes(parseRelationTypes(scopeRule));
        } else {
            rule.setScopeRule(StringUtils.hasText(scopeRule) ? scopeRule : "");
            rule.setRelationTypes(List.of());
        }
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
        if (rule.getRelationTypes() == null) {
            rule.setRelationTypes(new ArrayList<>());
        }
        return rule;
    }

    /**
     * 判断是否为关系型数据权限。
     *
     * @param scopeType 范围类型
     * @return 是否关系型
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean isRelationScopeType(String scopeType) {
        return RbacDataScopeType.RELATION.equalsIgnoreCase(scopeType)
                || RbacDataScopeType.RELATION_SUBTREE.equalsIgnoreCase(scopeType);
    }

    /**
     * 解析关系类型列表。
     *
     * @param rawValue 原始配置
     * @return 关系类型列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> parseRelationTypes(String rawValue) {
        return parseNormalizedValues(rawValue);
    }

    /**
     * 解析用户组织节点ID列表。
     *
     * @param userId 用户ID
     * @return 组织节点ID列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<Long> resolveUserOrgUnitIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (UserOrgMembership membership : userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId)) {
            if (membership.getOrgUnitId() != null) {
                ids.add(membership.getOrgUnitId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 解析用户组织编码列表。
     *
     * @param orgUnitIds 组织节点ID列表
     * @return 组织编码列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<OrgUnit> resolveUserOrgUnits(List<Long> orgUnitIds) {
        if (CollectionUtils.isEmpty(orgUnitIds)) {
            return List.of();
        }
        return orgUnitRepository.findByIdIn(orgUnitIds);
    }

    /**
     * 收集直接归属组织编码列表。
     *
     * @param orgUnits 直接归属组织节点
     * @return 组织编码列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> collectOrgUnitCodes(List<OrgUnit> orgUnits) {
        if (CollectionUtils.isEmpty(orgUnits)) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (OrgUnit orgUnit : orgUnits) {
            if (StringUtils.hasText(orgUnit.getUnitCode())) {
                codes.add(orgUnit.getUnitCode().trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 收集直接归属组织类型编码列表。
     *
     * @param orgUnits 直接归属组织节点
     * @return 组织类型编码列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> collectOrgTypeCodes(List<OrgUnit> orgUnits) {
        if (CollectionUtils.isEmpty(orgUnits)) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (OrgUnit orgUnit : orgUnits) {
            if (StringUtils.hasText(orgUnit.getTypeCode())) {
                codes.add(orgUnit.getTypeCode().trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 收集直接归属组织标签列表。
     *
     * @param orgUnits 直接归属组织节点
     * @return 组织标签列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> collectOrgTags(List<OrgUnit> orgUnits) {
        if (CollectionUtils.isEmpty(orgUnits)) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (OrgUnit orgUnit : orgUnits) {
            tags.addAll(parseNormalizedValues(orgUnit.getTagText()));
        }
        return new ArrayList<>(tags);
    }

    /**
     * 解析并规范化逗号/分号分隔的取值列表。
     *
     * @param rawValue 原始文本
     * @return 规范化后的取值列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> parseNormalizedValues(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        String normalized = rawValue
                .replace('，', ',')
                .replace('；', ';')
                .replace('\n', ',')
                .replace('\r', ',');
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : normalized.split("[,;]+")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * 解析用户组织及下级编码列表。
     *
     * @param tenantId   租户ID
     * @param orgUnitIds 组织节点ID列表
     * @return 组织及下级编码列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> resolveUserOrgSubtreeCodes(String tenantId, List<Long> orgUnitIds) {
        if (!StringUtils.hasText(tenantId) || CollectionUtils.isEmpty(orgUnitIds)) {
            return List.of();
        }
        List<OrgUnit> tenantUnits = orgUnitRepository.findByTenantId(tenantId);
        List<Long> matchedRootIds = tenantUnits.stream()
                .filter(unit -> unit.getId() != null && orgUnitIds.contains(unit.getId()))
                .map(OrgUnit::getId)
                .toList();
        if (matchedRootIds.isEmpty()) {
            return List.of();
        }
        return resolveSubtreeCodes(tenantUnits, matchedRootIds);
    }

    /**
     * 解析组织关系可扩展访问的编码列表。
     *
     * <p>当前统一按“目标组织及下级”扩展关系语义：</p>
     * <p>`OWNERSHIP / SUPERVISE / COOPERATE / SERVICE / MATRIX` 均会并入当前会话可见范围。</p>
     *
     * @param tenantId   租户ID
     * @param orgUnitIds 当前用户直属组织节点
     * @return 可扩展访问的组织编码列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private ResolvedRelationScopes resolveRelationVisibleCodes(String tenantId, List<Long> orgUnitIds) {
        if (!StringUtils.hasText(tenantId) || CollectionUtils.isEmpty(orgUnitIds)) {
            return ResolvedRelationScopes.empty();
        }
        List<OrgUnitRelation> relations = orgUnitRelationRepository
                .findByTenantIdAndStatusAndSourceOrgUnitIdIn(tenantId, ACTIVE_STATUS, orgUnitIds);
        if (CollectionUtils.isEmpty(relations)) {
            return ResolvedRelationScopes.empty();
        }
        List<OrgUnit> tenantUnits = orgUnitRepository.findByTenantId(tenantId);
        if (CollectionUtils.isEmpty(tenantUnits)) {
            return ResolvedRelationScopes.empty();
        }
        Map<Long, OrgUnit> unitMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null)
                .collect(Collectors.toMap(OrgUnit::getId, item -> item, (left, right) -> left));
        LinkedHashSet<String> visibleCodes = new LinkedHashSet<>();
        Map<String, LinkedHashSet<String>> groupedCodes = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> directGroupedCodes = new LinkedHashMap<>();
        for (OrgUnitRelation relation : relations) {
            if (relation == null || relation.getTargetOrgUnitId() == null) {
                continue;
            }
            String relationType = normalizeRelationType(relation.getRelationType());
            if (!SUPPORTED_RELATION_TYPES.contains(relationType)) {
                continue;
            }
            OrgUnit target = unitMapping.get(relation.getTargetOrgUnitId());
            if (target == null || !StringUtils.hasText(target.getUnitCode())) {
                continue;
            }
            String normalizedCode = target.getUnitCode().trim().toUpperCase();
            visibleCodes.add(normalizedCode);
            LinkedHashSet<String> typeCodes = groupedCodes.computeIfAbsent(relationType, key -> new LinkedHashSet<>());
            LinkedHashSet<String> directTypeCodes = directGroupedCodes.computeIfAbsent(relationType, key -> new LinkedHashSet<>());
            typeCodes.add(normalizedCode);
            directTypeCodes.add(normalizedCode);
            if (SUBTREE_RELATION_TYPES.contains(relationType)) {
                List<String> subtreeCodes = resolveSubtreeCodes(tenantUnits, List.of(target.getId()));
                typeCodes.addAll(subtreeCodes);
                visibleCodes.addAll(subtreeCodes);
            }
        }
        if (groupedCodes.isEmpty()) {
            return ResolvedRelationScopes.empty();
        }
        Map<String, List<String>> normalizedGroupedCodes = new LinkedHashMap<>();
        Map<String, List<String>> normalizedDirectGroupedCodes = new LinkedHashMap<>();
        for (String relationType : RELATION_SCOPE_ORDER) {
            LinkedHashSet<String> codes = groupedCodes.get(relationType);
            if (codes == null || codes.isEmpty()) {
                codes = new LinkedHashSet<>();
            }
            if (!codes.isEmpty()) {
                normalizedGroupedCodes.put(relationType, new ArrayList<>(codes));
            }
            LinkedHashSet<String> directCodes = directGroupedCodes.get(relationType);
            if (directCodes != null && !directCodes.isEmpty()) {
                normalizedDirectGroupedCodes.put(relationType, new ArrayList<>(directCodes));
            }
        }
        return new ResolvedRelationScopes(normalizedGroupedCodes, normalizedDirectGroupedCodes, new ArrayList<>(visibleCodes));
    }

    /**
     * 按根节点解析自身及下级组织编码。
     *
     * @param tenantUnits 租户内全部组织节点
     * @param rootIds     根节点ID列表
     * @return 编码列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> resolveSubtreeCodes(List<OrgUnit> tenantUnits, List<Long> rootIds) {
        if (CollectionUtils.isEmpty(tenantUnits) || CollectionUtils.isEmpty(rootIds)) {
            return List.of();
        }
        Map<Long, OrgUnit> rootMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null && rootIds.contains(unit.getId()))
                .collect(Collectors.toMap(OrgUnit::getId, item -> item));
        if (rootMapping.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (OrgUnit unit : tenantUnits) {
            if (!StringUtils.hasText(unit.getUnitCode()) || unit.getId() == null) {
                continue;
            }
            for (OrgUnit root : rootMapping.values()) {
                if (root == null || root.getId() == null) {
                    continue;
                }
                if (Objects.equals(unit.getId(), root.getId())) {
                    codes.add(unit.getUnitCode().trim().toUpperCase());
                    break;
                }
                if (StringUtils.hasText(root.getTreePath())
                        && StringUtils.hasText(unit.getTreePath())
                        && unit.getTreePath().startsWith(root.getTreePath() + "/")) {
                    codes.add(unit.getUnitCode().trim().toUpperCase());
                    break;
                }
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 合并组织编码列表并去重，保持原有顺序优先。
     *
     * @param primary   主列表
     * @param secondary 追加列表
     * @return 合并结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> mergeCodes(List<String> primary, List<String> secondary) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(primary)) {
            merged.addAll(primary.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(item -> item.toUpperCase(Locale.ROOT))
                    .toList());
        }
        if (!CollectionUtils.isEmpty(secondary)) {
            merged.addAll(secondary.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(item -> item.toUpperCase(Locale.ROOT))
                    .toList());
        }
        return new ArrayList<>(merged);
    }

    /**
     * 标准化组织关系类型。
     *
     * @param relationType 原始关系类型
     * @return 标准化结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalizeRelationType(String relationType) {
        if (!StringUtils.hasText(relationType)) {
            return "";
        }
        return relationType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 复制关系语义映射，避免响应对象与会话共享可变引用。
     *
     * @param relationScopes 关系语义映射
     * @return 复制后的映射
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Map<String, List<String>> copyRelationScopes(Map<String, List<String>> relationScopes) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (relationScopes == null || relationScopes.isEmpty()) {
            return copied;
        }
        for (Map.Entry<String, List<String>> entry : relationScopes.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || CollectionUtils.isEmpty(entry.getValue())) {
                continue;
            }
            copied.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copied;
    }

    /**
     * 关系语义解析结果。
     *
     * @param groupedCodes 按关系类型分组的编码
     * @param visibleCodes 合并后的可见编码
     * @author 余炘洋
     * @date 2026-04-11
     */
    private record ResolvedRelationScopes(Map<String, List<String>> groupedCodes,
                                         Map<String, List<String>> directGroupedCodes,
                                         List<String> visibleCodes) {

        private static ResolvedRelationScopes empty() {
            return new ResolvedRelationScopes(new LinkedHashMap<>(), new LinkedHashMap<>(), List.of());
        }
    }

    private Duration resolveRefreshTtl() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth != null && auth.getRefreshTtl() != null) {
            return auth.getRefreshTtl();
        }
        return Duration.ofDays(7);
    }

    private boolean isSingleSessionEnabled() {
        LingSqlProperties.Auth auth = properties.getAuth();
        return auth == null || auth.isSingleSessionEnabled();
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
