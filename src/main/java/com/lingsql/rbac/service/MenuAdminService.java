package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.service.OrgGovernanceCatalogService;
import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.model.MenuAdminView;
import com.lingsql.rbac.model.MenuImpactDiffItemView;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuPreviewRequest;
import com.lingsql.rbac.model.MenuRuleSnapshotView;
import com.lingsql.rbac.model.MenuTenantOverrideSnapshotView;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final OrgGovernanceCatalogService orgGovernanceCatalogService;

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
        orgGovernanceCatalogService.validateTenantSelections(
                tenantId,
                request == null ? null : request.getOrgTypeCodes(),
                request == null ? null : request.getOrgUnitCodes(),
                request == null ? null : request.getOrgTags(),
                request == null ? null : request.getRequiredRelationTypes()
        );
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
        orgGovernanceCatalogService.validateTenantSelections(
                tenantId,
                request == null ? null : request.getOrgTypeCodes(),
                request == null ? null : request.getOrgUnitCodes(),
                request == null ? null : request.getOrgTags(),
                request == null ? null : request.getRelationTypes()
        );
        return menuService.listMenus(buildPreviewSession(request, tenantId));
    }

    /**
     * 预览菜单规则变更影响。
     *
     * @param request       影响分析请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 影响分析结果
     * @author 余炘洋
     * @date 2026-04-13
     */
    public MenuImpactPreviewView previewMenuImpact(MenuImpactPreviewRequest request, String sessionTenant, boolean sysAdmin) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "影响分析请求不能为空");
        }
        String tenantId = resolveTenantId(request.getTenantId(), sessionTenant, sysAdmin);
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        MenuPreviewRequest previewContext = request.getPreviewContext() == null ? new MenuPreviewRequest() : request.getPreviewContext();
        orgGovernanceCatalogService.validateTenantSelections(
                tenantId,
                previewContext.getOrgTypeCodes(),
                previewContext.getOrgUnitCodes(),
                previewContext.getOrgTags(),
                previewContext.getRelationTypes()
        );
        RbacUserSession previewSession = buildPreviewSession(previewContext, tenantId);
        List<MenuView> baselineMenus = menuService.listMenus(previewSession);

        if (request.getMenuId() != null) {
            return previewGlobalRuleImpact(request, tenantId, previewSession, baselineMenus);
        }
        if (StringUtils.hasText(request.getMenuCode())) {
            return previewTenantRuleImpact(request, tenantId, previewSession, baselineMenus);
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "影响分析目标不能为空");
    }

    private MenuImpactPreviewView previewGlobalRuleImpact(MenuImpactPreviewRequest request,
                                                          String tenantId,
                                                          RbacUserSession previewSession,
                                                          List<MenuView> baselineMenus) {
        RbacMenu globalMenu = menuRepository.findById(request.getMenuId())
                .orElseThrow(() -> new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在"));
        if (StringUtils.hasText(globalMenu.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前仅支持分析全局菜单规则");
        }
        RbacMenu previewGlobalMenu = copyMenuEntity(globalMenu);
        applyRuleFields(previewGlobalMenu, request.getGlobalRule());
        List<RbacMenu> targetEffectiveMenus = resolveEffectiveMenusForPreview(
                tenantId,
                Map.of(normalizeValue(previewGlobalMenu.getMenuCode()), previewGlobalMenu),
                Map.of(),
                Set.of()
        );
        return buildImpactPreviewView(
                tenantId,
                false,
                globalMenu.getMenuCode(),
                globalMenu.getMenuName(),
                buildRuleSummary(globalMenu),
                createRuleSnapshot(globalMenu),
                null,
                buildRuleSummary(previewGlobalMenu),
                createRuleSnapshot(previewGlobalMenu),
                null,
                determineChangedDimensions(globalMenu, previewGlobalMenu),
                baselineMenus,
                menuService.listMenus(previewSession, targetEffectiveMenus)
        );
    }

    private MenuImpactPreviewView previewTenantRuleImpact(MenuImpactPreviewRequest request,
                                                          String tenantId,
                                                          RbacUserSession previewSession,
                                                          List<MenuView> baselineMenus) {
        String normalizedMenuCode = normalizeValue(request.getMenuCode());
        if (!StringUtils.hasText(normalizedMenuCode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜单编码不能为空");
        }
        TenantMenuRuleUpdateRequest tenantRule = request.getTenantRule() == null ? new TenantMenuRuleUpdateRequest() : request.getTenantRule();
        boolean clearTenantOverride = request.getClearTenantOverride() != null && request.getClearTenantOverride();
        if (!clearTenantOverride) {
            orgGovernanceCatalogService.validateTenantSelections(
                    tenantId,
                    tenantRule.getOrgTypeCodes(),
                    tenantRule.getOrgUnitCodes(),
                    tenantRule.getOrgTags(),
                    tenantRule.getRequiredRelationTypes()
            );
        }
        RbacMenu globalMenu = menuRepository.findByTenantIdIsNullAndMenuCode(normalizedMenuCode)
                .orElseThrow(() -> new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在"));
        RbacMenu baselineOverride = menuRepository.findByTenantIdAndMenuCode(tenantId, normalizedMenuCode).orElse(null);
        RbacMenu baselineEffectiveMenu = menuService.mergeMenu(globalMenu, baselineOverride);
        RbacMenu previewOverride = clearTenantOverride
                ? null
                : java.util.Optional.ofNullable(baselineOverride)
                .map(this::copyMenuEntity)
                .orElseGet(() -> createTenantOverrideSkeleton(globalMenu, tenantId));
        if (!clearTenantOverride) {
            applyTenantOverrideFields(previewOverride, tenantRule);
        }
        List<RbacMenu> targetEffectiveMenus = resolveEffectiveMenusForPreview(
                tenantId,
                Map.of(),
                previewOverride == null ? Map.of() : Map.of(normalizedMenuCode, previewOverride),
                clearTenantOverride ? Set.of(normalizedMenuCode) : Set.of()
        );
        RbacMenu targetEffectiveMenu = menuService.mergeMenu(globalMenu, previewOverride);
        return buildImpactPreviewView(
                tenantId,
                true,
                globalMenu.getMenuCode(),
                globalMenu.getMenuName(),
                buildRuleSummary(baselineEffectiveMenu),
                createRuleSnapshot(baselineEffectiveMenu),
                createTenantOverrideSnapshot(baselineOverride),
                buildRuleSummary(targetEffectiveMenu),
                createRuleSnapshot(targetEffectiveMenu),
                createTenantOverrideSnapshot(previewOverride),
                determineChangedDimensions(baselineEffectiveMenu, targetEffectiveMenu),
                baselineMenus,
                menuService.listMenus(previewSession, targetEffectiveMenus)
        );
    }

    private MenuImpactPreviewView buildImpactPreviewView(String tenantId,
                                                         boolean tenantOverrideMode,
                                                         String menuCode,
                                                         String menuName,
                                                         String baselineRuleSummary,
                                                         MenuRuleSnapshotView baselineRuleSnapshot,
                                                         MenuTenantOverrideSnapshotView baselineOverrideSnapshot,
                                                         String targetRuleSummary,
                                                         MenuRuleSnapshotView targetRuleSnapshot,
                                                         MenuTenantOverrideSnapshotView targetOverrideSnapshot,
                                                         List<String> changedDimensions,
                                                         List<MenuView> baselineMenus,
                                                         List<MenuView> targetMenus) {
        Map<String, MenuImpactDiffItemView> baselineMapping = flattenMenuTree(baselineMenus);
        Map<String, MenuImpactDiffItemView> targetMapping = flattenMenuTree(targetMenus);
        List<MenuImpactDiffItemView> addedMenus = targetMapping.entrySet().stream()
                .filter(entry -> !baselineMapping.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<MenuImpactDiffItemView> removedMenus = baselineMapping.entrySet().stream()
                .filter(entry -> !targetMapping.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        MenuImpactPreviewView view = new MenuImpactPreviewView();
        view.setTenantId(tenantId);
        view.setTenantOverrideMode(tenantOverrideMode);
        view.setMenuCode(menuCode);
        view.setMenuName(menuName);
        view.setBaselineMenuCount(baselineMapping.size());
        view.setTargetMenuCount(targetMapping.size());
        view.setAddedMenuCount(addedMenus.size());
        view.setRemovedMenuCount(removedMenus.size());
        view.setBaselineMatched(baselineMapping.containsKey(normalizeValue(menuCode)));
        view.setTargetMatched(targetMapping.containsKey(normalizeValue(menuCode)));
        view.setBaselineRuleSummary(baselineRuleSummary);
        view.setBaselineRuleSnapshot(baselineRuleSnapshot);
        view.setBaselineOverrideSnapshot(baselineOverrideSnapshot);
        view.setTargetRuleSummary(targetRuleSummary);
        view.setTargetRuleSnapshot(targetRuleSnapshot);
        view.setTargetOverrideSnapshot(targetOverrideSnapshot);
        view.setChangedDimensions(changedDimensions);
        view.setAddedMenus(addedMenus);
        view.setRemovedMenus(removedMenus);
        view.setImpactSummary(buildImpactSummary(view));
        return view;
    }

    private String buildImpactSummary(MenuImpactPreviewView view) {
        String targetStatus = view.isTargetMatched() ? "目标菜单可见" : "目标菜单不可见";
        return "基线菜单 " + defaultCount(view.getBaselineMenuCount())
                + " 项，目标菜单 " + defaultCount(view.getTargetMenuCount())
                + " 项，新增 " + defaultCount(view.getAddedMenuCount())
                + " 项，移除 " + defaultCount(view.getRemovedMenuCount())
                + " 项，当前样本上下文下" + targetStatus + "。";
    }

    private int defaultCount(Integer value) {
        return value == null ? 0 : value;
    }

    private Map<String, MenuImpactDiffItemView> flattenMenuTree(List<MenuView> menus) {
        Map<String, MenuImpactDiffItemView> mapping = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(menus)) {
            return mapping;
        }
        for (MenuView menu : menus) {
            collectMenuTree(mapping, menu);
        }
        return mapping;
    }

    private void collectMenuTree(Map<String, MenuImpactDiffItemView> mapping, MenuView menu) {
        if (menu == null || !StringUtils.hasText(menu.getMenuCode())) {
            return;
        }
        String normalizedCode = normalizeValue(menu.getMenuCode());
        mapping.putIfAbsent(normalizedCode, toDiffItem(menu));
        if (CollectionUtils.isEmpty(menu.getChildren())) {
            return;
        }
        for (MenuView child : menu.getChildren()) {
            collectMenuTree(mapping, child);
        }
    }

    private MenuImpactDiffItemView toDiffItem(MenuView menu) {
        MenuImpactDiffItemView item = new MenuImpactDiffItemView();
        item.setMenuCode(menu.getMenuCode());
        item.setMenuName(menu.getMenuName());
        item.setPath(menu.getPath());
        return item;
    }

    private List<RbacMenu> resolveEffectiveMenusForPreview(String tenantId,
                                                           Map<String, RbacMenu> previewGlobalMenus,
                                                           Map<String, RbacMenu> previewTenantOverrides,
                                                           Set<String> clearedTenantOverrideCodes) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        Map<String, RbacMenu> globalPreviewMapping = previewGlobalMenus == null ? Map.of() : previewGlobalMenus;
        Map<String, RbacMenu> tenantOverrideMapping = new HashMap<>();
        for (RbacMenu menu : menuRepository.findByTenantId(tenantId.trim())) {
            if (menu == null || !StringUtils.hasText(menu.getMenuCode())) {
                continue;
            }
            String normalizedMenuCode = normalizeValue(menu.getMenuCode());
            if (clearedTenantOverrideCodes != null && clearedTenantOverrideCodes.contains(normalizedMenuCode)) {
                continue;
            }
            tenantOverrideMapping.put(normalizedMenuCode, menu);
        }
        if (previewTenantOverrides != null && !previewTenantOverrides.isEmpty()) {
            tenantOverrideMapping.putAll(previewTenantOverrides);
        }
        List<RbacMenu> resolvedMenus = new ArrayList<>();
        for (RbacMenu globalMenu : menuRepository.findByTenantIdIsNull()) {
            if (globalMenu == null || !StringUtils.hasText(globalMenu.getMenuCode())) {
                continue;
            }
            RbacMenu effectiveGlobalMenu = globalPreviewMapping.getOrDefault(normalizeValue(globalMenu.getMenuCode()), globalMenu);
            RbacMenu effectiveMenu = menuService.mergeMenu(
                    effectiveGlobalMenu,
                    tenantOverrideMapping.get(normalizeValue(effectiveGlobalMenu.getMenuCode()))
            );
            if (isActiveStatus(effectiveMenu == null ? null : effectiveMenu.getStatus())) {
                resolvedMenus.add(effectiveMenu);
            }
        }
        return resolvedMenus;
    }

    private String buildRuleSummary(RbacMenu menu) {
        if (menu == null) {
            return "未配置额外规则";
        }
        List<String> segments = new ArrayList<>();
        if (menu.getVisible() != null) {
            segments.add(menu.getVisible() == 1 ? "显示" : "隐藏");
        }
        if (menu.getStatus() != null) {
            segments.add(menu.getStatus() == 1 ? "启用" : "停用");
        }
        List<String> orgTypeCodes = parseConfiguredValues(menu.getOrgTypeCodes());
        if (!orgTypeCodes.isEmpty()) {
            segments.add("组织类型 " + String.join(" / ", orgTypeCodes));
        }
        List<String> orgUnitCodes = parseConfiguredValues(menu.getOrgUnitCodes());
        if (!orgUnitCodes.isEmpty()) {
            segments.add("组织编码 " + String.join(" / ", orgUnitCodes));
        }
        List<String> orgTags = parseConfiguredValues(menu.getOrgTags());
        if (!orgTags.isEmpty()) {
            segments.add("组织标签 " + String.join(" / ", orgTags));
        }
        List<String> relationTypes = parseConfiguredValues(menu.getRequiredRelationTypes());
        if (!relationTypes.isEmpty()) {
            segments.add("关系语义 " + String.join(" / ", relationTypes));
        }
        return segments.isEmpty() ? "未配置额外规则" : String.join("；", segments);
    }

    private List<String> determineChangedDimensions(RbacMenu baselineMenu, RbacMenu targetMenu) {
        List<String> dimensions = new ArrayList<>();
        if (!java.util.Objects.equals(baselineMenu == null ? null : baselineMenu.getVisible(), targetMenu == null ? null : targetMenu.getVisible())) {
            dimensions.add("显示策略");
        }
        if (!java.util.Objects.equals(baselineMenu == null ? null : baselineMenu.getStatus(), targetMenu == null ? null : targetMenu.getStatus())) {
            dimensions.add("启用状态");
        }
        if (!parseConfiguredValues(baselineMenu == null ? null : baselineMenu.getOrgTypeCodes())
                .equals(parseConfiguredValues(targetMenu == null ? null : targetMenu.getOrgTypeCodes()))) {
            dimensions.add("组织类型");
        }
        if (!parseConfiguredValues(baselineMenu == null ? null : baselineMenu.getOrgUnitCodes())
                .equals(parseConfiguredValues(targetMenu == null ? null : targetMenu.getOrgUnitCodes()))) {
            dimensions.add("组织编码");
        }
        if (!parseConfiguredValues(baselineMenu == null ? null : baselineMenu.getOrgTags())
                .equals(parseConfiguredValues(targetMenu == null ? null : targetMenu.getOrgTags()))) {
            dimensions.add("组织标签");
        }
        if (!parseConfiguredValues(baselineMenu == null ? null : baselineMenu.getRequiredRelationTypes())
                .equals(parseConfiguredValues(targetMenu == null ? null : targetMenu.getRequiredRelationTypes()))) {
            dimensions.add("关系语义");
        }
        return dimensions;
    }

    MenuRuleSnapshotView resolveCurrentRuleSnapshot(String tenantId, Long menuId, String menuCode, boolean tenantOverrideMode) {
        if (tenantOverrideMode) {
            String normalizedMenuCode = normalizeValue(menuCode);
            if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(normalizedMenuCode)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "租户菜单规则快照参数不完整");
            }
            RbacMenu globalMenu = menuRepository.findByTenantIdIsNullAndMenuCode(normalizedMenuCode)
                    .orElseThrow(() -> new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在"));
            RbacMenu effectiveMenu = menuService.mergeMenu(
                    globalMenu,
                    menuRepository.findByTenantIdAndMenuCode(tenantId, normalizedMenuCode).orElse(null)
            );
            return createRuleSnapshot(effectiveMenu);
        }
        if (menuId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "全局菜单规则快照参数不完整");
        }
        RbacMenu globalMenu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BizException(ErrorCode.MENU_NOT_FOUND, "菜单不存在"));
        if (StringUtils.hasText(globalMenu.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前仅支持查询全局菜单规则快照");
        }
        return createRuleSnapshot(globalMenu);
    }

    MenuTenantOverrideSnapshotView resolveCurrentOverrideSnapshot(String tenantId, String menuCode) {
        String normalizedMenuCode = normalizeValue(menuCode);
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(normalizedMenuCode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户菜单覆盖快照参数不完整");
        }
        return createTenantOverrideSnapshot(
                menuRepository.findByTenantIdAndMenuCode(tenantId, normalizedMenuCode).orElse(null)
        );
    }

    private RbacUserSession buildPreviewSession(MenuPreviewRequest request, String tenantId) {
        RbacUserSession session = new RbacUserSession();
        session.setTenantId(tenantId);
        session.setPermissions(normalizeValues(request == null ? null : request.getPermissionCodes()));
        session.setMemberOrgTypeCodes(normalizeValues(request == null ? null : request.getOrgTypeCodes()));
        session.setMemberOrgUnitCodes(normalizeValues(request == null ? null : request.getOrgUnitCodes()));
        session.setMemberOrgTags(normalizeValues(request == null ? null : request.getOrgTags()));
        session.setRelationScopes(buildRelationScopes(request == null ? null : request.getRelationTypes()));
        session.setExperience(request != null && Boolean.TRUE.equals(request.getExperience()));
        session.setMenuWhitelist(normalizeValues(request == null ? null : request.getMenuWhitelist()));
        return session;
    }

    private RbacMenu copyMenuEntity(RbacMenu source) {
        if (source == null) {
            return null;
        }
        RbacMenu copied = new RbacMenu();
        copied.setId(source.getId());
        copied.setTenantId(source.getTenantId());
        copied.setMenuCode(source.getMenuCode());
        copied.setMenuName(source.getMenuName());
        copied.setParentId(source.getParentId());
        copied.setPath(source.getPath());
        copied.setComponent(source.getComponent());
        copied.setIcon(source.getIcon());
        copied.setSort(source.getSort());
        copied.setVisible(source.getVisible());
        copied.setStatus(source.getStatus());
        copied.setPermCode(source.getPermCode());
        copied.setOrgTypeCodes(source.getOrgTypeCodes());
        copied.setOrgUnitCodes(source.getOrgUnitCodes());
        copied.setOrgTags(source.getOrgTags());
        copied.setRequiredRelationTypes(source.getRequiredRelationTypes());
        copied.setCreateTime(source.getCreateTime());
        copied.setUpdateTime(source.getUpdateTime());
        return copied;
    }

    private MenuRuleSnapshotView createRuleSnapshot(RbacMenu menu) {
        MenuRuleSnapshotView snapshot = new MenuRuleSnapshotView();
        if (menu == null) {
            return snapshot;
        }
        snapshot.setVisible(menu.getVisible());
        snapshot.setStatus(menu.getStatus());
        snapshot.setOrgTypeCodes(parseConfiguredValues(menu.getOrgTypeCodes()));
        snapshot.setOrgUnitCodes(parseConfiguredValues(menu.getOrgUnitCodes()));
        snapshot.setOrgTags(parseConfiguredValues(menu.getOrgTags()));
        snapshot.setRequiredRelationTypes(parseConfiguredValues(menu.getRequiredRelationTypes()));
        return snapshot;
    }

    private MenuTenantOverrideSnapshotView createTenantOverrideSnapshot(RbacMenu tenantOverride) {
        MenuTenantOverrideSnapshotView snapshot = new MenuTenantOverrideSnapshotView();
        if (tenantOverride == null) {
            snapshot.setOverrideActive(false);
            return snapshot;
        }
        snapshot.setOverrideActive(true);
        snapshot.setVisible(tenantOverride.getVisible());
        snapshot.setStatus(tenantOverride.getStatus());
        snapshot.setOrgTypeCodesSpecified(tenantOverride.getOrgTypeCodes() != null);
        snapshot.setOrgTypeCodes(parseConfiguredValues(tenantOverride.getOrgTypeCodes()));
        snapshot.setOrgUnitCodesSpecified(tenantOverride.getOrgUnitCodes() != null);
        snapshot.setOrgUnitCodes(parseConfiguredValues(tenantOverride.getOrgUnitCodes()));
        snapshot.setOrgTagsSpecified(tenantOverride.getOrgTags() != null);
        snapshot.setOrgTags(parseConfiguredValues(tenantOverride.getOrgTags()));
        snapshot.setRequiredRelationTypesSpecified(tenantOverride.getRequiredRelationTypes() != null);
        snapshot.setRequiredRelationTypes(parseConfiguredValues(tenantOverride.getRequiredRelationTypes()));
        return snapshot;
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

    private boolean isActiveStatus(Integer status) {
        return status == null || status == 1;
    }
}
