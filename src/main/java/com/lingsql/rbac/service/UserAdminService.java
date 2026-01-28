package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.PageResult;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserRole;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.model.UserPasswordResetRequest;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.model.UserView;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final String EXPERIENCE_ROLE_CODE = "ROLE_EXPERIENCE_TEMPLATE";

    private final RbacUserRepository userRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final RbacRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PageParamResolver pageParamResolver;

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
    public UserView createUser(UserCreateRequest request, String tenantId, boolean sysAdmin) {
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
        validatePassword(request.getPassword());
        RbacUser user = new RbacUser();
        user.setTenantId(resolvedTenant);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(normalize(request.getNickname()));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setIsExperience(request.getIsExperience() == null ? 0 : request.getIsExperience());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user = userRepository.save(user);
        applyUserRoles(user, request.getRoleIds(), sysAdmin);
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
        if (request != null) {
            boolean statusDisabled = request.getStatus() != null && request.getStatus() == 0;
            boolean experienceChanged = request.getIsExperience() != null && !request.getIsExperience().equals(originalExperience);
            boolean statusChanged = request.getStatus() != null && !request.getStatus().equals(originalStatus);
            if (statusDisabled || rolesUpdated || experienceChanged || statusChanged) {
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
    public void resetPassword(Long userId, UserPasswordResetRequest request, String tenantId, boolean sysAdmin) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户ID不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getNewPassword())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "新密码不能为空");
        }
        validatePassword(request.getNewPassword());
        RbacUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
        validateTenantAccess(user.getTenantId(), tenantId, sysAdmin);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        tokenService.revokeUserTokens(user.getId());
        refreshTokenService.revokeByUserId(user.getId());
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

    /**
     * 校验密码强度。
     *
     * @param password 密码
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码不能为空");
        }
        String trimmed = password.trim();
        if (trimmed.length() < 8 || trimmed.length() > 32) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码长度需在 8-32 位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码必须包含字母和数字");
        }
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
        view.setRoleIds(userRoleRepository.findRoleIdsByUserId(user.getId()));
        return view;
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
