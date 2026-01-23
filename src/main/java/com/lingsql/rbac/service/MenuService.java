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
import java.util.List;
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
        List<RbacMenu> menus = menuRepository.findActiveMenus(session.getTenantId());
        Set<String> permissions = new HashSet<>(session.getPermissions());
        List<RbacMenu> filtered = menus.stream()
                .filter(menu -> menu.getVisible() == null || menu.getVisible() == 1)
                .filter(menu -> {
                    String permCode = menu.getPermCode();
                    if (!StringUtils.hasText(permCode)) {
                        return true;
                    }
                    return permissions.contains(permCode);
                })
                .toList();

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
