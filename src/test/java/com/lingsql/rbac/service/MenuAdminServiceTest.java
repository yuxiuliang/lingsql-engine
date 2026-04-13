package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.service.OrgGovernanceCatalogService;
import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.model.MenuAdminView;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuPreviewRequest;
import com.lingsql.rbac.model.MenuRuleUpdateRequest;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.model.TenantMenuAdminView;
import com.lingsql.rbac.model.TenantMenuRuleUpdateRequest;
import com.lingsql.rbac.repository.RbacMenuRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuAdminServiceTest {

    @Test
    void listGlobalMenusShouldBuildTreeAndParseRules() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuAdminService service = new MenuAdminService(
                repository,
                Mockito.mock(MenuService.class),
                Mockito.mock(OrgGovernanceCatalogService.class)
        );

        RbacMenu parent = buildMenu(1L, "MENU_ADMIN", null);
        RbacMenu child = buildMenu(2L, "MENU_CHAIN", 1L);
        child.setOrgTypeCodes("CHAIN_STORE");
        child.setOrgTags("直营,零售");
        when(repository.findByTenantIdIsNull()).thenReturn(List.of(parent, child));

        List<MenuAdminView> result = service.listGlobalMenus();

        assertEquals(1, result.size());
        assertEquals("MENU_ADMIN", result.getFirst().getMenuCode());
        assertEquals(1, result.getFirst().getChildren().size());
        assertEquals(List.of("CHAIN_STORE"), result.getFirst().getChildren().getFirst().getOrgTypeCodes());
        assertEquals(List.of("直营".toUpperCase(), "零售".toUpperCase()), result.getFirst().getChildren().getFirst().getOrgTags());
    }

    @Test
    void updateGlobalMenuRuleShouldNormalizeAndPersistRules() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuAdminService service = new MenuAdminService(
                repository,
                Mockito.mock(MenuService.class),
                Mockito.mock(OrgGovernanceCatalogService.class)
        );

        RbacMenu menu = buildMenu(5L, "MENU_CHAIN", 1L);
        when(repository.findById(5L)).thenReturn(Optional.of(menu));
        when(repository.findById(1L)).thenReturn(Optional.of(buildMenu(1L, "MENU_ADMIN", null)));
        when(repository.save(any(RbacMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuRuleUpdateRequest request = new MenuRuleUpdateRequest();
        request.setOrgTypeCodes(List.of(" chain_store ", "CHAIN_STORE"));
        request.setOrgUnitCodes(List.of("store_001"));
        request.setOrgTags(List.of("直营"));
        request.setRequiredRelationTypes(List.of("supervise"));

        MenuAdminView result = service.updateGlobalMenuRule(5L, request);

        assertEquals("MENU_ADMIN", result.getParentMenuCode());
        assertEquals(List.of("CHAIN_STORE"), result.getOrgTypeCodes());
        assertEquals(List.of("STORE_001"), result.getOrgUnitCodes());
        assertEquals(List.of("SUPERVISE"), result.getRequiredRelationTypes());
        verify(repository).save(argThat(saved ->
                "CHAIN_STORE".equals(saved.getOrgTypeCodes())
                        && "STORE_001".equals(saved.getOrgUnitCodes())
                        && "直营".toUpperCase().equals(saved.getOrgTags())
                        && "SUPERVISE".equals(saved.getRequiredRelationTypes())
        ));
    }

    @Test
    void previewMenusShouldUseSessionTenantForTenantAdmin() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = Mockito.mock(MenuService.class);
        MenuAdminService service = new MenuAdminService(
                repository,
                menuService,
                Mockito.mock(OrgGovernanceCatalogService.class)
        );
        when(menuService.listMenus(any())).thenReturn(List.of(new MenuView()));

        MenuPreviewRequest request = new MenuPreviewRequest();
        request.setPermissionCodes(List.of("menu:chain"));
        request.setOrgTypeCodes(List.of("chain_store"));
        request.setRelationTypes(List.of("service"));

        List<MenuView> result = service.previewMenus(request, "tenant-a", false);

        assertEquals(1, result.size());
        verify(menuService).listMenus(argThat(session ->
                "tenant-a".equals(session.getTenantId())
                        && session.getPermissions().equals(List.of("MENU:CHAIN"))
                        && session.getMemberOrgTypeCodes().equals(List.of("CHAIN_STORE"))
                        && session.getRelationScopes().containsKey("SERVICE")
        ));
    }

    @Test
    void previewMenusShouldRejectCrossTenantPreviewForTenantAdmin() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuAdminService service = new MenuAdminService(
                repository,
                Mockito.mock(MenuService.class),
                Mockito.mock(OrgGovernanceCatalogService.class)
        );
        MenuPreviewRequest request = new MenuPreviewRequest();
        request.setTenantId("tenant-b");

        BizException ex = assertThrows(BizException.class, () -> service.previewMenus(request, "tenant-a", false));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void listTenantMenusShouldMergeGlobalAndTenantOverride() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);
        MenuAdminService service = new MenuAdminService(
                repository,
                menuService,
                Mockito.mock(OrgGovernanceCatalogService.class)
        );

        RbacMenu globalMenu = buildMenu(8L, "MENU_CHAIN", null);
        globalMenu.setVisible(1);
        globalMenu.setStatus(1);
        globalMenu.setOrgTypeCodes("CHAIN_STORE");
        RbacMenu tenantOverride = buildMenu(18L, "MENU_CHAIN", null);
        tenantOverride.setTenantId("tenant-a");
        tenantOverride.setVisible(0);
        tenantOverride.setStatus(null);
        tenantOverride.setOrgTypeCodes("");
        when(repository.findByTenantIdIsNull()).thenReturn(List.of(globalMenu));
        when(repository.findByTenantId("tenant-a")).thenReturn(List.of(tenantOverride));

        List<TenantMenuAdminView> result = service.listTenantMenus("tenant-a", "tenant-a", false);

        assertEquals(1, result.size());
        assertEquals(true, result.getFirst().isOverrideActive());
        assertEquals(0, result.getFirst().getVisible());
        assertEquals(List.of(), result.getFirst().getOrgTypeCodes());
        assertEquals(List.of("CHAIN_STORE"), result.getFirst().getBaseOrgTypeCodes());
    }

    @Test
    void upsertTenantMenuRuleShouldCreateOverrideWithExplicitBlankRules() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);
        OrgGovernanceCatalogService orgGovernanceCatalogService = Mockito.mock(OrgGovernanceCatalogService.class);
        MenuAdminService service = new MenuAdminService(repository, menuService, orgGovernanceCatalogService);

        RbacMenu globalMenu = buildMenu(5L, "MENU_CHAIN", 1L);
        globalMenu.setVisible(1);
        globalMenu.setStatus(1);
        globalMenu.setOrgTypeCodes("CHAIN_STORE");
        when(repository.findByTenantIdIsNullAndMenuCode("MENU_CHAIN")).thenReturn(Optional.of(globalMenu));
        when(repository.findByTenantIdAndMenuCode("tenant-a", "MENU_CHAIN")).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(buildMenu(1L, "MENU_ADMIN", null)));
        when(repository.save(any(RbacMenu.class))).thenAnswer(invocation -> {
            RbacMenu menu = invocation.getArgument(0);
            menu.setId(15L);
            return menu;
        });

        TenantMenuRuleUpdateRequest request = new TenantMenuRuleUpdateRequest();
        request.setTenantId("tenant-a");
        request.setVisible(0);
        request.setStatus(null);
        request.setOrgTypeCodes(List.of());
        request.setRequiredRelationTypes(List.of("service"));

        TenantMenuAdminView result = service.upsertTenantMenuRule("MENU_CHAIN", request, "tenant-a", false);

        assertEquals(true, result.isOverrideActive());
        assertEquals(0, result.getVisible());
        assertEquals(List.of(), result.getOrgTypeCodes());
        assertEquals(List.of("SERVICE"), result.getRequiredRelationTypes());
        verify(repository).save(argThat(saved ->
                "tenant-a".equals(saved.getTenantId())
                        && "MENU_CHAIN".equals(saved.getMenuCode())
                        && saved.getVisible() == 0
                        && saved.getStatus() == null
                        && "".equals(saved.getOrgTypeCodes())
                        && "SERVICE".equals(saved.getRequiredRelationTypes())
        ));
    }

    @Test
    void clearTenantMenuRuleShouldDeleteOverride() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);
        MenuAdminService service = new MenuAdminService(
                repository,
                menuService,
                Mockito.mock(OrgGovernanceCatalogService.class)
        );

        service.clearTenantMenuRule("MENU_CHAIN", "tenant-a", "tenant-a", false);

        verify(repository).deleteByTenantIdAndMenuCode("tenant-a", "MENU_CHAIN");
    }

    @Test
    void upsertTenantMenuRuleShouldRejectUnknownTenantDimension() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);
        OrgGovernanceCatalogService orgGovernanceCatalogService = Mockito.mock(OrgGovernanceCatalogService.class);
        MenuAdminService service = new MenuAdminService(repository, menuService, orgGovernanceCatalogService);

        TenantMenuRuleUpdateRequest request = new TenantMenuRuleUpdateRequest();
        request.setTenantId("tenant-a");
        request.setOrgTypeCodes(List.of("UNKNOWN_TYPE"));

        Mockito.doThrow(new BizException(ErrorCode.INVALID_PARAM, "组织类型编码存在未定义项: UNKNOWN_TYPE"))
                .when(orgGovernanceCatalogService)
                .validateTenantSelections("tenant-a", List.of("UNKNOWN_TYPE"), List.of(), List.of(), List.of());

        BizException ex = assertThrows(BizException.class,
                () -> service.upsertTenantMenuRule("MENU_CHAIN", request, "tenant-a", false));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
    }

    @Test
    void previewGlobalRuleImpactShouldReportMatchedMenuDiff() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);
        OrgGovernanceCatalogService orgGovernanceCatalogService = Mockito.mock(OrgGovernanceCatalogService.class);
        MenuAdminService service = new MenuAdminService(repository, menuService, orgGovernanceCatalogService);

        RbacMenu parent = buildMenu(1L, "MENU_ADMIN", null);
        parent.setPermCode(null);
        RbacMenu child = buildMenu(2L, "MENU_CHAIN", 1L);
        child.setOrgTypeCodes("CHAIN_STORE");

        when(repository.findByTenantIdIsNull()).thenReturn(List.of(parent, child));
        when(repository.findByTenantId("tenant-a")).thenReturn(List.of());
        when(repository.findById(2L)).thenReturn(Optional.of(child));

        MenuImpactPreviewRequest request = new MenuImpactPreviewRequest();
        request.setTenantId("tenant-a");
        request.setMenuId(2L);
        MenuRuleUpdateRequest globalRule = new MenuRuleUpdateRequest();
        globalRule.setOrgTypeCodes(List.of("GOV_ORG"));
        request.setGlobalRule(globalRule);
        MenuPreviewRequest previewContext = new MenuPreviewRequest();
        previewContext.setPermissionCodes(List.of("MENU:CHAIN"));
        previewContext.setOrgTypeCodes(List.of("GOV_ORG"));
        request.setPreviewContext(previewContext);

        MenuImpactPreviewView view = service.previewMenuImpact(request, "system", true);

        assertEquals("MENU_CHAIN", view.getMenuCode());
        assertEquals(false, view.isBaselineMatched());
        assertEquals(true, view.isTargetMatched());
        assertEquals(1, view.getBaselineMenuCount());
        assertEquals(2, view.getTargetMenuCount());
        assertEquals(1, view.getAddedMenuCount());
        assertEquals("MENU_CHAIN", view.getAddedMenus().getFirst().getMenuCode());
    }

    @Test
    void previewTenantRuleImpactShouldReportRemovedMenuDiff() {
        RbacMenuRepository repository = Mockito.mock(RbacMenuRepository.class);
        MenuService menuService = new MenuService(repository);
        OrgGovernanceCatalogService orgGovernanceCatalogService = Mockito.mock(OrgGovernanceCatalogService.class);
        MenuAdminService service = new MenuAdminService(repository, menuService, orgGovernanceCatalogService);

        RbacMenu parent = buildMenu(1L, "MENU_ADMIN", null);
        parent.setPermCode(null);
        RbacMenu child = buildMenu(2L, "MENU_CHAIN", 1L);

        when(repository.findByTenantIdIsNull()).thenReturn(List.of(parent, child));
        when(repository.findByTenantId("tenant-a")).thenReturn(List.of());
        when(repository.findByTenantIdIsNullAndMenuCode("MENU_CHAIN")).thenReturn(Optional.of(child));
        when(repository.findByTenantIdAndMenuCode("tenant-a", "MENU_CHAIN")).thenReturn(Optional.empty());

        MenuImpactPreviewRequest request = new MenuImpactPreviewRequest();
        request.setTenantId("tenant-a");
        request.setMenuCode("MENU_CHAIN");
        TenantMenuRuleUpdateRequest tenantRule = new TenantMenuRuleUpdateRequest();
        tenantRule.setVisible(0);
        request.setTenantRule(tenantRule);
        MenuPreviewRequest previewContext = new MenuPreviewRequest();
        previewContext.setPermissionCodes(List.of("MENU:CHAIN"));
        request.setPreviewContext(previewContext);

        MenuImpactPreviewView view = service.previewMenuImpact(request, "system", true);

        assertEquals(true, view.isBaselineMatched());
        assertEquals(false, view.isTargetMatched());
        assertEquals(2, view.getBaselineMenuCount());
        assertEquals(1, view.getTargetMenuCount());
        assertEquals(1, view.getRemovedMenuCount());
        assertEquals("MENU_CHAIN", view.getRemovedMenus().getFirst().getMenuCode());
    }

    private RbacMenu buildMenu(Long id, String menuCode, Long parentId) {
        RbacMenu menu = new RbacMenu();
        menu.setId(id);
        menu.setMenuCode(menuCode);
        menu.setMenuName(menuCode);
        menu.setParentId(parentId);
        menu.setVisible(1);
        menu.setStatus(1);
        menu.setSort(id.intValue());
        return menu;
    }
}
