package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.PageResult;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.service.OrgGovernanceCatalogService;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserDataScope;
import com.lingsql.rbac.entity.RbacUserRole;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.PasswordState;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.model.UserPasswordResetRequest;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.model.UserView;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserDataScopeRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    private static final String EXPERIENCE_ROLE_CODE = "ROLE_EXPERIENCE_TEMPLATE";
    private static final String INHERIT_SCOPE = "INHERIT";
    private static final String EFFECTIVE_SOURCE_USER = "USER_OVERRIDE";
    private static final String EFFECTIVE_SOURCE_ROLE = "ROLE_INHERIT";
    private static final String EFFECTIVE_SOURCE_DEFAULT = "DEFAULT_TENANT";

    private final RbacUserRepository userRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final RbacRoleDataScopeRepository roleDataScopeRepository;
    private final RbacRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PageParamResolver pageParamResolver;
    private final OrgUnitRepository orgUnitRepository;
    private final UserOrgMembershipRepository userOrgMembershipRepository;
    private final RbacUserDataScopeRepository userDataScopeRepository;
    private final OrgGovernanceCatalogService orgGovernanceCatalogService;

    /**
     * 查询用户列表。
     *
     * @param tenantId 租户ID
     * @param keyword  关键词
     * @param page     页码
     * @param size     每页大小
     * @return 用户分页
     * @author 余炘洋
     * @date 2026-01-26
     */
    public PageResult<UserView> listUsers(String tenantId, String keyword, int page, int size) {
        PageParam param = pageParamResolver.resolve(page, size);
        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(), Sort.by(Sort.Direction.DESC, "id"));
        Page<RbacUser> result;
        if (StringUtils.hasText(tenantId)) {
            if (StringUtils.hasText(keyword)) {
                result = userRepository.findByTenantIdAndUsernameContaining(tenantId, keyword.trim(), pageable);
            } else {
                result = userRepository.findByTenantId(tenantId, pageable);
            }
        } else {
            result = userRepository.findAll(pageable);
        }
        List<UserView> views = result.getContent().stream().map(this::toView).toList();
        return new PageResult<>(views, result.getTotalElements(), param.page(), param.size());
    }

    /**
     * 创建用户。
     *
     * @param request   创建请求
     * @param tenantId  租户ID
     * @param sysAdmin  是否系统管理员
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public UserView createUser(UserCreateRequest request, String tenantId, boolean sysAdmin) {
        return createUser(request, tenantId, sysAdmin, PasswordState.CHANGE_REQUIRED);
    }

    /**
     * 创建具有指定初始密码状态的用户。
     *
     * @param request       创建请求
     * @param tenantId      租户ID
     * @param sysAdmin      是否系统管理员
     * @param passwordState 初始密码状态
     * @return 用户信息
     */
    @Transactional
    public UserView createUser(
            UserCreateRequest request,
            String tenantId,
            boolean sysAdmin,
            PasswordState passwordState
    ) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请求不能为空");
        }
        String username = normalize(request.getUsername());
        if (!StringUtils.hasText(username)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户名不能为空");
        }
        String resolvedTenant = resolveTenantId(request.getTenantId(), tenantId, sysAdmin);
        if (userRepository.findByTenantIdAndUsername(resolvedTenant, username).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "账号已存在");
        }
        passwordPolicyService.validate(request.getPassword());
        RbacUser user = new RbacUser();
        user.setTenantId(resolvedTenant);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordState(passwordState == null ? PasswordState.ACTIVE : passwordState);
        user.setCredentialVersion(1L);
        user.setNickname(normalize(request.getNickname()));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setIsExperience(request.getIsExperience() == null ? 0 : request.getIsExperience());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        applyUserRoles(user, request.getRoleIds(), sysAdmin);
        applyUserOrgMembership(user, request.getOrgUnitIds(), request.getPrimaryOrgUnitId());
        if (request.getDataScope() != null) {
            applyUserDataScope(user, request.getDataScope());
        }
        return toView(user);
    }

    /**
     * 更新用户。
     *
     * @param userId   用户ID
     * @param request  更新请求
     * @param tenantId 租户ID
     * @param sysAdmin 是否系统管理员
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public UserView updateUser(Long userId, UserUpdateRequest request, String tenantId, boolean sysAdmin) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户ID不能为空");
        }
        RbacUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
        validateTenantAccess(user.getTenantId(), tenantId, sysAdmin);
        Integer originalStatus = user.getStatus();
        Integer originalExperience = user.getIsExperience();
        boolean rolesUpdated = request != null && request.getRoleIds() != null;
        if (request != null) {
            if (request.getNickname() != null) {
                user.setNickname(normalize(request.getNickname()));
            }
            if (request.getStatus() != null) {
                user.setStatus(request.getStatus());
            }
            if (request.getIsExperience() != null) {
                user.setIsExperience(request.getIsExperience());
            }
        }
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        if (request != null && request.getRoleIds() != null) {
            applyUserRoles(user, request.getRoleIds(), sysAdmin);
        } else if (request != null && request.getIsExperience() != null && request.getIsExperience() == 1) {
            applyUserRoles(user, List.of(), sysAdmin);
        }
        if (request != null && (request.getOrgUnitIds() != null || request.getPrimaryOrgUnitId() != null)) {
            List<Long> targetOrgUnitIds = request.getOrgUnitIds();
            if (targetOrgUnitIds == null) {
                targetOrgUnitIds = userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(user.getId()).stream()
                        .map(UserOrgMembership::getOrgUnitId)
                        .toList();
            }
            applyUserOrgMembership(user, targetOrgUnitIds, request.getPrimaryOrgUnitId());
        }
        if (request != null && request.getDataScope() != null) {
            applyUserDataScope(user, request.getDataScope());
        }
        if (request != null) {
            boolean statusDisabled = request.getStatus() != null && request.getStatus() == 0;
            boolean experienceChanged = request.getIsExperience() != null && !request.getIsExperience().equals(originalExperience);
            boolean statusChanged = request.getStatus() != null && !request.getStatus().equals(originalStatus);
            boolean dataScopeChanged = request.getDataScope() != null;
            if (statusDisabled || rolesUpdated || experienceChanged || statusChanged || dataScopeChanged) {
                tokenService.revokeUserTokens(user.getId());
                refreshTokenService.revokeByUserId(user.getId());
            }
        }
        return toView(user);
    }

    /**
     * 重置用户密码。
     *
     * @param userId   用户ID
     * @param request  重置请求
     * @param tenantId 租户ID
     * @param sysAdmin 是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public void resetPassword(Long userId, UserPasswordResetRequest request, String tenantId, boolean sysAdmin) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户ID不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getNewPassword())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "新密码不能为空");
        }
        passwordPolicyService.validate(request.getNewPassword());
        RbacUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
        validateTenantAccess(user.getTenantId(), tenantId, sysAdmin);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordState(PasswordState.CHANGE_REQUIRED);
        user.setPasswordChangedAt(null);
        user.setCredentialVersion(resolveCredentialVersion(user) + 1L);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        revokeCredentialsAfterCommit(user.getId());
    }

    /**
     * 应用用户角色。
     *
     * @param user      用户
     * @param roleIds   角色ID列表
     * @param sysAdmin  是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void applyUserRoles(RbacUser user, List<Long> roleIds, boolean sysAdmin) {
        if (user == null) {
            return;
        }
        List<Long> resolvedRoleIds = resolveRoleIds(user, roleIds, sysAdmin);
        userRoleRepository.deleteByUserId(user.getId());
        if (CollectionUtils.isEmpty(resolvedRoleIds)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RbacUserRole> relations = new ArrayList<>();
        for (Long roleId : resolvedRoleIds) {
            RbacUserRole relation = new RbacUserRole();
            relation.setUserId(user.getId());
            relation.setRoleId(roleId);
            relation.setCreateTime(now);
            relations.add(relation);
        }
        userRoleRepository.saveAll(relations);
    }

    /**
     * 解析角色ID。
     *
     * @param user     用户
     * @param roleIds  角色ID列表
     * @param sysAdmin 是否系统管理员
     * @return 角色ID列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<Long> resolveRoleIds(RbacUser user, List<Long> roleIds, boolean sysAdmin) {
        List<Long> inputRoleIds = CollectionUtils.isEmpty(roleIds) ? List.of() : roleIds;
        boolean experience = user.getIsExperience() != null && user.getIsExperience() == 1;
        Set<Long> resolvedIds = new HashSet<>();
        if (!CollectionUtils.isEmpty(inputRoleIds)) {
            List<RbacRole> roles = roleRepository.findByIdIn(inputRoleIds);
            for (RbacRole role : roles) {
                if (role.getId() == null) {
                    continue;
                }
                if (!sysAdmin && role.getTenantId() == null) {
                    continue;
                }
                if (!sysAdmin && role.getTenantId() != null && !role.getTenantId().equals(user.getTenantId())) {
                    continue;
                }
                resolvedIds.add(role.getId());
            }
        }
        if (experience) {
            Long experienceRoleId = resolveExperienceRoleId();
            resolvedIds.add(experienceRoleId);
            if (!sysAdmin) {
                return List.of(experienceRoleId);
            }
        }
        return new ArrayList<>(resolvedIds);
    }

    /**
     * 应用用户组织归属关系。
     *
     * @param user             用户实体
     * @param orgUnitIds       组织节点ID列表
     * @param primaryOrgUnitId 主归属组织ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void applyUserOrgMembership(RbacUser user, List<Long> orgUnitIds, Long primaryOrgUnitId) {
        if (user == null || user.getId() == null) {
            return;
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(orgUnitIds)) {
            normalizedIds.addAll(orgUnitIds.stream().filter(Objects::nonNull).toList());
        }
        if (primaryOrgUnitId != null) {
            normalizedIds.add(primaryOrgUnitId);
        }
        if (normalizedIds.isEmpty()) {
            userOrgMembershipRepository.deleteByUserId(user.getId());
            return;
        }
        List<Long> orderedIds = new ArrayList<>(normalizedIds);
        List<OrgUnit> orgUnits = orgUnitRepository.findByIdIn(orderedIds);
        if (orgUnits.size() != orderedIds.size()) {
            throw new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "组织节点不存在");
        }
        Map<Long, OrgUnit> orgUnitMapping = orgUnits.stream()
                .collect(Collectors.toMap(OrgUnit::getId, item -> item));
        for (Long orgUnitId : orderedIds) {
            OrgUnit orgUnit = orgUnitMapping.get(orgUnitId);
            if (orgUnit == null || !Objects.equals(user.getTenantId(), orgUnit.getTenantId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "组织节点不属于当前租户");
            }
        }
        Long resolvedPrimary = primaryOrgUnitId != null ? primaryOrgUnitId : orderedIds.get(0);
        userOrgMembershipRepository.deleteByUserId(user.getId());
        LocalDateTime now = LocalDateTime.now();
        List<UserOrgMembership> memberships = new ArrayList<>();
        for (Long orgUnitId : orderedIds) {
            UserOrgMembership membership = new UserOrgMembership();
            membership.setTenantId(user.getTenantId());
            membership.setUserId(user.getId());
            membership.setOrgUnitId(orgUnitId);
            membership.setIsPrimary(Objects.equals(orgUnitId, resolvedPrimary) ? 1 : 0);
            membership.setCreateTime(now);
            membership.setUpdateTime(now);
            memberships.add(membership);
        }
        userOrgMembershipRepository.saveAll(memberships);
    }

    /**
     * 应用用户数据权限覆盖。
     *
     * @param user 用户
     * @param rule 数据权限规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void applyUserDataScope(RbacUser user, DataScopeRule rule) {
        if (user == null || user.getId() == null) {
            return;
        }
        userDataScopeRepository.deleteByUserId(user.getId());
        if (rule == null || !StringUtils.hasText(rule.getScopeType())
                || INHERIT_SCOPE.equalsIgnoreCase(rule.getScopeType())) {
            return;
        }
        DataScopeRule normalizedRule = normalizeDataScope(rule, user.getTenantId());
        validateUserDataScope(user, normalizedRule);
        LocalDateTime now = LocalDateTime.now();
        RbacUserDataScope entity = new RbacUserDataScope();
        entity.setUserId(user.getId());
        entity.setScopeType(normalizedRule.getScopeType());
        entity.setScopeColumn(normalizedRule.getScopeColumn());
        entity.setScopeRule(resolvePersistedScopeRule(normalizedRule));
        entity.setStatus(1);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        userDataScopeRepository.save(entity);
    }

    /**
     * 校验用户数据权限配置是否满足前置条件。
     *
     * @param user 用户
     * @param rule 数据权限规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void validateUserDataScope(RbacUser user, DataScopeRule rule) {
        if (user == null || user.getId() == null || rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return;
        }
        if (!RbacDataScopeType.ORG.equals(rule.getScopeType())
                && !RbacDataScopeType.ORG_SUBTREE.equals(rule.getScopeType())
                && !RbacDataScopeType.RELATION.equals(rule.getScopeType())
                && !RbacDataScopeType.RELATION_SUBTREE.equals(rule.getScopeType())) {
            return;
        }
        if (!userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(user.getId()).isEmpty()) {
            return;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "组织数据权限要求用户至少绑定一个组织节点");
    }

    /**
     * 获取体验账号模板角色ID。
     *
     * @return 角色ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long resolveExperienceRoleId() {
        return roleRepository.findByTenantIdIsNullAndRoleCode(EXPERIENCE_ROLE_CODE)
                .orElseThrow(() -> new BizException(ErrorCode.ROLE_NOT_FOUND, "体验账号模板角色不存在"))
                .getId();
    }

    private long resolveCredentialVersion(RbacUser user) {
        return user == null || user.getCredentialVersion() == null || user.getCredentialVersion() < 1L
                ? 1L : user.getCredentialVersion();
    }

    private void revokeCredentialsAfterCommit(Long userId) {
        Runnable revokeAction = () -> {
            try {
                tokenService.revokeUserTokens(userId);
                refreshTokenService.revokeByUserId(userId);
            } catch (RuntimeException ex) {
                log.error(
                        "密码重置后的 Redis 会话清理失败，将由数据库凭据版本复核在下次请求时拒绝并清理: userId={}",
                        userId,
                        ex
                );
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            revokeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revokeAction.run();
            }
        });
    }

    /**
     * 解析租户ID。
     *
     * @param requestTenant 请求租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveTenantId(String requestTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            return StringUtils.hasText(requestTenant) ? requestTenant.trim() : sessionTenant;
        }
        if (!StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        return sessionTenant.trim();
    }

    /**
     * 校验租户访问权限。
     *
     * @param targetTenant 目标租户
     * @param sessionTenant 会话租户
     * @param sysAdmin 是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void validateTenantAccess(String targetTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            return;
        }
        if (!StringUtils.hasText(sessionTenant) || !StringUtils.hasText(targetTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        if (!sessionTenant.trim().equals(targetTenant.trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
    }

    /**
     * 转换视图。
     *
     * @param user 用户实体
     * @return 视图对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private UserView toView(RbacUser user) {
        UserView view = new UserView();
        view.setId(user.getId());
        view.setTenantId(user.getTenantId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setStatus(user.getStatus());
        view.setIsExperience(user.getIsExperience());
        view.setLastLoginTime(user.getLastLoginTime());
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());
        view.setRoleIds(roleIds);
        List<UserOrgMembership> memberships = userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(user.getId());
        List<Long> orgUnitIds = memberships.stream()
                .map(UserOrgMembership::getOrgUnitId)
                .toList();
        view.setOrgUnitIds(orgUnitIds);
        view.setPrimaryOrgUnitId(memberships.stream()
                .filter(item -> item.getIsPrimary() != null && item.getIsPrimary() == 1)
                .map(UserOrgMembership::getOrgUnitId)
                .findFirst()
                .orElse(null));
        if (orgUnitIds.isEmpty()) {
            view.setOrgUnitNames(List.of());
        } else {
            Map<Long, String> orgUnitNames = orgUnitRepository.findByIdIn(orgUnitIds).stream()
                    .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getUnitName));
            view.setOrgUnitNames(orgUnitIds.stream()
                    .map(orgUnitNames::get)
                    .filter(Objects::nonNull)
                    .toList());
        }
        DataScopeRule userScope = resolveUserDataScope(user.getId());
        EffectiveDataScope effectiveDataScope = resolveEffectiveDataScope(userScope, roleIds);
        view.setDataScope(userScope);
        view.setEffectiveDataScope(effectiveDataScope.rule());
        view.setEffectiveDataScopeSource(effectiveDataScope.source());
        return view;
    }

    /**
     * 查询用户数据权限覆盖。
     *
     * @param userId 用户ID
     * @return 数据权限规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule resolveUserDataScope(Long userId) {
        if (userId == null) {
            return createInheritedDataScope();
        }
        return userDataScopeRepository.findByUserId(userId)
                .filter(scope -> scope.getStatus() == null || scope.getStatus() == 1)
                .map(this::toDataScopeRule)
                .orElseGet(this::createInheritedDataScope);
    }

    /**
     * 查询当前用户数据权限覆盖。
     *
     * @param userId 用户ID
     * @return 数据权限规则
     * @author 余炘洋
     * @date 2026-04-13
     */
    DataScopeRule resolveCurrentUserDataScope(Long userId) {
        return resolveUserDataScope(userId);
    }

    /**
     * 查询当前用户角色ID列表。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    List<Long> resolveCurrentUserRoleIds(Long userId) {
        return userId == null ? List.of() : userRoleRepository.findRoleIdsByUserId(userId);
    }

    /**
     * 查询当前用户组织归属ID列表。
     *
     * @param userId 用户ID
     * @return 组织归属ID列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    List<Long> resolveCurrentUserOrgUnitIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId).stream()
                .map(UserOrgMembership::getOrgUnitId)
                .toList();
    }

    /**
     * 解析当前真正生效的数据权限。
     *
     * @param userScope 用户覆盖规则
     * @param roleIds   角色ID列表
     * @return 生效规则与来源
     * @author 余炘洋
     * @date 2026-04-11
     */
    private EffectiveDataScope resolveEffectiveDataScope(DataScopeRule userScope, List<Long> roleIds) {
        if (userScope != null
                && StringUtils.hasText(userScope.getScopeType())
                && !INHERIT_SCOPE.equalsIgnoreCase(userScope.getScopeType())) {
            return new EffectiveDataScope(normalizeDataScope(userScope, null), EFFECTIVE_SOURCE_USER);
        }
        if (CollectionUtils.isEmpty(roleIds)) {
            return new EffectiveDataScope(createTenantDataScope(), EFFECTIVE_SOURCE_DEFAULT);
        }
        List<RbacRoleDataScope> scopes = roleDataScopeRepository.findActiveByRoleIds(roleIds);
        if (CollectionUtils.isEmpty(scopes)) {
            return new EffectiveDataScope(createTenantDataScope(), EFFECTIVE_SOURCE_DEFAULT);
        }
        return new EffectiveDataScope(toDataScopeRule(chooseBestScope(scopes)), EFFECTIVE_SOURCE_ROLE);
    }

    /**
     * 转换用户数据权限实体为规则模型。
     *
     * @param entity 数据权限实体
     * @return 规则模型
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule toDataScopeRule(RbacUserDataScope entity) {
        if (entity == null) {
            return createInheritedDataScope();
        }
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(StringUtils.hasText(entity.getScopeType())
                ? entity.getScopeType().trim().toUpperCase()
                : RbacDataScopeType.TENANT);
        rule.setScopeColumn(StringUtils.hasText(entity.getScopeColumn()) ? entity.getScopeColumn().trim() : "");
        if (isRelationScopeType(rule.getScopeType())) {
            rule.setScopeRule("");
            rule.setRelationTypes(parseRelationTypes(entity.getScopeRule()));
        } else {
            rule.setScopeRule(StringUtils.hasText(entity.getScopeRule()) ? entity.getScopeRule().trim() : "");
            rule.setRelationTypes(List.of());
        }
        return rule;
    }

    /**
     * 转换角色数据权限实体为规则模型。
     *
     * @param entity 角色数据权限实体
     * @return 规则模型
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule toDataScopeRule(RbacRoleDataScope entity) {
        DataScopeRule rule = new DataScopeRule();
        if (entity == null) {
            rule.setScopeType(RbacDataScopeType.TENANT);
            rule.setScopeColumn("");
            rule.setScopeRule("");
            return rule;
        }
        rule.setScopeType(StringUtils.hasText(entity.getScopeType())
                ? entity.getScopeType().trim().toUpperCase()
                : RbacDataScopeType.TENANT);
        rule.setScopeColumn(StringUtils.hasText(entity.getScopeColumn()) ? entity.getScopeColumn().trim() : "");
        if (isRelationScopeType(rule.getScopeType())) {
            rule.setScopeRule("");
            rule.setRelationTypes(parseRelationTypes(entity.getScopeRule()));
        } else {
            rule.setScopeRule(StringUtils.hasText(entity.getScopeRule()) ? entity.getScopeRule().trim() : "");
            rule.setRelationTypes(List.of());
        }
        return rule;
    }

    /**
     * 按登录态规则挑选最佳角色数据权限。
     *
     * @param scopes 角色数据权限列表
     * @return 最佳规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private RbacRoleDataScope chooseBestScope(List<RbacRoleDataScope> scopes) {
        return scopes.stream()
                .sorted(Comparator.comparingInt(this::scopePriority))
                .findFirst()
                .orElse(null);
    }

    /**
     * 角色数据权限优先级。
     *
     * @param scope 规则
     * @return 优先级
     * @author 余炘洋
     * @date 2026-04-11
     */
    private int scopePriority(RbacRoleDataScope scope) {
        String type = scope == null ? null : scope.getScopeType();
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
     * 规范化数据权限规则。
     *
     * @param rule 原始规则
     * @return 规范化后的规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule normalizeDataScope(DataScopeRule rule, String tenantId) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return createInheritedDataScope();
        }
        String scopeType = rule.getScopeType().trim().toUpperCase();
        if (!RbacDataScopeType.ALL.equals(scopeType)
                && !RbacDataScopeType.TENANT.equals(scopeType)
                && !RbacDataScopeType.SELF.equals(scopeType)
                && !RbacDataScopeType.ORG.equals(scopeType)
                && !RbacDataScopeType.ORG_SUBTREE.equals(scopeType)
                && !RbacDataScopeType.RELATION.equals(scopeType)
                && !RbacDataScopeType.RELATION_SUBTREE.equals(scopeType)
                && !RbacDataScopeType.CUSTOM.equals(scopeType)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数据权限范围不支持");
        }
        DataScopeRule normalized = new DataScopeRule();
        normalized.setScopeType(scopeType);
        normalized.setRelationTypes(List.of());
        if (RbacDataScopeType.CUSTOM.equals(scopeType)) {
            String scopeRule = StringUtils.hasText(rule.getScopeRule()) ? rule.getScopeRule().trim() : "";
            if (!StringUtils.hasText(scopeRule)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "自定义数据权限规则不能为空");
            }
            normalized.setScopeColumn("");
            normalized.setScopeRule(scopeRule);
            return normalized;
        }
        if (isRelationScopeType(scopeType)) {
            List<String> relationTypes = normalizeRelationTypes(rule.getRelationTypes());
            if (CollectionUtils.isEmpty(relationTypes)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "关系型数据权限至少选择一个关系类型");
            }
            orgGovernanceCatalogService.validateRelationTypes(tenantId, relationTypes, "数据权限关系类型");
            normalized.setScopeColumn(StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn().trim() : "");
            normalized.setScopeRule("");
            normalized.setRelationTypes(relationTypes);
            return normalized;
        }
        normalized.setScopeColumn(StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn().trim() : "");
        normalized.setScopeRule("");
        return normalized;
    }

    /**
     * 创建继承角色的数据权限占位。
     *
     * @return 继承规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule createInheritedDataScope() {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(INHERIT_SCOPE);
        rule.setScopeColumn("");
        rule.setScopeRule("");
        rule.setRelationTypes(List.of());
        return rule;
    }

    /**
     * 创建默认租户级数据权限。
     *
     * @return 租户级规则
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule createTenantDataScope() {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.TENANT);
        rule.setScopeColumn("");
        rule.setScopeRule("");
        rule.setRelationTypes(List.of());
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
        return RbacDataScopeType.RELATION.equals(scopeType)
                || RbacDataScopeType.RELATION_SUBTREE.equals(scopeType);
    }

    /**
     * 规范化关系类型列表。
     *
     * @param relationTypes 原始列表
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> normalizeRelationTypes(List<String> relationTypes) {
        if (CollectionUtils.isEmpty(relationTypes)) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String relationType : relationTypes) {
            if (StringUtils.hasText(relationType)) {
                normalized.add(relationType.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 解析持久化关系类型列表。
     *
     * @param rawValue 原始值
     * @return 关系类型列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> parseRelationTypes(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        String normalized = rawValue.replace('，', ',').replace('；', ',');
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : normalized.split(",")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * 解析持久化 scopeRule。
     *
     * @param rule 数据权限规则
     * @return 持久化值
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolvePersistedScopeRule(DataScopeRule rule) {
        if (rule == null) {
            return "";
        }
        if (isRelationScopeType(rule.getScopeType())) {
            return String.join(",", normalizeRelationTypes(rule.getRelationTypes()));
        }
        return rule.getScopeRule();
    }

    /**
     * 生效数据权限结果。
     *
     * @param rule   生效规则
     * @param source 来源
     * @author 余炘洋
     * @date 2026-04-11
     */
    private record EffectiveDataScope(DataScopeRule rule, String source) {
    }

    /**
     * 规范化字符串。
     *
     * @param value 输入值
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
