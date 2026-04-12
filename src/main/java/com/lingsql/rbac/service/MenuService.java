package com.lingsql.rbac.service;

import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.repository.RbacMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final RbacMenuRepository menuRepository;

    /**
     * 构建当前用户的菜单树。
     *
     * @param session 登录会话
     * @return 菜单树
     * @author 余炘洋
     * @date 2026-01-23
     */
    public List<MenuView> listMenus(RbacUserSession session) {
        if (session == null || !StringUtils.hasText(session.getTenantId())) {
            return List.of();
        }
        List<RbacMenu> menus = resolveEffectiveMenus(session.getTenantId());
        List<RbacMenu> visibleMenus = menus.stream()
                .filter(menu -> menu.getVisible() == null || menu.getVisible() == 1)
                .toList();
        List<RbacMenu> filtered = retainAccessibleMenus(visibleMenus, session);

        List<MenuView> tree = buildTree(filtered);

        if (session.isExperience() && !CollectionUtils.isEmpty(session.getMenuWhitelist())) {
            Set<String> whitelist = session.getMenuWhitelist().stream()
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            return filterByWhitelist(tree, whitelist);
        }
        return tree;
    }

    /**
     * 解析租户最终生效的菜单列表。
     *
     * <p>全局菜单负责结构定义，租户菜单作为覆盖层按 `menuCode` 合并。</p>
     *
     * @param tenantId 租户ID
     * @return 生效菜单列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<RbacMenu> resolveEffectiveMenus(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        List<RbacMenu> globalMenus = menuRepository.findByTenantIdIsNull().stream()
                .filter(menu -> menu != null && isActiveStatus(menu.getStatus()))
                .toList();
        Map<String, RbacMenu> tenantOverrideMapping = new HashMap<>();
        for (RbacMenu menu : menuRepository.findByTenantId(tenantId.trim())) {
            if (menu == null || !StringUtils.hasText(menu.getMenuCode())) {
                continue;
            }
            tenantOverrideMapping.put(normalizeValue(menu.getMenuCode()), menu);
        }
        List<RbacMenu> resolvedMenus = new ArrayList<>();
        for (RbacMenu globalMenu : globalMenus) {
            RbacMenu effectiveMenu = mergeMenu(globalMenu, tenantOverrideMapping.get(normalizeValue(globalMenu.getMenuCode())));
            if (isActiveStatus(effectiveMenu == null ? null : effectiveMenu.getStatus())) {
                resolvedMenus.add(effectiveMenu);
            }
        }
        return resolvedMenus;
    }

    /**
     * 合并全局菜单与租户覆盖层。
     *
     * @param globalMenu     全局菜单
     * @param tenantOverride 租户覆盖
     * @return 生效菜单
     * @author 余炘洋
     * @date 2026-04-12
     */
    RbacMenu mergeMenu(RbacMenu globalMenu, RbacMenu tenantOverride) {
        if (globalMenu == null) {
            return null;
        }
        RbacMenu merged = new RbacMenu();
        merged.setId(globalMenu.getId());
        merged.setTenantId(globalMenu.getTenantId());
        merged.setMenuCode(globalMenu.getMenuCode());
        merged.setMenuName(globalMenu.getMenuName());
        merged.setParentId(globalMenu.getParentId());
        merged.setPath(globalMenu.getPath());
        merged.setComponent(globalMenu.getComponent());
        merged.setIcon(globalMenu.getIcon());
        merged.setSort(globalMenu.getSort());
        merged.setPermCode(globalMenu.getPermCode());
        merged.setVisible(resolveOverrideInteger(globalMenu.getVisible(), tenantOverride == null ? null : tenantOverride.getVisible()));
        merged.setStatus(resolveOverrideInteger(globalMenu.getStatus(), tenantOverride == null ? null : tenantOverride.getStatus()));
        merged.setOrgTypeCodes(resolveOverrideText(globalMenu.getOrgTypeCodes(), tenantOverride == null ? null : tenantOverride.getOrgTypeCodes()));
        merged.setOrgUnitCodes(resolveOverrideText(globalMenu.getOrgUnitCodes(), tenantOverride == null ? null : tenantOverride.getOrgUnitCodes()));
        merged.setOrgTags(resolveOverrideText(globalMenu.getOrgTags(), tenantOverride == null ? null : tenantOverride.getOrgTags()));
        merged.setRequiredRelationTypes(resolveOverrideText(globalMenu.getRequiredRelationTypes(), tenantOverride == null ? null : tenantOverride.getRequiredRelationTypes()));
        return merged;
    }

    /**
     * 保留当前会话可访问的菜单，并补齐被命中菜单的祖先节点。
     *
     * @param menus   可见菜单列表
     * @param session 登录会话
     * @return 可访问菜单列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<RbacMenu> retainAccessibleMenus(List<RbacMenu> menus, RbacUserSession session) {
        if (CollectionUtils.isEmpty(menus)) {
            return List.of();
        }
        Set<String> permissions = normalizeValues(session == null ? List.of() : session.getPermissions());
        Map<Long, RbacMenu> menuMapping = new HashMap<>();
        for (RbacMenu menu : menus) {
            if (menu.getId() != null) {
                menuMapping.put(menu.getId(), menu);
            }
        }
        LinkedHashSet<Long> accessibleIds = new LinkedHashSet<>();
        for (RbacMenu menu : menus) {
            if (!matchesPermission(menu, permissions) || !matchesOrgRule(menu, session)) {
                continue;
            }
            collectAncestorIds(accessibleIds, menu, menuMapping);
        }
        return menus.stream()
                .filter(menu -> menu.getId() != null && accessibleIds.contains(menu.getId()))
                .toList();
    }

    /**
     * 校验菜单权限编码。
     *
     * @param menu        菜单
     * @param permissions 当前权限集合
     * @return 是否命中
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean matchesPermission(RbacMenu menu, Set<String> permissions) {
        if (menu == null) {
            return false;
        }
        String permCode = menu.getPermCode();
        if (!StringUtils.hasText(permCode)) {
            return true;
        }
        return permissions.contains(normalizeValue(permCode));
    }

    /**
     * 校验菜单组织适配规则。
     *
     * <p>规则约定：</p>
     * <p>1. 未配置某一维度时，不限制该维度。</p>
     * <p>2. 同一维度内按“任一命中”处理。</p>
     * <p>3. 不同维度之间按“全部满足”处理。</p>
     *
     * @param menu    菜单
     * @param session 登录会话
     * @return 是否命中
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean matchesOrgRule(RbacMenu menu, RbacUserSession session) {
        if (menu == null) {
            return false;
        }
        if (!matchesAnyConfigured(parseConfiguredValues(menu.getOrgTypeCodes()), session == null ? List.of() : session.getMemberOrgTypeCodes())) {
            return false;
        }
        if (!matchesAnyConfigured(parseConfiguredValues(menu.getOrgUnitCodes()), session == null ? List.of() : session.getMemberOrgUnitCodes())) {
            return false;
        }
        if (!matchesAnyConfigured(parseConfiguredValues(menu.getOrgTags()), session == null ? List.of() : session.getMemberOrgTags())) {
            return false;
        }
        Set<String> requiredRelationTypes = parseConfiguredValues(menu.getRequiredRelationTypes());
        if (requiredRelationTypes.isEmpty()) {
            return true;
        }
        Set<String> grantedRelationTypes = resolveGrantedRelationTypes(session);
        return !grantedRelationTypes.isEmpty() && !java.util.Collections.disjoint(requiredRelationTypes, grantedRelationTypes);
    }

    /**
     * 判断某一维度是否命中。
     *
     * @param configuredValues 菜单配置值
     * @param actualValues     当前会话值
     * @return 是否命中
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean matchesAnyConfigured(Set<String> configuredValues, List<String> actualValues) {
        if (configuredValues.isEmpty()) {
            return true;
        }
        Set<String> actualValueSet = normalizeValues(actualValues);
        if (actualValueSet.isEmpty()) {
            return false;
        }
        return configuredValues.stream().anyMatch(actualValueSet::contains);
    }

    /**
     * 收集已授予的关系类型集合。
     *
     * @param session 登录会话
     * @return 关系类型集合
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Set<String> resolveGrantedRelationTypes(RbacUserSession session) {
        if (session == null || session.getRelationScopes() == null || session.getRelationScopes().isEmpty()) {
            return Set.of();
        }
        Set<String> relationTypes = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : session.getRelationScopes().entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                relationTypes.add(normalizeValue(entry.getKey()));
            }
        }
        return relationTypes;
    }

    /**
     * 收集命中菜单及其祖先节点。
     *
     * @param accessibleIds 可访问菜单ID集合
     * @param menu          当前菜单
     * @param menuMapping   菜单映射
     * @author 余炘洋
     * @date 2026-04-12
     */
    private void collectAncestorIds(Set<Long> accessibleIds, RbacMenu menu, Map<Long, RbacMenu> menuMapping) {
        RbacMenu current = menu;
        while (current != null && current.getId() != null && accessibleIds.add(current.getId())) {
            Long parentId = current.getParentId();
            if (parentId == null || parentId == 0L) {
                break;
            }
            current = menuMapping.get(parentId);
        }
    }

    /**
     * 解析配置值并规范化。
     *
     * @param rawValue 原始配置
     * @return 规范化后的配置集合
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Set<String> parseConfiguredValues(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Set.of();
        }
        String normalized = rawValue
                .replace('，', ',')
                .replace('；', ';')
                .replace('\n', ',')
                .replace('\r', ',');
        Set<String> values = new LinkedHashSet<>();
        for (String item : normalized.split("[,;]+")) {
            String value = normalizeValue(item);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 规范化值列表。
     *
     * @param values 原始值列表
     * @return 规范化后的集合
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Set<String> normalizeValues(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Set.of();
        }
        Set<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeValue(value);
            if (StringUtils.hasText(normalizedValue)) {
                normalizedValues.add(normalizedValue);
            }
        }
        return normalizedValues;
    }

    /**
     * 规范化单个值。
     *
     * @param value 原始值
     * @return 规范化后的值
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
     * 解析整型覆盖值。
     *
     * @param baseValue     全局值
     * @param overrideValue 覆盖值
     * @return 生效值
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Integer resolveOverrideInteger(Integer baseValue, Integer overrideValue) {
        return overrideValue == null ? baseValue : overrideValue;
    }

    /**
     * 解析文本覆盖值。`null` 继承全局，空串表示显式清空。
     *
     * @param baseValue     全局值
     * @param overrideValue 覆盖值
     * @return 生效值
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveOverrideText(String baseValue, String overrideValue) {
        return overrideValue == null ? baseValue : overrideValue;
    }

    /**
     * 判断菜单是否启用。
     *
     * @param status 状态
     * @return 是否启用
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean isActiveStatus(Integer status) {
        return status == null || status == 1;
    }

    /**
     * 构建菜单树。
     *
     * @param menus 菜单列表
     * @return 菜单树
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<MenuView> buildTree(List<RbacMenu> menus) {
        Map<Long, MenuView> mapping = new HashMap<>();
        List<MenuView> roots = new ArrayList<>();
        for (RbacMenu menu : menus) {
            MenuView view = toView(menu);
            mapping.put(menu.getId(), view);
        }
        for (RbacMenu menu : menus) {
            MenuView view = mapping.get(menu.getId());
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0) {
                roots.add(view);
            } else {
                MenuView parent = mapping.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(view);
                } else {
                    roots.add(view);
                }
            }
        }
        roots.forEach(this::sortChildren);
        roots.sort(Comparator.comparingInt(view -> view.getSort() == null ? 0 : view.getSort()));
        return roots;
    }

    /**
     * 体验账号菜单白名单过滤。
     *
     * @param menus     菜单树
     * @param whitelist 白名单
     * @return 过滤后的菜单
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<MenuView> filterByWhitelist(List<MenuView> menus, Set<String> whitelist) {
        List<MenuView> result = new ArrayList<>();
        for (MenuView menu : menus) {
            MenuView copy = copyMenu(menu);
            copy.setChildren(filterByWhitelist(menu.getChildren(), whitelist));
            if (whitelist.contains(menu.getMenuCode()) || !copy.getChildren().isEmpty()) {
                result.add(copy);
            }
        }
        return result;
    }

    /**
     * 排序子菜单。
     *
     * @param menu 菜单节点
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void sortChildren(MenuView menu) {
        if (menu.getChildren() == null) {
            return;
        }
        menu.getChildren().forEach(this::sortChildren);
        menu.getChildren().sort(Comparator.comparingInt(view -> view.getSort() == null ? 0 : view.getSort()));
    }

    /**
     * 转换为视图对象。
     *
     * @param menu 菜单实体
     * @return 菜单视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    private MenuView toView(RbacMenu menu) {
        MenuView view = new MenuView();
        view.setId(menu.getId());
        view.setMenuCode(menu.getMenuCode());
        view.setMenuName(menu.getMenuName());
        view.setPath(menu.getPath());
        view.setComponent(menu.getComponent());
        view.setIcon(menu.getIcon());
        view.setSort(menu.getSort());
        view.setVisible(menu.getVisible());
        return view;
    }

    /**
     * 复制菜单节点。
     *
     * @param menu 菜单节点
     * @return 复制节点
     * @author 余炘洋
     * @date 2026-01-23
     */
    private MenuView copyMenu(MenuView menu) {
        MenuView view = new MenuView();
        view.setId(menu.getId());
        view.setMenuCode(menu.getMenuCode());
        view.setMenuName(menu.getMenuName());
        view.setPath(menu.getPath());
        view.setComponent(menu.getComponent());
        view.setIcon(menu.getIcon());
        view.setSort(menu.getSort());
        view.setVisible(menu.getVisible());
        view.setChildren(new ArrayList<>());
        return view;
    }
}
