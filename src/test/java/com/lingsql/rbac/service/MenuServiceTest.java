package com.lingsql.rbac.service;

import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.repository.RbacMenuRepository;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MenuServiceTest {

    @Test
    void listMenusShouldFilterByOrganizationContextAndKeepAncestor() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);

        RbacMenu parent = buildMenu(1L, "MENU_ADMIN", null, "MENU:ADMIN");
        RbacMenu chain = buildMenu(2L, "MENU_CHAIN", 1L, "MENU:CHAIN");
        chain.setOrgTypeCodes("CHAIN_STORE");
        RbacMenu tagDriven = buildMenu(3L, "MENU_DIRECT", 1L, "MENU:DIRECT");
        tagDriven.setOrgTags("直营");
        RbacMenu relationDriven = buildMenu(4L, "MENU_SUPERVISE", 1L, "MENU:SUPERVISE");
        relationDriven.setRequiredRelationTypes("SUPERVISE");
        RbacMenu blocked = buildMenu(5L, "MENU_GOV", 1L, "MENU:GOV");
        blocked.setOrgTypeCodes("GOV_ORG");

        when(repository.findByTenantIdIsNull()).thenReturn(List.of(parent, chain, tagDriven, relationDriven, blocked));
        when(repository.findByTenantId("TENANT_A")).thenReturn(List.of());

        RbacUserSession session = new RbacUserSession();
        session.setTenantId("TENANT_A");
        session.setPermissions(List.of("MENU:CHAIN", "MENU:DIRECT", "MENU:SUPERVISE"));
        session.setMemberOrgTypeCodes(List.of("CHAIN_STORE"));
        session.setMemberOrgUnitCodes(List.of("STORE_001"));
        session.setMemberOrgTags(List.of("直营"));
        Map<String, List<String>> relationScopes = new LinkedHashMap<>();
        relationScopes.put("SUPERVISE", List.of("REGION_EAST"));
        session.setRelationScopes(relationScopes);

        List<MenuView> menus = menuService.listMenus(session);

        assertEquals(1, menus.size());
        assertEquals("MENU_ADMIN", menus.getFirst().getMenuCode());
        assertEquals(3, menus.getFirst().getChildren().size());
        assertTrue(menus.getFirst().getChildren().stream().anyMatch(item -> "MENU_CHAIN".equals(item.getMenuCode())));
        assertTrue(menus.getFirst().getChildren().stream().anyMatch(item -> "MENU_DIRECT".equals(item.getMenuCode())));
        assertTrue(menus.getFirst().getChildren().stream().anyMatch(item -> "MENU_SUPERVISE".equals(item.getMenuCode())));
    }

    @Test
    void listMenusShouldMatchDirectOrgUnitCodes() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);

        RbacMenu menu = buildMenu(10L, "MENU_STORE", null, "MENU:STORE");
        menu.setOrgUnitCodes("STORE_001,STORE_002");
        when(repository.findByTenantIdIsNull()).thenReturn(List.of(menu));
        when(repository.findByTenantId("TENANT_A")).thenReturn(List.of());

        RbacUserSession session = new RbacUserSession();
        session.setTenantId("TENANT_A");
        session.setPermissions(List.of("MENU:STORE"));
        session.setMemberOrgUnitCodes(List.of("STORE_002"));

        List<MenuView> menus = menuService.listMenus(session);

        assertEquals(1, menus.size());
        assertEquals("MENU_STORE", menus.getFirst().getMenuCode());
    }

    @Test
    void listMenusShouldApplyTenantOverrideForVisibilityAndRules() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);

        RbacMenu globalMenu = buildMenu(11L, "MENU_STORE", null, "MENU:STORE");
        globalMenu.setVisible(1);
        globalMenu.setStatus(1);
        globalMenu.setOrgTypeCodes("CHAIN_STORE");
        RbacMenu tenantOverride = buildMenu(99L, "MENU_STORE", null, "MENU:STORE");
        tenantOverride.setTenantId("TENANT_A");
        tenantOverride.setVisible(0);
        tenantOverride.setStatus(null);
        tenantOverride.setOrgTypeCodes("");
        when(repository.findByTenantIdIsNull()).thenReturn(List.of(globalMenu));
        when(repository.findByTenantId("TENANT_A")).thenReturn(List.of(tenantOverride));

        RbacUserSession session = new RbacUserSession();
        session.setTenantId("TENANT_A");
        session.setPermissions(List.of("MENU:STORE"));
        session.setMemberOrgTypeCodes(List.of("CHAIN_STORE"));

        List<MenuView> menus = menuService.listMenus(session);

        assertEquals(0, menus.size());
        List<RbacMenu> effectiveMenus = menuService.resolveEffectiveMenus("TENANT_A");
        assertEquals("", effectiveMenus.getFirst().getOrgTypeCodes());
        assertEquals(0, effectiveMenus.getFirst().getVisible());
        assertEquals(1, effectiveMenus.getFirst().getStatus());
    }

    private RbacMenu buildMenu(Long id, String menuCode, Long parentId, String permCode) {
        RbacMenu menu = new RbacMenu();
        menu.setId(id);
        menu.setTenantId(null);
        menu.setMenuCode(menuCode);
        menu.setMenuName(menuCode);
        menu.setParentId(parentId);
        menu.setPath("/" + menuCode.toLowerCase());
        menu.setComponent("View");
        menu.setSort(id.intValue());
        menu.setVisible(1);
        menu.setStatus(1);
        menu.setPermCode(permCode);
        return menu;
    }
}
