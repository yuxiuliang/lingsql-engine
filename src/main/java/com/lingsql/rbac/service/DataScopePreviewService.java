package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.model.DataScopePreviewDiffView;
import com.lingsql.rbac.model.DataScopePreviewOrgView;
import com.lingsql.rbac.model.DataScopePreviewRoleCandidateView;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.RoleDataScopePreviewRequest;
import com.lingsql.rbac.model.UserDataScopePreviewRequest;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 数据权限命中预览服务。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Service
@RequiredArgsConstructor
public class DataScopePreviewService {

    private static final int MATCHED_ORG_PREVIEW_LIMIT = 50;
    private static final Integer ACTIVE_STATUS = 1;

    private final OrgUnitRepository orgUnitRepository;
    private final OrgUnitRelationRepository orgUnitRelationRepository;
    private final RbacRoleRepository roleRepository;
    private final RbacRoleDataScopeRepository roleDataScopeRepository;
    private final RbacUserRoleRepository userRoleRepository;

    /**
     * 预览角色数据权限。
     *
     * @param request       预览请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 预览结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    public DataScopePreviewView previewRoleScope(RoleDataScopePreviewRequest request, String sessionTenant, boolean sysAdmin) {
        String tenantId = resolveTenantId(request == null ? null : request.getTenantId(), sessionTenant, sysAdmin);
        DataScopeRule rule = normalizePreviewRule(request == null ? null : request.getDataScope(), false);
        DataScopeRule baselineRule = normalizePreviewRule(request == null ? null : request.getBaselineDataScope(), false);
        ScopeContext context = resolveScopeContext(tenantId, request == null ? List.of() : request.getOrgUnitIds());
        List<DataScopePreviewOrgView> targetMatched = resolveMatchedOrgItems(rule, context);
        List<DataScopePreviewOrgView> baselineMatched = resolveMatchedOrgItems(baselineRule, context);
        DataScopePreviewView view = buildPreview(rule, "ROLE_CONFIG", context);
        DataScopePreviewView baselineView = buildPreview(baselineRule, "ROLE_BASELINE", context);
        view.setDiff(buildDiff(baselineView, view, baselineMatched, targetMatched));
        applyRoleImpactStats(view, request == null ? null : request.getRoleId());
        if (CollectionUtils.isEmpty(context.directOrgCodes())
                && (RbacDataScopeType.ORG.equals(rule.getScopeType())
                || RbacDataScopeType.ORG_SUBTREE.equals(rule.getScopeType())
                || RbacDataScopeType.RELATION.equals(rule.getScopeType())
                || RbacDataScopeType.RELATION_SUBTREE.equals(rule.getScopeType()))) {
            view.getWarnings().add("未提供样本组织，当前结果仅能展示规则语义，无法展开命中组织。");
        }
        return view;
    }

    /**
     * 预览用户数据权限。
     *
     * @param request       预览请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 预览结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    public DataScopePreviewView previewUserScope(UserDataScopePreviewRequest request, String sessionTenant, boolean sysAdmin) {
        String tenantId = resolveTenantId(request == null ? null : request.getTenantId(), sessionTenant, sysAdmin);
        ScopeContext context = resolveScopeContext(tenantId, request == null ? List.of() : request.getOrgUnitIds());
        ScopeContext baselineContext = resolveScopeContext(tenantId, request == null ? List.of() : resolveBaselineOrgUnitIds(request));
        List<DataScopePreviewRoleCandidateView> roleCandidates = resolveRoleCandidates(request == null ? List.of() : request.getRoleIds());
        List<DataScopePreviewRoleCandidateView> baselineRoleCandidates = resolveRoleCandidates(request == null ? List.of() : resolveBaselineRoleIds(request));
        EffectiveRule effectiveRule = resolveEffectiveRule(request == null ? null : request.getDataScope(), roleCandidates);
        EffectiveRule baselineEffectiveRule = resolveEffectiveRule(request == null ? null : request.getBaselineDataScope(), baselineRoleCandidates);
        List<DataScopePreviewOrgView> targetMatched = resolveMatchedOrgItems(effectiveRule.rule(), context);
        List<DataScopePreviewOrgView> baselineMatched = resolveMatchedOrgItems(baselineEffectiveRule.rule(), baselineContext);
        DataScopePreviewView view = buildPreview(effectiveRule.rule(), effectiveRule.source(), context);
        DataScopePreviewView baselineView = buildPreview(baselineEffectiveRule.rule(), baselineEffectiveRule.source(), baselineContext);
        view.setRoleCandidates(roleCandidates);
        view.setDiff(buildDiff(baselineView, view, baselineMatched, targetMatched));
        applyUserImpactStats(view, request == null ? null : request.getUserId(), request == null ? List.of() : request.getRoleIds());
        if (CollectionUtils.isEmpty(context.directOrgCodes())
                && (RbacDataScopeType.ORG.equals(effectiveRule.rule().getScopeType())
                || RbacDataScopeType.ORG_SUBTREE.equals(effectiveRule.rule().getScopeType())
                || RbacDataScopeType.RELATION.equals(effectiveRule.rule().getScopeType())
                || RbacDataScopeType.RELATION_SUBTREE.equals(effectiveRule.rule().getScopeType()))) {
            view.getWarnings().add("当前用户未绑定样本组织，组织型数据权限无法展开命中组织。");
        }
        return view;
    }

    private DataScopePreviewView buildPreview(DataScopeRule rule, String source, ScopeContext context) {
        DataScopePreviewView view = new DataScopePreviewView();
        view.setEffectiveDataScope(rule);
        view.setEffectiveSource(source);
        view.setDirectOrgCodes(context.directOrgCodes());
        view.setDirectOrgSubtreeCodes(context.directOrgSubtreeCodes());
        view.setRelationDirectScopes(context.relationDirectScopes());
        view.setRelationSubtreeScopes(context.relationSubtreeScopes());

        MatchedOrgResult matched = limitMatched(resolveMatchedOrgItems(rule, context));
        view.setMatchedOrgCount(matched.totalCount());
        view.setMatchedOrgTruncated(matched.truncated());
        view.setMatchedOrgs(matched.items());
        view.setSummary(resolveSummary(rule, source, matched.totalCount()));
        view.setDescription(resolveDescription(rule, matched.totalCount()));
        return view;
    }

    private List<DataScopePreviewOrgView> resolveMatchedOrgItems(DataScopeRule rule, ScopeContext context) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return List.of();
        }
        String scopeType = rule.getScopeType().trim().toUpperCase(Locale.ROOT);
        if (RbacDataScopeType.ORG.equals(scopeType)) {
            return buildPreviewOrgs(context.directOrgs(), "DIRECT");
        }
        if (RbacDataScopeType.ORG_SUBTREE.equals(scopeType)) {
            return buildPreviewOrgs(context.directSubtreeOrgs(), "DIRECT_SUBTREE");
        }
        if (RbacDataScopeType.RELATION.equals(scopeType)) {
            return resolveRelationPreviewOrgs(context.relationDirectOrgs(), rule.getRelationTypes());
        }
        if (RbacDataScopeType.RELATION_SUBTREE.equals(scopeType)) {
            return resolveRelationPreviewOrgs(context.relationSubtreeOrgs(), rule.getRelationTypes());
        }
        if (RbacDataScopeType.TENANT.equals(scopeType) || RbacDataScopeType.ALL.equals(scopeType)) {
            return buildPreviewOrgs(context.tenantOrgs(), RbacDataScopeType.ALL.equals(scopeType) ? "ALL_TENANT" : "TENANT");
        }
        return List.of();
    }

    private List<DataScopePreviewOrgView> resolveRelationPreviewOrgs(
            Map<String, List<DataScopePreviewOrgView>> relationOrgMapping,
            List<String> relationTypes
    ) {
        if (relationOrgMapping == null || relationOrgMapping.isEmpty() || CollectionUtils.isEmpty(relationTypes)) {
            return List.of();
        }
        LinkedHashMap<Long, DataScopePreviewOrgView> merged = new LinkedHashMap<>();
        for (String relationType : relationTypes) {
            if (!StringUtils.hasText(relationType)) {
                continue;
            }
            List<DataScopePreviewOrgView> matched = relationOrgMapping.get(relationType.trim().toUpperCase(Locale.ROOT));
            if (CollectionUtils.isEmpty(matched)) {
                continue;
            }
            for (DataScopePreviewOrgView item : matched) {
                if (item.getOrgUnitId() != null) {
                    merged.putIfAbsent(item.getOrgUnitId(), item);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private MatchedOrgResult limitMatched(List<DataScopePreviewOrgView> items) {
        if (CollectionUtils.isEmpty(items)) {
            return MatchedOrgResult.empty();
        }
        int total = items.size();
        if (total <= MATCHED_ORG_PREVIEW_LIMIT) {
            return new MatchedOrgResult(items, total, false);
        }
        return new MatchedOrgResult(new ArrayList<>(items.subList(0, MATCHED_ORG_PREVIEW_LIMIT)), total, true);
    }

    private DataScopePreviewDiffView buildDiff(
            DataScopePreviewView baseline,
            DataScopePreviewView target,
            List<DataScopePreviewOrgView> baselineMatched,
            List<DataScopePreviewOrgView> targetMatched
    ) {
        DataScopePreviewDiffView diff = new DataScopePreviewDiffView();
        diff.setBaselineSummary(baseline == null ? null : baseline.getSummary());
        diff.setTargetSummary(target == null ? null : target.getSummary());
        diff.setBaselineMatchedOrgCount(baselineMatched == null ? 0 : baselineMatched.size());
        diff.setTargetMatchedOrgCount(targetMatched == null ? 0 : targetMatched.size());

        LinkedHashMap<String, DataScopePreviewOrgView> baselineMap = toPreviewOrgMap(baselineMatched);
        LinkedHashMap<String, DataScopePreviewOrgView> targetMap = toPreviewOrgMap(targetMatched);
        List<DataScopePreviewOrgView> added = new ArrayList<>();
        List<DataScopePreviewOrgView> removed = new ArrayList<>();
        int unchanged = 0;
        for (Map.Entry<String, DataScopePreviewOrgView> entry : targetMap.entrySet()) {
            if (baselineMap.containsKey(entry.getKey())) {
                unchanged++;
            } else {
                added.add(entry.getValue());
            }
        }
        for (Map.Entry<String, DataScopePreviewOrgView> entry : baselineMap.entrySet()) {
            if (!targetMap.containsKey(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }
        diff.setAddedOrgCount(added.size());
        diff.setRemovedOrgCount(removed.size());
        diff.setUnchangedOrgCount(unchanged);
        diff.setAddedOrgs(limitMatched(added).items());
        diff.setRemovedOrgs(limitMatched(removed).items());
        return diff;
    }

    private LinkedHashMap<String, DataScopePreviewOrgView> toPreviewOrgMap(List<DataScopePreviewOrgView> items) {
        LinkedHashMap<String, DataScopePreviewOrgView> mapping = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(items)) {
            return mapping;
        }
        for (DataScopePreviewOrgView item : items) {
            if (item == null) {
                continue;
            }
            String key = StringUtils.hasText(item.getUnitCode())
                    ? item.getUnitCode().trim().toUpperCase(Locale.ROOT)
                    : String.valueOf(item.getOrgUnitId());
            mapping.putIfAbsent(key, item);
        }
        return mapping;
    }

    private void applyRoleImpactStats(DataScopePreviewView view, Long roleId) {
        if (view == null) {
            return;
        }
        if (roleId == null) {
            view.setAffectedRoleCount(0);
            view.setAffectedUserCount(0);
            view.setImpactSummary("当前为未落库的新角色预览，暂不涉及存量用户。");
            return;
        }
        view.setAffectedRoleCount(1);
        int affectedUsers = new LinkedHashSet<>(userRoleRepository.findUserIdsByRoleId(roleId)).size();
        view.setAffectedUserCount(affectedUsers);
        view.setImpactSummary("当前角色变更会直接影响 " + affectedUsers + " 个已绑定用户的数据可见范围。");
    }

    private void applyUserImpactStats(DataScopePreviewView view, Long userId, List<Long> roleIds) {
        if (view == null) {
            return;
        }
        int affectedRoles = CollectionUtils.isEmpty(roleIds) ? 0 : new LinkedHashSet<>(roleIds).size();
        view.setAffectedRoleCount(affectedRoles);
        view.setAffectedUserCount(userId == null ? 0 : 1);
        if (userId == null) {
            view.setImpactSummary("当前为未落库的新用户预览，主要用于验证角色继承与组织命中范围。");
            return;
        }
        view.setImpactSummary("当前用户变更会影响 1 个用户自身的数据可见范围，并涉及 " + affectedRoles + " 个绑定角色的继承关系。");
    }

    private String resolveSummary(DataScopeRule rule, String source, int matchedOrgCount) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return "未识别到有效的数据权限规则";
        }
        String scopeType = rule.getScopeType().trim().toUpperCase(Locale.ROOT);
        String prefix = switch (source) {
            case "USER_OVERRIDE" -> "用户覆盖";
            case "ROLE_INHERIT" -> "角色继承";
            case "DEFAULT_TENANT" -> "默认租户";
            default -> "当前配置";
        };
        return switch (scopeType) {
            case RbacDataScopeType.ALL -> prefix + "：全部数据，不限制组织范围";
            case RbacDataScopeType.TENANT -> prefix + "：本租户数据，不额外限制组织范围";
            case RbacDataScopeType.SELF -> prefix + "：仅本人数据";
            case RbacDataScopeType.ORG -> prefix + "：直属组织范围，命中 " + matchedOrgCount + " 个组织";
            case RbacDataScopeType.ORG_SUBTREE -> prefix + "：直属组织及下级范围，命中 " + matchedOrgCount + " 个组织";
            case RbacDataScopeType.RELATION -> prefix + "：关系目标组织范围，命中 " + matchedOrgCount + " 个组织";
            case RbacDataScopeType.RELATION_SUBTREE -> prefix + "：关系目标组织及下级范围，命中 " + matchedOrgCount + " 个组织";
            case RbacDataScopeType.CUSTOM -> prefix + "：自定义 SQL 条件";
            default -> prefix + "：" + scopeType;
        };
    }

    private String resolveDescription(DataScopeRule rule, int matchedOrgCount) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return "当前缺少有效的数据权限规则。";
        }
        String scopeType = rule.getScopeType().trim().toUpperCase(Locale.ROOT);
        if (RbacDataScopeType.SELF.equals(scopeType)) {
            return "按当前用户主键字段过滤数据。";
        }
        if (RbacDataScopeType.CUSTOM.equals(scopeType)) {
            return "当前规则依赖自定义 SQL 条件，系统无法静态枚举命中组织。";
        }
        if (RbacDataScopeType.RELATION.equals(scopeType) || RbacDataScopeType.RELATION_SUBTREE.equals(scopeType)) {
            return "关系类型：" + String.join(" / ", rule.getRelationTypes() == null ? List.of() : rule.getRelationTypes())
                    + "；命中组织数：" + matchedOrgCount;
        }
        return "命中组织数：" + matchedOrgCount + "；字段：" + (StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn() : "默认字段");
    }

    private EffectiveRule resolveEffectiveRule(DataScopeRule requestRule, List<DataScopePreviewRoleCandidateView> candidates) {
        if (requestRule != null
                && StringUtils.hasText(requestRule.getScopeType())
                && !"INHERIT".equalsIgnoreCase(requestRule.getScopeType())) {
            return new EffectiveRule(normalizePreviewRule(requestRule, false), "USER_OVERRIDE");
        }
        if (!CollectionUtils.isEmpty(candidates)) {
            DataScopePreviewRoleCandidateView selected = candidates.stream().filter(DataScopePreviewRoleCandidateView::isSelected).findFirst().orElse(null);
            if (selected != null && selected.getDataScope() != null) {
                return new EffectiveRule(selected.getDataScope(), "ROLE_INHERIT");
            }
        }
        return new EffectiveRule(createTenantDataScope(), "DEFAULT_TENANT");
    }

    private List<Long> resolveBaselineOrgUnitIds(UserDataScopePreviewRequest request) {
        if (request == null) {
            return List.of();
        }
        if (!CollectionUtils.isEmpty(request.getBaselineOrgUnitIds())) {
            return request.getBaselineOrgUnitIds();
        }
        return request.getOrgUnitIds() == null ? List.of() : request.getOrgUnitIds();
    }

    private List<Long> resolveBaselineRoleIds(UserDataScopePreviewRequest request) {
        if (request == null) {
            return List.of();
        }
        if (!CollectionUtils.isEmpty(request.getBaselineRoleIds())) {
            return request.getBaselineRoleIds();
        }
        return request.getRoleIds() == null ? List.of() : request.getRoleIds();
    }

    private List<DataScopePreviewRoleCandidateView> resolveRoleCandidates(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        List<RbacRole> roles = roleRepository.findByIdIn(roleIds);
        if (CollectionUtils.isEmpty(roles)) {
            return List.of();
        }
        Map<Long, DataScopeRule> roleScopeMap = new LinkedHashMap<>();
        List<Long> validRoleIds = roles.stream().map(RbacRole::getId).filter(Objects::nonNull).toList();
        for (RbacRoleDataScope scope : roleDataScopeRepository.findActiveByRoleIds(validRoleIds)) {
            if (scope.getRoleId() != null && !roleScopeMap.containsKey(scope.getRoleId())) {
                roleScopeMap.put(scope.getRoleId(), toDataScopeRule(scope));
            }
        }
        List<DataScopePreviewRoleCandidateView> candidates = new ArrayList<>();
        for (RbacRole role : roles) {
            DataScopeRule rule = roleScopeMap.getOrDefault(role.getId(), createTenantDataScope());
            DataScopePreviewRoleCandidateView candidate = new DataScopePreviewRoleCandidateView();
            candidate.setRoleId(role.getId());
            candidate.setRoleCode(role.getRoleCode());
            candidate.setRoleName(role.getRoleName());
            candidate.setDataScope(rule);
            candidate.setRelationTypes(rule.getRelationTypes() == null ? List.of() : rule.getRelationTypes());
            candidate.setSummary(buildRoleScopeSummary(rule));
            candidates.add(candidate);
        }
        DataScopePreviewRoleCandidateView selected = candidates.stream()
                .min(Comparator.comparingInt(item -> scopePriority(item.getDataScope() == null ? null : item.getDataScope().getScopeType())))
                .orElse(null);
        if (selected != null) {
            selected.setSelected(true);
        }
        return candidates;
    }

    private ScopeContext resolveScopeContext(String tenantId, List<Long> orgUnitIds) {
        String resolvedTenantId = trimToNull(tenantId);
        if (!StringUtils.hasText(resolvedTenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        List<OrgUnit> tenantUnits = orgUnitRepository.findByTenantId(resolvedTenantId);
        Map<Long, OrgUnit> tenantUnitMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null)
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        List<OrgUnit> directOrgs = new ArrayList<>();
        if (!CollectionUtils.isEmpty(orgUnitIds)) {
            for (Long orgUnitId : new LinkedHashSet<>(orgUnitIds)) {
                OrgUnit orgUnit = tenantUnitMapping.get(orgUnitId);
                if (orgUnit == null) {
                    throw new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "组织节点不存在");
                }
                directOrgs.add(orgUnit);
            }
        }
        List<OrgUnit> directSubtreeOrgs = resolveSubtreeOrgs(tenantUnits, directOrgs.stream().map(OrgUnit::getId).filter(Objects::nonNull).toList());
        RelationScopeContext relationContext = resolveRelationScopeContext(resolvedTenantId, tenantUnits, directOrgs.stream().map(OrgUnit::getId).filter(Objects::nonNull).toList());
        return new ScopeContext(
                toCodes(directOrgs),
                toCodes(directSubtreeOrgs),
                directOrgs,
                directSubtreeOrgs,
                relationContext.directCodes(),
                relationContext.subtreeCodes(),
                relationContext.directOrgs(),
                relationContext.subtreeOrgs(),
                tenantUnits
        );
    }

    private RelationScopeContext resolveRelationScopeContext(String tenantId, List<OrgUnit> tenantUnits, List<Long> sourceOrgIds) {
        if (!StringUtils.hasText(tenantId) || CollectionUtils.isEmpty(sourceOrgIds)) {
            return RelationScopeContext.empty();
        }
        List<OrgUnitRelation> relations = orgUnitRelationRepository.findByTenantIdAndStatusAndSourceOrgUnitIdIn(tenantId, ACTIVE_STATUS, sourceOrgIds);
        if (CollectionUtils.isEmpty(relations)) {
            return RelationScopeContext.empty();
        }
        Map<Long, OrgUnit> tenantUnitMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null)
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        Map<String, LinkedHashSet<String>> directCodes = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> subtreeCodes = new LinkedHashMap<>();
        Map<String, List<DataScopePreviewOrgView>> directOrgs = new LinkedHashMap<>();
        Map<String, List<DataScopePreviewOrgView>> subtreeOrgs = new LinkedHashMap<>();
        for (OrgUnitRelation relation : relations) {
            if (relation == null || relation.getTargetOrgUnitId() == null || !StringUtils.hasText(relation.getRelationType())) {
                continue;
            }
            String relationType = relation.getRelationType().trim().toUpperCase(Locale.ROOT);
            OrgUnit target = tenantUnitMapping.get(relation.getTargetOrgUnitId());
            if (target == null || !StringUtils.hasText(target.getUnitCode())) {
                continue;
            }
            directCodes.computeIfAbsent(relationType, key -> new LinkedHashSet<>()).add(target.getUnitCode().trim().toUpperCase(Locale.ROOT));
            directOrgs.computeIfAbsent(relationType, key -> new ArrayList<>())
                    .add(toPreviewOrg(target, relationType));

            List<OrgUnit> subtree = resolveSubtreeOrgs(tenantUnits, List.of(target.getId()));
            LinkedHashSet<String> subtreeCodeSet = subtreeCodes.computeIfAbsent(relationType, key -> new LinkedHashSet<>());
            List<DataScopePreviewOrgView> subtreeOrgItems = subtreeOrgs.computeIfAbsent(relationType, key -> new ArrayList<>());
            for (OrgUnit item : subtree) {
                if (StringUtils.hasText(item.getUnitCode())) {
                    subtreeCodeSet.add(item.getUnitCode().trim().toUpperCase(Locale.ROOT));
                    subtreeOrgItems.add(toPreviewOrg(item, relationType + "_SUBTREE"));
                }
            }
        }
        return new RelationScopeContext(toMap(directCodes), toMap(subtreeCodes), dedupePreviewMap(directOrgs), dedupePreviewMap(subtreeOrgs));
    }

    private Map<String, List<String>> toMap(Map<String, LinkedHashSet<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : source.entrySet()) {
            if (!CollectionUtils.isEmpty(entry.getValue())) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return result;
    }

    private Map<String, List<DataScopePreviewOrgView>> dedupePreviewMap(Map<String, List<DataScopePreviewOrgView>> source) {
        Map<String, List<DataScopePreviewOrgView>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<DataScopePreviewOrgView>> entry : source.entrySet()) {
            LinkedHashMap<Long, DataScopePreviewOrgView> deduped = new LinkedHashMap<>();
            for (DataScopePreviewOrgView item : entry.getValue()) {
                if (item.getOrgUnitId() != null) {
                    deduped.putIfAbsent(item.getOrgUnitId(), item);
                }
            }
            result.put(entry.getKey(), new ArrayList<>(deduped.values()));
        }
        return result;
    }

    private List<OrgUnit> resolveSubtreeOrgs(List<OrgUnit> tenantUnits, List<Long> rootIds) {
        if (CollectionUtils.isEmpty(tenantUnits) || CollectionUtils.isEmpty(rootIds)) {
            return List.of();
        }
        Map<Long, OrgUnit> rootMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null && rootIds.contains(unit.getId()))
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        if (rootMapping.isEmpty()) {
            return List.of();
        }
        List<OrgUnit> matched = new ArrayList<>();
        for (OrgUnit unit : tenantUnits) {
            if (unit.getId() == null) {
                continue;
            }
            for (OrgUnit root : rootMapping.values()) {
                if (Objects.equals(unit.getId(), root.getId())) {
                    matched.add(unit);
                    break;
                }
                if (StringUtils.hasText(root.getTreePath())
                        && StringUtils.hasText(unit.getTreePath())
                        && unit.getTreePath().startsWith(root.getTreePath() + "/")) {
                    matched.add(unit);
                    break;
                }
            }
        }
        matched.sort(Comparator.comparing(OrgUnit::getTreePath, Comparator.nullsLast(String::compareTo))
                .thenComparing(OrgUnit::getUnitCode, Comparator.nullsLast(String::compareTo)));
        return matched;
    }

    private List<String> toCodes(List<OrgUnit> orgUnits) {
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

    private List<DataScopePreviewOrgView> buildPreviewOrgs(List<OrgUnit> orgUnits, String hitSource) {
        if (CollectionUtils.isEmpty(orgUnits)) {
            return List.of();
        }
        List<DataScopePreviewOrgView> views = new ArrayList<>();
        for (OrgUnit orgUnit : orgUnits) {
            views.add(toPreviewOrg(orgUnit, hitSource));
        }
        return views;
    }

    private DataScopePreviewOrgView toPreviewOrg(OrgUnit orgUnit, String hitSource) {
        DataScopePreviewOrgView view = new DataScopePreviewOrgView();
        view.setOrgUnitId(orgUnit.getId());
        view.setUnitCode(orgUnit.getUnitCode());
        view.setUnitName(orgUnit.getUnitName());
        view.setTypeCode(orgUnit.getTypeCode());
        view.setHitSource(hitSource);
        return view;
    }

    private DataScopeRule normalizePreviewRule(DataScopeRule rule, boolean allowInherit) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return allowInherit ? createInheritDataScope() : createTenantDataScope();
        }
        String scopeType = rule.getScopeType().trim().toUpperCase(Locale.ROOT);
        if (allowInherit && "INHERIT".equals(scopeType)) {
            return createInheritDataScope();
        }
        if (!List.of(
                RbacDataScopeType.ALL,
                RbacDataScopeType.TENANT,
                RbacDataScopeType.SELF,
                RbacDataScopeType.ORG,
                RbacDataScopeType.ORG_SUBTREE,
                RbacDataScopeType.RELATION,
                RbacDataScopeType.RELATION_SUBTREE,
                RbacDataScopeType.CUSTOM
        ).contains(scopeType)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数据权限范围不支持");
        }
        DataScopeRule normalized = new DataScopeRule();
        normalized.setScopeType(scopeType);
        normalized.setScopeColumn(StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn().trim() : "");
        normalized.setScopeRule(StringUtils.hasText(rule.getScopeRule()) ? rule.getScopeRule().trim() : "");
        normalized.setRelationTypes(normalizeRelationTypes(rule.getRelationTypes()));
        return normalized;
    }

    private DataScopeRule toDataScopeRule(RbacRoleDataScope entity) {
        DataScopeRule rule = new DataScopeRule();
        if (entity == null || !StringUtils.hasText(entity.getScopeType())) {
            return createTenantDataScope();
        }
        rule.setScopeType(entity.getScopeType().trim().toUpperCase(Locale.ROOT));
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

    private int scopePriority(String type) {
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

    private String buildRoleScopeSummary(DataScopeRule rule) {
        if (rule == null || !StringUtils.hasText(rule.getScopeType())) {
            return "本租户";
        }
        String scopeType = rule.getScopeType().trim().toUpperCase(Locale.ROOT);
        return switch (scopeType) {
            case RbacDataScopeType.ALL -> "全部数据";
            case RbacDataScopeType.TENANT -> "本租户";
            case RbacDataScopeType.SELF -> "仅本人";
            case RbacDataScopeType.ORG -> "直属组织";
            case RbacDataScopeType.ORG_SUBTREE -> "直属组织及下级";
            case RbacDataScopeType.RELATION -> "关系目标组织";
            case RbacDataScopeType.RELATION_SUBTREE -> "关系目标组织及下级";
            case RbacDataScopeType.CUSTOM -> "自定义条件";
            default -> scopeType;
        };
    }

    private boolean isRelationScopeType(String scopeType) {
        return RbacDataScopeType.RELATION.equalsIgnoreCase(scopeType)
                || RbacDataScopeType.RELATION_SUBTREE.equalsIgnoreCase(scopeType);
    }

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

    private List<String> parseRelationTypes(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String normalized = rawValue.replace('，', ',').replace('；', ',');
        for (String item : normalized.split(",")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(values);
    }

    private DataScopeRule createTenantDataScope() {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.TENANT);
        rule.setScopeColumn("");
        rule.setScopeRule("");
        rule.setRelationTypes(List.of());
        return rule;
    }

    private DataScopeRule createInheritDataScope() {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType("INHERIT");
        rule.setScopeColumn("");
        rule.setScopeRule("");
        rule.setRelationTypes(List.of());
        return rule;
    }

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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record EffectiveRule(DataScopeRule rule, String source) {
    }

    private record ScopeContext(
            List<String> directOrgCodes,
            List<String> directOrgSubtreeCodes,
            List<OrgUnit> directOrgs,
            List<OrgUnit> directSubtreeOrgs,
            Map<String, List<String>> relationDirectScopes,
            Map<String, List<String>> relationSubtreeScopes,
            Map<String, List<DataScopePreviewOrgView>> relationDirectOrgs,
            Map<String, List<DataScopePreviewOrgView>> relationSubtreeOrgs,
            List<OrgUnit> tenantOrgs
    ) {
    }

    private record RelationScopeContext(
            Map<String, List<String>> directCodes,
            Map<String, List<String>> subtreeCodes,
            Map<String, List<DataScopePreviewOrgView>> directOrgs,
            Map<String, List<DataScopePreviewOrgView>> subtreeOrgs
    ) {
        private static RelationScopeContext empty() {
            return new RelationScopeContext(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private record MatchedOrgResult(List<DataScopePreviewOrgView> items, int totalCount, boolean truncated) {
        private static MatchedOrgResult empty() {
            return new MatchedOrgResult(List.of(), 0, false);
        }
    }
}
