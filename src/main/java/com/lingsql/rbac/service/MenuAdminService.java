package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.model.MenuAdminView;
import com.lingsql.rbac.model.MenuPreviewRequest;
import com.lingsql.rbac.model.MenuRuleUpdateRequest;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.model.TenantMenuAdminView;
import com.lingsql.rbac.model.TenantMenuRuleUpdateRequest;
import com.lingsql.rbac.repository.RbacMenuRepository;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 菜单治理服务。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Service
@RequiredArgsConstructor
public class MenuAdminService {

    private static final String PREVIEW_RELATION_SENTINEL = "__PREVIEW__";

    private final RbacMenuRepository menuRepository;
    private final MenuService menuService;

    /**
     * 查询全局菜单规则树。
     *
     * @return 菜单规则树
     * @author 余炘洋
     * @date 2026-04-12
     */
    public List<MenuAdminView> listGlobalMenus() {
        return buildTree(menuRepository.findByTenantIdIsNull());
    }

    /**
     * 查询租户级菜单覆盖树。
     *
     * @param requestTenant 请求租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 租户覆盖树
     * @author 余炘洋
     * @date 2026-04-12
     */
    public List<TenantMenuAdminView> listTenantMenus(String requestTenant, String sessionTenant, boolean sysAdmin) {
        String tenantId = resolveTenantId(requestTenant, sessionTenant, sysAdmin);
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        List<RbacMenu> globalMenus = menuRepository.findByTenantIdIsNull();
        Map<String, RbacMenu> tenantOverrideMapping = new LinkedHashMap<>();
        for (RbacMenu override : menuRepository.findByTenantId(tenantId)) {
            if (override == null || !StringUtils.hasText(override.getMenuCode())) {
                continue;
            }
            tenantOverrideMapping.put(normalizeValue(override.getMenuCode()), override);
        }
        Map<Long, RbacMenu> globalMapping = new LinkedHashMap<>();
        Map<Long, TenantMenuAdminView> viewMapping = new LinkedHashMap<>();
        for (RbacMenu globalMenu : globalMenus) {
            if (globalMenu != null && globalMenu.getId() != null) {
                globalMapping.put(globalMenu.getId(), globalMenu);
            }
        }
        for (RbacMenu globalMenu : globalMenus) {
            if (globalMenu == null || globalMenu.getId() == null || !StringUtils.hasText(globalMenu.getMenuCode())) {
                continue;
            }
            RbacMenu tenantOverride = tenantOverrideMapping.get(normalizeValue(globalMenu.getMenuCode()));
            String parentMenuCode = globalMenu.getParentId() == null
                    ? null
                    : java.util.Optional.ofNullable(globalMapping.get(globalMenu.getParentId()))
                    .map(RbacMenu::getMenuCode)
                    .orElse(null);
            viewMapping.put(globalMenu.getId(), toTenantView(globalMenu, tenantOverride, tenantId, parentMenuCode));
        }
        List<TenantMenuAdminView> roots = new ArrayList<>();
        for (RbacMenu globalMenu : globalMenus) {
            if (globalMenu == null || globalMenu.getId() == null) {
                continue;
            }
            TenantMenuAdminView current = viewMapping.get(globalMenu.getId());
            if (current == null) {
                continue;
            }
            Long parentId = globalMenu.getParentId();
            if (parentId == null || parentId == 0L) {
                roots.add(current);
            } else {
                TenantMenuAdminView parent = viewMapping.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(current);
                } else {
                    roots.add(current);
                }
            }
        }
        roots.forEach(this::sortTenantChildren);
        roots.sort(tenantMenuComparator());
        return roots;
    }

    /**
     * 更新全局菜单规则。
     *
     * @param menuId  菜单ID
     * @param request 更新请求
     * @return 更新后的菜单视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    public MenuAdminView updateGlobalMenuRule(Long menuId, MenuRuleUpdateRequest request) {
        if (menuId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜单ID不能为空");
        }
        RbacMenu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在"));
        if (StringUtils.hasText(menu.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前仅支持维护全局菜单规则");
        }
        applyRuleFields(menu, request);
        menu.setUpdateTime(LocalDateTime.now());
        RbacMenu saved = menuRepository.save(menu);
        String parentMenuCode = menu.getParentId() == null
                ? null
                : menuRepository.findById(menu.getParentId()).map(RbacMenu::getMenuCode).orElse(null);
        return toView(saved, parentMenuCode);
    }

    /**
     * 保存租户级菜单覆盖。
     *
     * @param menuCode      菜单编码
     * @param request       更新请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 更新后的租户菜单视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    public TenantMenuAdminView upsertTenantMenuRule(
            String menuCode,
            TenantMenuRuleUpdateRequest request,
            String sessionTenant,
            boolean sysAdmin
    ) {
        String normalizedMenuCode = trimToNull(menuCode);
        if (!StringUtils.hasText(normalizedMenuCode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜单编码不能为空");
        }
        String tenantId = resolveTenantId(request == null ? null : request.getTenantId(), sessionTenant, sysAdmin);
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        RbacMenu globalMenu = menuRepository.findByTenantIdIsNullAndMenuCode(normalizedMenuCode)
                .orElseThrow(() -> new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在"));
        RbacMenu override = menuRepository.findByTenantIdAndMenuCode(tenantId, globalMenu.getMenuCode())
                .orElseGet(() -> createTenantOverrideSkeleton(globalMenu, tenantId));
        applyTenantOverrideFields(override, request);
        override.setUpdateTime(LocalDateTime.now());
        RbacMenu saved = menuRepository.save(override);
        String parentMenuCode = globalMenu.getParentId() == null
                ? null
                : menuRepository.findById(globalMenu.getParentId()).map(RbacMenu::getMenuCode).orElse(null);
        return toTenantView(globalMenu, saved, tenantId, parentMenuCode);
    }

    /**
     * 清除租户级菜单覆盖。
     *
     * @param menuCode      菜单编码
     * @param requestTenant 请求租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @author 余炘洋
     * @date 2026-04-12
     */
    public void clearTenantMenuRule(String menuCode, String requestTenant, String sessionTenant, boolean sysAdmin) {
        String normalizedMenuCode = trimToNull(menuCode);
        if (!StringUtils.hasText(normalizedMenuCode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜单编码不能为空");
        }
        String tenantId = resolveTenantId(requestTenant, sessionTenant, sysAdmin);
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        menuRepository.deleteByTenantIdAndMenuCode(tenantId, normalizedMenuCode);
    }

    /**
     * 预览指定组织上下文命中的菜单树。
     *
     * @param request       预览请求
     * @param sessionTenant 当前会话租户
     * @param sysAdmin      是否系统管理员
     * @return 菜单树
     * @author 余炘洋
     * @date 2026-04-12
     */
    public List<MenuView> previewMenus(MenuPreviewRequest request, String sessionTenant, boolean sysAdmin) {
        String tenantId = resolveTenantId(request == null ? null : request.getTenantId(), sessionTenant, sysAdmin);
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        RbacUserSession session = new RbacUserSession();
        session.setTenantId(tenantId);
        session.setPermissions(normalizeValues(request == null ? null : request.getPermissionCodes()));
        session.setMemberOrgTypeCodes(normalizeValues(request == null ? null : request.getOrgTypeCodes()));
        session.setMemberOrgUnitCodes(normalizeValues(request == null ? null : request.getOrgUnitCodes()));
        session.setMemberOrgTags(normalizeValues(request == null ? null : request.getOrgTags()));
        session.setRelationScopes(buildRelationScopes(request == null ? null : request.getRelationTypes()));
        session.setExperience(request != null && Boolean.TRUE.equals(request.getExperience()));
        session.setMenuWhitelist(normalizeValues(request == null ? null : request.getMenuWhitelist()));
        return menuService.listMenus(session);
    }

    /**
     * 构建菜单树。
     *
     * @param menus 菜单列表
     * @return 菜单树
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<MenuAdminView> buildTree(List<RbacMenu> menus) {
        if (CollectionUtils.isEmpty(menus)) {
            return List.of();
        }
        Map<Long, RbacMenu> menuMapping = new LinkedHashMap<>();
        Map<Long, MenuAdminView> viewMapping = new LinkedHashMap<>();
        for (RbacMenu menu : menus) {
            if (menu.getId() != null) {
                menuMapping.put(menu.getId(), menu);
            }
        }
        for (RbacMenu menu : menus) {
            String parentMenuCode = menu.getParentId() == null
                    ? null
                    : java.util.Optional.ofNullable(menuMapping.get(menu.getParentId()))
                    .map(RbacMenu::getMenuCode)
                    .orElse(null);
            viewMapping.put(menu.getId(), toView(menu, parentMenuCode));
        }
        List<MenuAdminView> roots = new ArrayList<>();
        for (RbacMenu menu : menus) {
            MenuAdminView current = viewMapping.get(menu.getId());
            if (current == null) {
                continue;
            }
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0L) {
                roots.add(current);
                continue;
            }
            MenuAdminView parent = viewMapping.get(parentId);
            if (parent == null) {
                roots.add(current);
                continue;
            }
            parent.getChildren().add(current);
        }
        roots.forEach(this::sortChildren);
        roots.sort(menuComparator());
        return roots;
    }

    /**
     * 排序子菜单。
     *
     * @param menu 菜单节点
     * @author 余炘洋
     * @date 2026-04-12
     */
    private void sortChildren(MenuAdminView menu) {
        if (menu == null || CollectionUtils.isEmpty(menu.getChildren())) {
            return;
        }
        menu.getChildren().forEach(this::sortChildren);
        menu.getChildren().sort(menuComparator());
    }

    /**
     * 排序租户级子菜单。
     *
     * @param menu 菜单节点
     * @author 余炘洋
     * @date 2026-04-12
     */
    private void sortTenantChildren(TenantMenuAdminView menu) {
        if (menu == null || CollectionUtils.isEmpty(menu.getChildren())) {
            return;
        }
        menu.getChildren().forEach(this::sortTenantChildren);
        menu.getChildren().sort(tenantMenuComparator());
    }

    /**
     * 菜单排序器。
     *
     * @return 排序器
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Comparator<MenuAdminView> menuComparator() {
        return Comparator.comparingInt((MenuAdminView item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparingLong(item -> item.getId() == null ? 0L : item.getId());
    }

    /**
     * 租户菜单排序器。
     *
     * @return 排序器
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Comparator<TenantMenuAdminView> tenantMenuComparator() {
        return Comparator.comparingInt((TenantMenuAdminView item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(item -> trimToNull(item.getMenuCode()), Comparator.nullsLast(String::compareTo));
    }

    /**
     * 转换为菜单治理视图。
     *
     * @param menu           菜单实体
     * @param parentMenuCode 父级菜单编码
     * @return 菜单治理视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    private MenuAdminView toView(RbacMenu menu, String parentMenuCode) {
        MenuAdminView view = new MenuAdminView();
        view.setId(menu.getId());
        view.setTenantId(menu.getTenantId());
        view.setMenuCode(menu.getMenuCode());
        view.setMenuName(menu.getMenuName());
        view.setParentId(menu.getParentId());
        view.setParentMenuCode(parentMenuCode);
        view.setPath(menu.getPath());
        view.setComponent(menu.getComponent());
        view.setIcon(menu.getIcon());
        view.setSort(menu.getSort());
        view.setVisible(menu.getVisible());
        view.setStatus(menu.getStatus());
        view.setPermCode(menu.getPermCode());
        view.setOrgTypeCodes(parseConfiguredValues(menu.getOrgTypeCodes()));
        view.setOrgUnitCodes(parseConfiguredValues(menu.getOrgUnitCodes()));
        view.setOrgTags(parseConfiguredValues(menu.getOrgTags()));
        view.setRequiredRelationTypes(parseConfiguredValues(menu.getRequiredRelationTypes()));
        return view;
    }

    /**
     * 应用规则字段。
     *
     * @param menu    菜单
     * @param request 请求体
     * @author 余炘洋
     * @date 2026-04-12
     */
    private void applyRuleFields(RbacMenu menu, MenuRuleUpdateRequest request) {
        menu.setOrgTypeCodes(joinValues(request == null ? null : request.getOrgTypeCodes()));
        menu.setOrgUnitCodes(joinValues(request == null ? null : request.getOrgUnitCodes()));
        menu.setOrgTags(joinValues(request == null ? null : request.getOrgTags()));
        menu.setRequiredRelationTypes(joinValues(request == null ? null : request.getRequiredRelationTypes()));
    }

    /**
     * 应用租户覆盖字段。
     *
     * @param menu    菜单
     * @param request 请求体
     * @author 余炘洋
     * @date 2026-04-12
     */
    private void applyTenantOverrideFields(RbacMenu menu, TenantMenuRuleUpdateRequest request) {
        if (menu == null) {
            return;
        }
        menu.setVisible(request == null ? null : request.getVisible());
        menu.setStatus(request == null ? null : request.getStatus());
        menu.setOrgTypeCodes(joinOverrideValues(request == null ? null : request.getOrgTypeCodes()));
        menu.setOrgUnitCodes(joinOverrideValues(request == null ? null : request.getOrgUnitCodes()));
        menu.setOrgTags(joinOverrideValues(request == null ? null : request.getOrgTags()));
        menu.setRequiredRelationTypes(joinOverrideValues(request == null ? null : request.getRequiredRelationTypes()));
    }

    /**
     * 创建租户覆盖骨架。
     *
     * @param globalMenu 全局菜单
     * @param tenantId   租户ID
     * @return 覆盖实体
     * @author 余炘洋
     * @date 2026-04-12
     */
    private RbacMenu createTenantOverrideSkeleton(RbacMenu globalMenu, String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        RbacMenu override = new RbacMenu();
        override.setTenantId(tenantId);
        override.setMenuCode(globalMenu.getMenuCode());
        override.setMenuName(globalMenu.getMenuName());
        override.setParentId(globalMenu.getParentId());
        override.setPath(globalMenu.getPath());
        override.setComponent(globalMenu.getComponent());
        override.setIcon(globalMenu.getIcon());
        override.setSort(globalMenu.getSort());
        override.setPermCode(globalMenu.getPermCode());
        override.setVisible(null);
        override.setStatus(null);
        override.setOrgTypeCodes(null);
        override.setOrgUnitCodes(null);
        override.setOrgTags(null);
        override.setRequiredRelationTypes(null);
        override.setCreateTime(now);
        override.setUpdateTime(now);
        return override;
    }

    /**
     * 转换为租户菜单治理视图。
     *
     * @param globalMenu     全局菜单
     * @param tenantOverride 租户覆盖
     * @param tenantId       租户ID
     * @param parentMenuCode 父级菜单编码
     * @return 租户菜单视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    private TenantMenuAdminView toTenantView(RbacMenu globalMenu, RbacMenu tenantOverride, String tenantId, String parentMenuCode) {
        RbacMenu effective = menuService.mergeMenu(globalMenu, tenantOverride);
        TenantMenuAdminView view = new TenantMenuAdminView();
        view.setId(globalMenu.getId());
        view.setOverrideId(tenantOverride == null ? null : tenantOverride.getId());
        view.setTenantId(tenantId);
        view.setMenuCode(globalMenu.getMenuCode());
        view.setMenuName(globalMenu.getMenuName());
        view.setParentId(globalMenu.getParentId());
        view.setParentMenuCode(parentMenuCode);
        view.setPath(globalMenu.getPath());
        view.setComponent(globalMenu.getComponent());
        view.setIcon(globalMenu.getIcon());
        view.setSort(globalMenu.getSort());
        view.setPermCode(globalMenu.getPermCode());
        view.setVisible(effective == null ? null : effective.getVisible());
        view.setStatus(effective == null ? null : effective.getStatus());
        view.setOrgTypeCodes(parseConfiguredValues(effective == null ? null : effective.getOrgTypeCodes()));
        view.setOrgUnitCodes(parseConfiguredValues(effective == null ? null : effective.getOrgUnitCodes()));
        view.setOrgTags(parseConfiguredValues(effective == null ? null : effective.getOrgTags()));
        view.setRequiredRelationTypes(parseConfiguredValues(effective == null ? null : effective.getRequiredRelationTypes()));
        view.setOverrideActive(tenantOverride != null);
        view.setBaseVisible(globalMenu.getVisible());
        view.setBaseStatus(globalMenu.getStatus());
        view.setBaseOrgTypeCodes(parseConfiguredValues(globalMenu.getOrgTypeCodes()));
        view.setBaseOrgUnitCodes(parseConfiguredValues(globalMenu.getOrgUnitCodes()));
        view.setBaseOrgTags(parseConfiguredValues(globalMenu.getOrgTags()));
        view.setBaseRequiredRelationTypes(parseConfiguredValues(globalMenu.getRequiredRelationTypes()));
        view.setOverrideVisible(tenantOverride == null ? null : tenantOverride.getVisible());
        view.setOverrideStatus(tenantOverride == null ? null : tenantOverride.getStatus());
        view.setOverrideOrgTypeCodes(parseConfiguredValues(tenantOverride == null ? null : tenantOverride.getOrgTypeCodes()));
        view.setOverrideOrgUnitCodes(parseConfiguredValues(tenantOverride == null ? null : tenantOverride.getOrgUnitCodes()));
        view.setOverrideOrgTags(parseConfiguredValues(tenantOverride == null ? null : tenantOverride.getOrgTags()));
        view.setOverrideRequiredRelationTypes(parseConfiguredValues(tenantOverride == null ? null : tenantOverride.getRequiredRelationTypes()));
        return view;
    }

    /**
     * 构建关系语义映射。
     *
     * @param relationTypes 关系类型列表
     * @return 关系语义映射
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Map<String, List<String>> buildRelationScopes(List<String> relationTypes) {
        Map<String, List<String>> relationScopes = new LinkedHashMap<>();
        for (String relationType : normalizeValues(relationTypes)) {
            relationScopes.put(relationType, List.of(PREVIEW_RELATION_SENTINEL));
        }
        return relationScopes;
    }

    /**
     * 解析租户ID。
     *
     * @param requestTenant 请求租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 租户ID
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveTenantId(String requestTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            return StringUtils.hasText(requestTenant) ? requestTenant.trim() : trimToNull(sessionTenant);
        }
        if (!StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        if (StringUtils.hasText(requestTenant) && !sessionTenant.trim().equalsIgnoreCase(requestTenant.trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return sessionTenant.trim();
    }

    /**
     * 解析配置列表。
     *
     * @param rawValue 原始文本
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> parseConfiguredValues(String rawValue) {
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
            String value = normalizeValue(item);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * 拼接配置值。
     *
     * @param values 原始值列表
     * @return 逗号拼接结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String joinValues(List<String> values) {
        List<String> normalizedValues = normalizeValues(values);
        if (normalizedValues.isEmpty()) {
            return null;
        }
        return String.join(",", normalizedValues);
    }

    /**
     * 拼接租户覆盖值，空数组表示显式清空规则。
     *
     * @param values 原始值
     * @return 覆盖结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String joinOverrideValues(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> normalizedValues = normalizeValues(values);
        if (normalizedValues.isEmpty()) {
            return "";
        }
        return String.join(",", normalizedValues);
    }

    /**
     * 规范化值列表。
     *
     * @param values 原始值
     * @return 规范化后的值列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> normalizeValues(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeValue(value);
            if (StringUtils.hasText(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 规范化单值。
     *
     * @param value 原始值
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化普通文本，不做大小写转换。
     *
     * @param value 原始值
     * @return 去空白后的结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
