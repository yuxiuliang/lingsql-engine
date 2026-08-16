package com.lingsql.organization.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.OrgUnitType;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.model.OrgUnitCreateRequest;
import com.lingsql.organization.model.OrgUnitRelationCreateRequest;
import com.lingsql.organization.model.OrgUnitRelationUpdateRequest;
import com.lingsql.organization.model.OrgRelationOpsTaskView;
import com.lingsql.organization.model.OrgRelationRouteCandidateView;
import com.lingsql.organization.model.OrgRelationRouteResolveView;
import com.lingsql.organization.model.OrgRelationWorkbenchView;
import com.lingsql.organization.model.OrgUnitRelationView;
import com.lingsql.organization.model.OrgTemplateApplyRequest;
import com.lingsql.organization.model.OrgTemplateView;
import com.lingsql.organization.model.OrgUnitTypeCreateRequest;
import com.lingsql.organization.model.OrgUnitTypeUpdateRequest;
import com.lingsql.organization.model.OrgUnitTypeView;
import com.lingsql.organization.model.OrgUnitUpdateRequest;
import com.lingsql.organization.model.OrgUnitView;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitTypeRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.authorization.AuthorizationChange;
import com.lingsql.rbac.authorization.AuthorizationChangeKind;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织域管理服务。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Service
@RequiredArgsConstructor
public class OrganizationAdminService {

    private static final Integer ACTIVE_STATUS = 1;
    private static final String TEMPLATE_GOVERNMENT = "GOVERNMENT_STANDARD";
    private static final String TEMPLATE_SCHOOL = "SCHOOL_STANDARD";
    private static final String TEMPLATE_ENTERPRISE = "ENTERPRISE_STANDARD";
    private static final String TEMPLATE_CHAIN = "CHAIN_STORE_STANDARD";
    private static final String TEMPLATE_SOHO = "SOLE_PROPRIETOR_STANDARD";
    private static final String PRIORITY_P0 = "P0";
    private static final String PRIORITY_P1 = "P1";
    private static final String PRIORITY_P2 = "P2";
    private static final String READINESS_READY = "READY";
    private static final String READINESS_PENDING = "PENDING";
    private static final String READINESS_BLOCKED = "BLOCKED";

    private final OrgUnitTypeRepository orgUnitTypeRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserOrgMembershipRepository userOrgMembershipRepository;
    private final OrgUnitRelationRepository orgUnitRelationRepository;
    private final RbacUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final IAuthorizationSessionLifecycle authorizationSessionLifecycle;

    /**
     * 查询组织类型列表。
     *
     * @param tenantId 租户ID
     * @return 类型列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    public List<OrgUnitTypeView> listUnitTypes(String tenantId) {
        String resolvedTenantId = requireTenantId(tenantId);
        Map<String, Long> unitCountMapping = new HashMap<>();
        for (OrgUnit unit : orgUnitRepository.findByTenantId(resolvedTenantId)) {
            unitCountMapping.merge(unit.getTypeCode(), 1L, Long::sum);
        }
        return orgUnitTypeRepository.findByTenantIdOrderBySortAscIdAsc(resolvedTenantId).stream()
                .map(type -> toTypeView(type, unitCountMapping.getOrDefault(type.getTypeCode(), 0L)))
                .toList();
    }

    /**
     * 查询组织模板列表。
     *
     * @return 模板列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    public List<OrgTemplateView> listTemplates() {
        return buildTemplates().stream()
                .map(this::toTemplateView)
                .toList();
    }

    /**
     * 创建组织类型。
     *
     * @param request       创建请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 类型视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public OrgUnitTypeView createUnitType(OrgUnitTypeCreateRequest request, String sessionTenant, boolean sysAdmin) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织类型请求不能为空");
        }
        String tenantId = resolveTenantId(request.getTenantId(), sessionTenant, sysAdmin);
        String typeCode = normalizeCode(request.getTypeCode(), "类型编码不能为空");
        String typeName = normalizeName(request.getTypeName(), "类型名称不能为空");
        if (orgUnitTypeRepository.existsByTenantIdAndTypeCode(tenantId, typeCode)) {
            throw new BizException(ErrorCode.CONFLICT, "组织类型编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        OrgUnitType entity = new OrgUnitType();
        entity.setTenantId(tenantId);
        entity.setTypeCode(typeCode);
        entity.setTypeName(typeName);
        entity.setSort(resolveSort(request.getSort()));
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setRemark(normalizeText(request.getRemark()));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return toTypeView(orgUnitTypeRepository.save(entity), 0L);
    }

    /**
     * 更新组织类型。
     *
     * @param id            类型ID
     * @param request       更新请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 类型视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public OrgUnitTypeView updateUnitType(Long id, OrgUnitTypeUpdateRequest request, String sessionTenant, boolean sysAdmin) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织类型ID不能为空");
        }
        OrgUnitType entity = orgUnitTypeRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_TYPE_NOT_FOUND, "组织类型不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (request != null) {
            if (request.getTypeName() != null) {
                entity.setTypeName(normalizeName(request.getTypeName(), "类型名称不能为空"));
            }
            if (request.getSort() != null) {
                entity.setSort(resolveSort(request.getSort()));
            }
            if (request.getStatus() != null) {
                entity.setStatus(resolveStatus(request.getStatus()));
            }
            entity.setRemark(normalizeText(request.getRemark()));
        }
        entity.setUpdateTime(LocalDateTime.now());
        OrgUnitType saved = orgUnitTypeRepository.save(entity);
        long unitCount = orgUnitRepository.findByTenantId(entity.getTenantId()).stream()
                .filter(unit -> Objects.equals(saved.getTypeCode(), unit.getTypeCode()))
                .count();
        return toTypeView(saved, unitCount);
    }

    /**
     * 删除组织类型。
     *
     * @param id            类型ID
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public void deleteUnitType(Long id, String sessionTenant, boolean sysAdmin) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织类型ID不能为空");
        }
        OrgUnitType entity = orgUnitTypeRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_TYPE_NOT_FOUND, "组织类型不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (orgUnitRepository.existsByTenantIdAndTypeCode(entity.getTenantId(), entity.getTypeCode())) {
            throw new BizException(ErrorCode.CONFLICT, "组织类型已被组织节点使用，无法删除");
        }
        orgUnitTypeRepository.delete(entity);
    }

    /**
     * 查询组织树。
     *
     * @param tenantId 租户ID
     * @return 组织树
     * @author 余炘洋
     * @date 2026-04-11
     */
    public List<OrgUnitView> listOrgUnits(String tenantId) {
        String resolvedTenantId = requireTenantId(tenantId);
        List<OrgUnit> units = orgUnitRepository.findByTenantId(resolvedTenantId);
        Map<String, OrgUnitType> typeMapping = new HashMap<>();
        for (OrgUnitType type : orgUnitTypeRepository.findByTenantIdOrderBySortAscIdAsc(resolvedTenantId)) {
            typeMapping.put(type.getTypeCode(), type);
        }
        Map<Long, RbacUser> userMapping = userRepository.findByTenantIdOrderByIdAsc(resolvedTenantId).stream()
                .collect(Collectors.toMap(RbacUser::getId, item -> item, (left, right) -> left));
        Map<Long, Long> memberCountMapping = new HashMap<>();
        for (UserOrgMembership membership : userOrgMembershipRepository.findByTenantId(resolvedTenantId)) {
            memberCountMapping.merge(membership.getOrgUnitId(), 1L, Long::sum);
        }
        List<OrgUnit> sortedUnits = new ArrayList<>(units);
        sortedUnits.sort(Comparator.comparingInt((OrgUnit unit) -> unit.getTreeLevel() == null ? Integer.MAX_VALUE : unit.getTreeLevel())
                .thenComparingInt(unit -> unit.getSort() == null ? 0 : unit.getSort())
                .thenComparingLong(unit -> unit.getId() == null ? 0L : unit.getId()));

        Map<Long, OrgUnitView> mapping = new LinkedHashMap<>();
        for (OrgUnit unit : sortedUnits) {
            OrgUnitType type = typeMapping.get(unit.getTypeCode());
            mapping.put(unit.getId(), toUnitView(unit, type, userMapping, memberCountMapping.getOrDefault(unit.getId(), 0L)));
        }
        List<OrgUnitView> roots = new ArrayList<>();
        for (OrgUnit unit : sortedUnits) {
            OrgUnitView view = mapping.get(unit.getId());
            OrgUnitView parent = unit.getParentId() == null ? null : mapping.get(unit.getParentId());
            if (parent == null) {
                roots.add(view);
            } else {
                parent.getChildren().add(view);
            }
        }
        roots.forEach(this::sortChildren);
        roots.sort(this::compareUnitView);
        return roots;
    }

    /**
     * 创建组织节点。
     *
     * @param request       创建请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 组织视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public OrgUnitView createOrgUnit(OrgUnitCreateRequest request, String sessionTenant, boolean sysAdmin) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织节点请求不能为空");
        }
        String tenantId = resolveTenantId(request.getTenantId(), sessionTenant, sysAdmin);
        String unitCode = normalizeCode(request.getUnitCode(), "组织编码不能为空");
        String unitName = normalizeName(request.getUnitName(), "组织名称不能为空");
        String typeCode = normalizeCode(request.getTypeCode(), "组织类型不能为空");
        if (orgUnitRepository.existsByTenantIdAndUnitCode(tenantId, unitCode)) {
            throw new BizException(ErrorCode.CONFLICT, "组织编码已存在");
        }
        ensureTypeExists(tenantId, typeCode);
        Long parentId = validateParent(tenantId, request.getParentId(), null);
        LocalDateTime now = LocalDateTime.now();
        OrgUnit entity = new OrgUnit();
        entity.setTenantId(tenantId);
        entity.setUnitCode(unitCode);
        entity.setUnitName(unitName);
        entity.setTypeCode(typeCode);
        entity.setLeaderUserId(validateLeader(tenantId, request.getLeaderUserId()));
        entity.setParentId(parentId);
        entity.setTreePath(null);
        entity.setTreeLevel(0);
        entity.setSort(resolveSort(request.getSort()));
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setRemark(normalizeText(request.getRemark()));
        entity.setTagText(writeTags(request.getTags()));
        entity.setAttributeJson(normalizeJson(request.getAttributeJson()));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        OrgUnit saved = orgUnitRepository.save(entity);
        rebuildTree(tenantId);
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(tenantId, AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> getOrgUnitView(saved.getId(), tenantId)
        );
    }

    /**
     * 更新组织节点。
     *
     * @param id            节点ID
     * @param request       更新请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 组织视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public OrgUnitView updateOrgUnit(Long id, OrgUnitUpdateRequest request, String sessionTenant, boolean sysAdmin) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织节点ID不能为空");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织节点请求不能为空");
        }
        OrgUnit entity = orgUnitRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "组织节点不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        String tenantId = entity.getTenantId();
        if (request.getUnitName() != null) {
            entity.setUnitName(normalizeName(request.getUnitName(), "组织名称不能为空"));
        }
        if (request.getTypeCode() != null) {
            String typeCode = normalizeCode(request.getTypeCode(), "组织类型不能为空");
            ensureTypeExists(tenantId, typeCode);
            entity.setTypeCode(typeCode);
        }
        if (request.isLeaderUserIdSpecified()) {
            entity.setLeaderUserId(validateLeader(tenantId, request.getLeaderUserId()));
        }
        entity.setParentId(validateParent(tenantId, request.getParentId(), entity.getId()));
        if (request.getSort() != null) {
            entity.setSort(resolveSort(request.getSort()));
        }
        if (request.getStatus() != null) {
            entity.setStatus(resolveStatus(request.getStatus()));
        }
        entity.setRemark(normalizeText(request.getRemark()));
        if (request.getTags() != null) {
            entity.setTagText(writeTags(request.getTags()));
        }
        if (request.getAttributeJson() != null) {
            entity.setAttributeJson(normalizeJson(request.getAttributeJson()));
        }
        entity.setUpdateTime(LocalDateTime.now());
        orgUnitRepository.save(entity);
        rebuildTree(tenantId);
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(tenantId, AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> getOrgUnitView(entity.getId(), tenantId)
        );
    }

    /**
     * 删除组织节点。
     *
     * @param id            节点ID
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public void deleteOrgUnit(Long id, String sessionTenant, boolean sysAdmin) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织节点ID不能为空");
        }
        OrgUnit entity = orgUnitRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "组织节点不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (orgUnitRepository.existsByTenantIdAndParentId(entity.getTenantId(), entity.getId())) {
            throw new BizException(ErrorCode.CONFLICT, "组织节点存在子节点，无法删除");
        }
        if (userOrgMembershipRepository.existsByOrgUnitId(entity.getId())) {
            throw new BizException(ErrorCode.CONFLICT, "组织节点已关联用户，无法删除");
        }
        for (OrgUnitRelation relation : orgUnitRelationRepository.findByTenantId(entity.getTenantId())) {
            if (Objects.equals(relation.getSourceOrgUnitId(), entity.getId())
                    || Objects.equals(relation.getTargetOrgUnitId(), entity.getId())) {
                throw new BizException(ErrorCode.CONFLICT, "组织节点已存在组织关系，无法删除");
            }
        }
        authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(entity.getTenantId(), AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> {
                    orgUnitRepository.delete(entity);
                    return null;
                }
        );
    }

    /**
     * 查询组织关系列表。
     *
     * @param tenantId 租户ID
     * @return 关系列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    public List<OrgUnitRelationView> listRelations(String tenantId) {
        String resolvedTenantId = requireTenantId(tenantId);
        Map<Long, OrgUnit> unitMapping = orgUnitRepository.findByTenantId(resolvedTenantId).stream()
                .collect(Collectors.toMap(OrgUnit::getId, item -> item, (left, right) -> left));
        return orgUnitRelationRepository.findByTenantId(resolvedTenantId).stream()
                .sorted(Comparator.comparingInt((OrgUnitRelation relation) -> relation.getSort() == null ? 0 : relation.getSort())
                        .thenComparingLong(relation -> relation.getId() == null ? 0L : relation.getId()))
                .map(relation -> toRelationView(relation, unitMapping))
                .toList();
    }

    /**
     * 查询组织关系消费工作台。
     *
     * @param tenantId 租户ID
     * @return 工作台视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    public OrgRelationWorkbenchView relationWorkbench(String tenantId) {
        String resolvedTenantId = requireTenantId(tenantId);
        LocalDateTime now = LocalDateTime.now();
        OrgRelationWorkbenchView workbench = new OrgRelationWorkbenchView();
        workbench.setGeneratedAt(now);

        List<OrgUnit> tenantUnits = orgUnitRepository.findByTenantId(resolvedTenantId);
        if (tenantUnits.isEmpty()) {
            return workbench;
        }
        Map<Long, OrgUnit> unitMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null)
                .collect(Collectors.toMap(OrgUnit::getId, item -> item, (left, right) -> left));
        Map<Long, RbacUser> userMapping = userRepository.findByTenantIdOrderByIdAsc(resolvedTenantId).stream()
                .collect(Collectors.toMap(RbacUser::getId, item -> item, (left, right) -> left));

        List<OrgRelationOpsTaskView> tasks = orgUnitRelationRepository.findByTenantId(resolvedTenantId).stream()
                .filter(this::isActiveRelation)
                .map(relation -> buildRelationOpsTask(relation, unitMapping, tenantUnits, userMapping))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((OrgRelationOpsTaskView item) -> priorityWeight(item.getPriority())).reversed()
                        .thenComparing(Comparator.comparingInt((OrgRelationOpsTaskView item) -> readinessWeight(item.getReadinessStatus())).reversed())
                        .thenComparing(item -> item.getRelationId() == null ? 0L : item.getRelationId()))
                .toList();

        workbench.setTasks(tasks);
        workbench.setTotalTasks(tasks.size());
        workbench.setP0Count((int) tasks.stream().filter(item -> PRIORITY_P0.equals(item.getPriority())).count());
        workbench.setP1Count((int) tasks.stream().filter(item -> PRIORITY_P1.equals(item.getPriority())).count());
        workbench.setP2Count((int) tasks.stream().filter(item -> PRIORITY_P2.equals(item.getPriority())).count());
        return workbench;
    }

    /**
     * 解析组织关系路由。
     *
     * @param tenantId        租户ID
     * @param sourceOrgUnitId 源组织ID
     * @param relationType    关系类型
     * @return 路由解析结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    public OrgRelationRouteResolveView resolveRelationRoute(String tenantId, Long sourceOrgUnitId, String relationType) {
        String resolvedTenantId = requireTenantId(tenantId);
        Long resolvedSourceOrgUnitId = requireOrgUnitId(sourceOrgUnitId, "源组织节点不能为空");
        String normalizedRelationType = normalizeCode(relationType, "关系类型不能为空");

        OrgUnit source = orgUnitRepository.findByIdAndTenantId(resolvedSourceOrgUnitId, resolvedTenantId)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "源组织节点不存在"));
        List<OrgUnit> tenantUnits = orgUnitRepository.findByTenantId(resolvedTenantId);
        Map<Long, OrgUnit> unitMapping = tenantUnits.stream()
                .filter(unit -> unit.getId() != null)
                .collect(Collectors.toMap(OrgUnit::getId, item -> item, (left, right) -> left));
        Map<Long, RbacUser> userMapping = userRepository.findByTenantIdOrderByIdAsc(resolvedTenantId).stream()
                .collect(Collectors.toMap(RbacUser::getId, item -> item, (left, right) -> left));

        List<OrgUnitRelation> relations = orgUnitRelationRepository.findByTenantIdAndStatusAndRelationTypeAndSourceOrgUnitIdIn(
                resolvedTenantId,
                ACTIVE_STATUS,
                normalizedRelationType,
                List.of(resolvedSourceOrgUnitId)
        ).stream().sorted(Comparator.comparingInt((OrgUnitRelation item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparingLong(item -> item.getId() == null ? 0L : item.getId()))
                .toList();

        OrgRelationRouteResolveView view = new OrgRelationRouteResolveView();
        view.setTenantId(resolvedTenantId);
        view.setSourceOrgUnitId(source.getId());
        view.setSourceOrgUnitCode(source.getUnitCode());
        view.setSourceOrgUnitName(source.getUnitName());
        view.setRelationType(normalizedRelationType);
        view.setMatched(!relations.isEmpty());
        view.setAmbiguous(relations.size() > 1);
        view.setCandidateCount(relations.size());

        if (relations.isEmpty()) {
            view.setWarnings(List.of("当前源组织不存在该关系类型的启用路由。"));
            return view;
        }

        List<OrgRelationRouteCandidateView> candidates = relations.stream()
                .map(relation -> buildRouteCandidate(relation, unitMapping, tenantUnits, userMapping))
                .filter(Objects::nonNull)
                .toList();
        view.setCandidates(candidates);
        view.setCandidateCount(candidates.size());
        view.setMatched(!candidates.isEmpty());
        view.setAmbiguous(candidates.size() > 1);
        if (candidates.isEmpty()) {
            view.setWarnings(List.of("当前路由配置缺少有效目标组织，无法完成解析。"));
            return view;
        }

        OrgRelationRouteCandidateView primary = candidates.getFirst();
        view.setPrimaryRelationId(primary.getRelationId());
        view.setPrimaryRelationName(primary.getRelationName());
        view.setPrimaryTargetOrgUnitId(primary.getTargetOrgUnitId());
        view.setPrimaryTargetOrgUnitCode(primary.getTargetOrgUnitCode());
        view.setPrimaryTargetOrgUnitName(primary.getTargetOrgUnitName());
        view.setPrimaryLeaderUserId(primary.getTargetLeaderUserId());
        view.setPrimaryLeaderName(primary.getTargetLeaderName());
        view.setPrimaryScopeUnitCount(primary.getScopeUnitCount());
        view.setPrimaryScopeUnitCodes(new ArrayList<>(primary.getScopeUnitCodes()));
        view.setPrimaryRouteScopeSummary(primary.getRouteScopeSummary());

        List<String> warnings = new ArrayList<>();
        if (view.isAmbiguous()) {
            warnings.add("当前存在多个候选目标，系统已按排序值选择主路由。");
        }
        if (primary.getTargetLeaderUserId() == null) {
            warnings.add("主路由目标组织未配置负责人，暂不适合直接自动派单。");
        }
        view.setWarnings(warnings);
        return view;
    }

    /**
     * 应用组织模板。
     *
     * @param request       初始化请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 结果提示
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public String applyTemplate(OrgTemplateApplyRequest request, String sessionTenant, boolean sysAdmin) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织模板请求不能为空");
        }
        String tenantId = resolveTenantId(request.getTenantId(), sessionTenant, sysAdmin);
        OrgTemplate template = findTemplate(normalizeCode(request.getTemplateCode(), "模板编码不能为空"));
        if (!orgUnitTypeRepository.findByTenantIdOrderBySortAscIdAsc(tenantId).isEmpty()
                || !orgUnitRepository.findByTenantId(tenantId).isEmpty()
                || !orgUnitRelationRepository.findByTenantId(tenantId).isEmpty()) {
            throw new BizException(ErrorCode.CONFLICT, "当前租户组织域已存在数据，请清空后再初始化模板");
        }

        LocalDateTime now = LocalDateTime.now();
        String rootUnitNameOverride = normalizeOptionalName(request.getRootUnitName());
        Map<String, OrgUnitType> typeMapping = new LinkedHashMap<>();
        for (TemplateType templateType : template.types()) {
            OrgUnitType entity = new OrgUnitType();
            entity.setTenantId(tenantId);
            entity.setTypeCode(templateType.typeCode());
            entity.setTypeName(templateType.typeName());
            entity.setSort(templateType.sort());
            entity.setStatus(ACTIVE_STATUS);
            entity.setRemark(templateType.remark());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            typeMapping.put(templateType.typeCode(), orgUnitTypeRepository.save(entity));
        }

        Map<String, OrgUnit> unitMapping = new LinkedHashMap<>();
        for (TemplateUnit templateUnit : template.units()) {
            if (!typeMapping.containsKey(templateUnit.typeCode())) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "组织模板定义缺少类型: " + templateUnit.typeCode());
            }
            OrgUnit entity = new OrgUnit();
            entity.setTenantId(tenantId);
            entity.setUnitCode(templateUnit.unitCode());
            entity.setUnitName(resolveTemplateUnitName(templateUnit, rootUnitNameOverride));
            entity.setTypeCode(templateUnit.typeCode());
            entity.setLeaderUserId(null);
            entity.setParentId(resolveTemplateParentId(templateUnit.parentCode(), unitMapping));
            entity.setTreePath(null);
            entity.setTreeLevel(0);
            entity.setSort(templateUnit.sort());
            entity.setStatus(ACTIVE_STATUS);
            entity.setRemark(templateUnit.remark());
            entity.setTagText(writeTags(templateUnit.tags()));
            entity.setAttributeJson(normalizeJson(templateUnit.attributeJson()));
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            unitMapping.put(templateUnit.unitCode(), orgUnitRepository.save(entity));
        }

        for (TemplateRelation templateRelation : template.relations()) {
            OrgUnit source = unitMapping.get(templateRelation.sourceUnitCode());
            OrgUnit target = unitMapping.get(templateRelation.targetUnitCode());
            if (source == null || target == null) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "组织模板定义缺少关系节点");
            }
            OrgUnitRelation entity = new OrgUnitRelation();
            entity.setTenantId(tenantId);
            entity.setSourceOrgUnitId(source.getId());
            entity.setTargetOrgUnitId(target.getId());
            entity.setRelationType(templateRelation.relationType());
            entity.setRelationName(templateRelation.relationName());
            entity.setSort(templateRelation.sort());
            entity.setStatus(ACTIVE_STATUS);
            entity.setRemark(templateRelation.remark());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            orgUnitRelationRepository.save(entity);
        }
        rebuildTree(tenantId);
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(tenantId, AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> "已初始化模板“" + template.templateName() + "”，创建 "
                        + template.types().size() + " 个组织类型、"
                        + template.units().size() + " 个组织节点、"
                        + template.relations().size() + " 条组织关系。"
        );
    }

    /**
     * 创建组织关系。
     *
     * @param request       创建请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 关系视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public OrgUnitRelationView createRelation(OrgUnitRelationCreateRequest request, String sessionTenant, boolean sysAdmin) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织关系请求不能为空");
        }
        String tenantId = resolveTenantId(request.getTenantId(), sessionTenant, sysAdmin);
        Long sourceOrgUnitId = requireOrgUnitId(request.getSourceOrgUnitId(), "源组织节点不能为空");
        Long targetOrgUnitId = requireOrgUnitId(request.getTargetOrgUnitId(), "目标组织节点不能为空");
        String relationType = normalizeCode(request.getRelationType(), "关系类型不能为空");
        validateRelationEndpoints(tenantId, sourceOrgUnitId, targetOrgUnitId);
        if (orgUnitRelationRepository.existsByTenantIdAndSourceOrgUnitIdAndTargetOrgUnitIdAndRelationType(
                tenantId, sourceOrgUnitId, targetOrgUnitId, relationType)) {
            throw new BizException(ErrorCode.CONFLICT, "组织关系已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        OrgUnitRelation entity = new OrgUnitRelation();
        entity.setTenantId(tenantId);
        entity.setSourceOrgUnitId(sourceOrgUnitId);
        entity.setTargetOrgUnitId(targetOrgUnitId);
        entity.setRelationType(relationType);
        entity.setRelationName(normalizeText(request.getRelationName()));
        entity.setSort(resolveSort(request.getSort()));
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setRemark(normalizeText(request.getRemark()));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        OrgUnitRelation saved = orgUnitRelationRepository.save(entity);
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(tenantId, AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> listRelations(tenantId).stream()
                        .filter(item -> Objects.equals(item.getId(), saved.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_NOT_FOUND, "组织关系不存在"))
        );
    }

    /**
     * 更新组织关系。
     *
     * @param id            关系ID
     * @param request       更新请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 关系视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public OrgUnitRelationView updateRelation(Long id,
                                              OrgUnitRelationUpdateRequest request,
                                              String sessionTenant,
                                              boolean sysAdmin) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织关系ID不能为空");
        }
        OrgUnitRelation entity = orgUnitRelationRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_NOT_FOUND, "组织关系不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        String tenantId = entity.getTenantId();
        if (request != null) {
            if (request.getSourceOrgUnitId() != null) {
                entity.setSourceOrgUnitId(requireOrgUnitId(request.getSourceOrgUnitId(), "源组织节点不能为空"));
            }
            if (request.getTargetOrgUnitId() != null) {
                entity.setTargetOrgUnitId(requireOrgUnitId(request.getTargetOrgUnitId(), "目标组织节点不能为空"));
            }
            if (request.getRelationType() != null) {
                entity.setRelationType(normalizeCode(request.getRelationType(), "关系类型不能为空"));
            }
            validateRelationEndpoints(tenantId, entity.getSourceOrgUnitId(), entity.getTargetOrgUnitId());
            entity.setRelationName(normalizeText(request.getRelationName()));
            if (request.getSort() != null) {
                entity.setSort(resolveSort(request.getSort()));
            }
            if (request.getStatus() != null) {
                entity.setStatus(resolveStatus(request.getStatus()));
            }
            entity.setRemark(normalizeText(request.getRemark()));
        }
        entity.setUpdateTime(LocalDateTime.now());
        orgUnitRelationRepository.save(entity);
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(tenantId, AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> listRelations(tenantId).stream()
                        .filter(item -> Objects.equals(item.getId(), entity.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_NOT_FOUND, "组织关系不存在"))
        );
    }

    /**
     * 删除组织关系。
     *
     * @param id            关系ID
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Transactional
    public void deleteRelation(Long id, String sessionTenant, boolean sysAdmin) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "组织关系ID不能为空");
        }
        OrgUnitRelation entity = orgUnitRelationRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_NOT_FOUND, "组织关系不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(entity.getTenantId(), AuthorizationChangeKind.ORGANIZATION_SCOPE),
                () -> {
                    orgUnitRelationRepository.delete(entity);
                    return null;
                }
        );
    }

    /**
     * 规范化并校验父节点。
     *
     * @param tenantId  租户ID
     * @param parentId  父节点ID
     * @param currentId 当前节点ID
     * @return 合法父节点ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long validateParent(String tenantId, Long parentId, Long currentId) {
        if (parentId == null) {
            return null;
        }
        if (currentId != null && Objects.equals(parentId, currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "组织节点不能挂到自身下方");
        }
        OrgUnit parent = orgUnitRepository.findByIdAndTenantId(parentId, tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "父组织节点不存在"));
        if (currentId == null) {
            return parent.getId();
        }
        Map<Long, OrgUnit> mapping = new HashMap<>();
        for (OrgUnit item : orgUnitRepository.findByTenantId(tenantId)) {
            mapping.put(item.getId(), item);
        }
        if (isDescendant(parent.getId(), currentId, mapping)) {
            throw new BizException(ErrorCode.CONFLICT, "父组织节点不能是当前节点的下级");
        }
        return parent.getId();
    }

    /**
     * 判断候选父节点是否位于当前节点子树中。
     *
     * @param candidateParentId 候选父节点
     * @param currentId         当前节点
     * @param mapping           节点映射
     * @return 是否位于子树中
     * @author 余炘洋
     * @date 2026-04-11
     */
    private boolean isDescendant(Long candidateParentId, Long currentId, Map<Long, OrgUnit> mapping) {
        Long cursor = candidateParentId;
        while (cursor != null) {
            if (Objects.equals(cursor, currentId)) {
                return true;
            }
            OrgUnit parent = mapping.get(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    /**
     * 重建租户组织树路径与层级，避免树结构漂移。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void rebuildTree(String tenantId) {
        List<OrgUnit> units = orgUnitRepository.findByTenantId(tenantId);
        if (units.isEmpty()) {
            return;
        }
        Map<Long, OrgUnit> mapping = new HashMap<>();
        Map<Long, List<OrgUnit>> childrenMapping = new HashMap<>();
        for (OrgUnit unit : units) {
            mapping.put(unit.getId(), unit);
            childrenMapping.computeIfAbsent(unit.getParentId(), key -> new ArrayList<>()).add(unit);
        }
        Comparator<OrgUnit> comparator = Comparator
                .comparingInt((OrgUnit unit) -> unit.getSort() == null ? 0 : unit.getSort())
                .thenComparingLong(unit -> unit.getId() == null ? 0L : unit.getId());
        childrenMapping.values().forEach(list -> list.sort(comparator));

        Set<Long> visited = new LinkedHashSet<>();
        List<OrgUnit> roots = units.stream()
                .filter(unit -> unit.getParentId() == null || !mapping.containsKey(unit.getParentId()))
                .sorted(comparator)
                .toList();
        for (OrgUnit root : roots) {
            assignTree(root, 1, "/" + root.getId(), childrenMapping, visited);
        }
        for (OrgUnit unit : units.stream().sorted(comparator).toList()) {
            if (visited.contains(unit.getId())) {
                continue;
            }
            unit.setParentId(null);
            assignTree(unit, 1, "/" + unit.getId(), childrenMapping, visited);
        }
        orgUnitRepository.saveAll(units);
    }

    /**
     * 递归写入节点层级与路径。
     *
     * @param unit            当前节点
     * @param level           树层级
     * @param path            树路径
     * @param childrenMapping 子节点映射
     * @param visited         已访问节点
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void assignTree(OrgUnit unit,
                            int level,
                            String path,
                            Map<Long, List<OrgUnit>> childrenMapping,
                            Set<Long> visited) {
        if (unit == null || unit.getId() == null || !visited.add(unit.getId())) {
            return;
        }
        applyTreePosition(unit, level, path);
        for (OrgUnit child : childrenMapping.getOrDefault(unit.getId(), List.of())) {
            assignTree(child, level + 1, path + "/" + child.getId(), childrenMapping, visited);
        }
    }

    /**
     * 仅在树信息变更时更新节点，避免无意义写入。
     *
     * @param unit  节点
     * @param level 树层级
     * @param path  树路径
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void applyTreePosition(OrgUnit unit, int level, String path) {
        boolean changed = !Objects.equals(unit.getTreeLevel(), level)
                || !Objects.equals(unit.getTreePath(), path);
        unit.setTreeLevel(level);
        unit.setTreePath(path);
        if (changed) {
            unit.setUpdateTime(LocalDateTime.now());
        }
    }

    /**
     * 按节点ID获取组织视图。
     *
     * @param id       节点ID
     * @param tenantId 租户ID
     * @return 组织视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgUnitView getOrgUnitView(Long id, String tenantId) {
        return listOrgUnits(tenantId).stream()
                .map(root -> findUnitView(root, id))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "组织节点不存在"));
    }

    /**
     * 递归查找节点视图。
     *
     * @param current  当前节点
     * @param targetId 目标ID
     * @return 节点视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgUnitView findUnitView(OrgUnitView current, Long targetId) {
        if (current == null) {
            return null;
        }
        if (Objects.equals(current.getId(), targetId)) {
            return current;
        }
        for (OrgUnitView child : current.getChildren()) {
            OrgUnitView found = findUnitView(child, targetId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 校验组织类型是否存在。
     *
     * @param tenantId 租户ID
     * @param typeCode 类型编码
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void ensureTypeExists(String tenantId, String typeCode) {
        if (orgUnitTypeRepository.findByTenantIdAndTypeCode(tenantId, typeCode).isEmpty()) {
            throw new BizException(ErrorCode.ORG_UNIT_TYPE_NOT_FOUND, "组织类型不存在");
        }
    }

    /**
     * 将类型实体转换为视图。
     *
     * @param entity    类型实体
     * @param unitCount 节点数量
     * @return 类型视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgUnitTypeView toTypeView(OrgUnitType entity, long unitCount) {
        OrgUnitTypeView view = new OrgUnitTypeView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTypeCode(entity.getTypeCode());
        view.setTypeName(entity.getTypeName());
        view.setSort(entity.getSort());
        view.setStatus(entity.getStatus());
        view.setRemark(entity.getRemark());
        view.setUnitCount(unitCount);
        return view;
    }

    /**
     * 将组织实体转换为视图。
     *
     * @param entity      组织实体
     * @param type        类型实体
     * @param memberCount 成员数量
     * @return 组织视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgUnitView toUnitView(OrgUnit entity,
                                   OrgUnitType type,
                                   Map<Long, RbacUser> userMapping,
                                   long memberCount) {
        OrgUnitView view = new OrgUnitView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setUnitCode(entity.getUnitCode());
        view.setUnitName(entity.getUnitName());
        view.setTypeCode(entity.getTypeCode());
        view.setTypeName(type == null ? entity.getTypeCode() : type.getTypeName());
        view.setLeaderUserId(entity.getLeaderUserId());
        RbacUser leader = entity.getLeaderUserId() == null ? null : userMapping.get(entity.getLeaderUserId());
        if (leader != null) {
            view.setLeaderUsername(leader.getUsername());
            view.setLeaderNickname(leader.getNickname());
        }
        view.setParentId(entity.getParentId());
        view.setTreePath(entity.getTreePath());
        view.setTreeLevel(entity.getTreeLevel());
        view.setSort(entity.getSort());
        view.setStatus(entity.getStatus());
        view.setRemark(entity.getRemark());
        view.setTags(parseTags(entity.getTagText()));
        view.setAttributeJson(entity.getAttributeJson());
        view.setMemberCount(memberCount);
        return view;
    }

    /**
     * 将组织关系实体转换为视图。
     *
     * @param entity      关系实体
     * @param unitMapping 节点映射
     * @return 关系视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgUnitRelationView toRelationView(OrgUnitRelation entity, Map<Long, OrgUnit> unitMapping) {
        OrgUnitRelationView view = new OrgUnitRelationView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setSourceOrgUnitId(entity.getSourceOrgUnitId());
        view.setTargetOrgUnitId(entity.getTargetOrgUnitId());
        OrgUnit source = unitMapping.get(entity.getSourceOrgUnitId());
        OrgUnit target = unitMapping.get(entity.getTargetOrgUnitId());
        view.setSourceOrgUnitName(source == null ? null : source.getUnitName());
        view.setTargetOrgUnitName(target == null ? null : target.getUnitName());
        view.setRelationType(entity.getRelationType());
        view.setRelationName(entity.getRelationName());
        view.setSort(entity.getSort());
        view.setStatus(entity.getStatus());
        view.setRemark(entity.getRemark());
        return view;
    }

    /**
     * 构建路由候选。
     *
     * @param relation    组织关系
     * @param unitMapping 组织节点映射
     * @param tenantUnits 租户内全部节点
     * @param userMapping 用户映射
     * @return 路由候选
     * @author 余炘洋
     * @date 2026-04-12
     */
    private OrgRelationRouteCandidateView buildRouteCandidate(OrgUnitRelation relation,
                                                              Map<Long, OrgUnit> unitMapping,
                                                              List<OrgUnit> tenantUnits,
                                                              Map<Long, RbacUser> userMapping) {
        if (relation == null) {
            return null;
        }
        OrgUnit target = unitMapping.get(relation.getTargetOrgUnitId());
        if (target == null) {
            return null;
        }
        List<String> scopeUnitCodes = resolveScopeUnitCodes(tenantUnits, target);
        RbacUser targetLeader = target.getLeaderUserId() == null ? null : userMapping.get(target.getLeaderUserId());
        OrgRelationRouteCandidateView view = new OrgRelationRouteCandidateView();
        view.setRelationId(relation.getId());
        view.setRelationName(StringUtils.hasText(relation.getRelationName()) ? relation.getRelationName() : defaultRelationName(normalizeRelationType(relation.getRelationType())));
        view.setRelationType(normalizeRelationType(relation.getRelationType()));
        view.setTargetOrgUnitId(target.getId());
        view.setTargetOrgUnitCode(target.getUnitCode());
        view.setTargetOrgUnitName(target.getUnitName());
        view.setTargetLeaderUserId(target.getLeaderUserId());
        view.setTargetLeaderName(resolveLeaderName(targetLeader, target.getLeaderUserId()));
        view.setSort(relation.getSort());
        view.setScopeUnitCount(scopeUnitCodes.size());
        view.setScopeUnitCodes(scopeUnitCodes);
        view.setRouteScopeSummary(buildRouteScopeSummary(target.getUnitName(), scopeUnitCodes.size()));
        return view;
    }

    /**
     * 构建组织关系消费任务。
     *
     * @param relation     组织关系
     * @param unitMapping  组织节点映射
     * @param tenantUnits  租户内全部节点
     * @param userMapping  用户映射
     * @return 消费任务
     * @author 余炘洋
     * @date 2026-04-12
     */
    private OrgRelationOpsTaskView buildRelationOpsTask(OrgUnitRelation relation,
                                                        Map<Long, OrgUnit> unitMapping,
                                                        List<OrgUnit> tenantUnits,
                                                        Map<Long, RbacUser> userMapping) {
        if (relation == null) {
            return null;
        }
        String relationType = normalizeRelationType(relation.getRelationType());
        if (!StringUtils.hasText(relationType)) {
            return null;
        }
        OrgUnit source = unitMapping.get(relation.getSourceOrgUnitId());
        OrgUnit target = unitMapping.get(relation.getTargetOrgUnitId());
        if (source == null || target == null) {
            return null;
        }
        List<String> missingCapabilities = new ArrayList<>();
        boolean targetLeaderMissing = target.getLeaderUserId() == null;
        if (targetLeaderMissing) {
            missingCapabilities.add("目标组织未配置负责人，当前无法自动落到责任人。");
        }
        if (requiresSourceLeader(relationType) && source.getLeaderUserId() == null) {
            missingCapabilities.add("源组织未配置负责人，当前缺少发起侧责任人。");
        }
        if (!StringUtils.hasText(relation.getRelationName())) {
            missingCapabilities.add("关系名称未明确，建议补充业务口径名称。");
        }

        int scopeUnitCount = countScopeUnits(tenantUnits, target);
        RbacUser targetLeader = target.getLeaderUserId() == null ? null : userMapping.get(target.getLeaderUserId());
        String readinessStatus = resolveReadinessStatus(missingCapabilities, targetLeaderMissing);

        OrgRelationOpsTaskView view = new OrgRelationOpsTaskView();
        view.setRelationId(relation.getId());
        view.setRelationType(relationType);
        view.setRelationName(StringUtils.hasText(relation.getRelationName()) ? relation.getRelationName() : defaultRelationName(relationType));
        view.setSourceOrgUnitName(source.getUnitName());
        view.setTargetOrgUnitName(target.getUnitName());
        view.setSourceOrgUnitCode(source.getUnitCode());
        view.setTargetOrgUnitCode(target.getUnitCode());
        view.setSceneCode(resolveSceneCode(relationType));
        view.setSceneName(resolveSceneName(relationType));
        view.setPriority(resolvePriority(relationType, readinessStatus));
        view.setReadinessStatus(readinessStatus);
        view.setSuggestedAction(resolveSuggestedAction(relationType, readinessStatus, target.getUnitName()));
        view.setTriggerReason(buildTriggerReason(relationType, source.getUnitName(), target.getUnitName(), scopeUnitCount));
        view.setRouteScopeSummary(buildRouteScopeSummary(target.getUnitName(), scopeUnitCount));
        view.setScopeUnitCount(scopeUnitCount);
        view.setTargetLeaderUserId(target.getLeaderUserId());
        view.setTargetLeaderName(resolveLeaderName(targetLeader, target.getLeaderUserId()));
        view.setMissingCapabilities(missingCapabilities);
        return view;
    }

    /**
     * 转换组织模板视图。
     *
     * @param template 模板定义
     * @return 模板视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgTemplateView toTemplateView(OrgTemplate template) {
        OrgTemplateView view = new OrgTemplateView();
        view.setTemplateCode(template.templateCode());
        view.setTemplateName(template.templateName());
        view.setIndustry(template.industry());
        view.setDescription(template.description());
        view.setTypeCount(template.types().size());
        view.setUnitCount(template.units().size());
        view.setRelationCount(template.relations().size());
        view.setDefaultRootUnitName(resolveDefaultRootUnitName(template));
        return view;
    }

    /**
     * 判断组织关系是否启用。
     *
     * @param relation 组织关系
     * @return 是否启用
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean isActiveRelation(OrgUnitRelation relation) {
        return relation != null && (relation.getStatus() == null || Objects.equals(relation.getStatus(), ACTIVE_STATUS));
    }

    /**
     * 排序子节点。
     *
     * @param view 节点视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void sortChildren(OrgUnitView view) {
        view.getChildren().forEach(this::sortChildren);
        view.getChildren().sort(this::compareUnitView);
    }

    /**
     * 比较两个组织节点顺序。
     *
     * @param left  左节点
     * @param right 右节点
     * @return 比较结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    private int compareUnitView(OrgUnitView left, OrgUnitView right) {
        int leftSort = left.getSort() == null ? 0 : left.getSort();
        int rightSort = right.getSort() == null ? 0 : right.getSort();
        if (leftSort != rightSort) {
            return Integer.compare(leftSort, rightSort);
        }
        long leftId = left.getId() == null ? 0L : left.getId();
        long rightId = right.getId() == null ? 0L : right.getId();
        return Long.compare(leftId, rightId);
    }

    /**
     * 解析租户ID。
     *
     * @param requestTenant 请求租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 租户ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String resolveTenantId(String requestTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            if (!StringUtils.hasText(requestTenant)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "请先选择要管理的租户");
            }
            return requestTenant.trim();
        }
        if (!StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        return sessionTenant.trim();
    }

    /**
     * 校验租户访问权限。
     *
     * @param targetTenant  目标租户
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void validateTenantAccess(String targetTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            return;
        }
        if (!StringUtils.hasText(targetTenant) || !StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        if (!targetTenant.trim().equals(sessionTenant.trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
    }

    /**
     * 要求租户ID不能为空。
     *
     * @param tenantId 租户ID
     * @return 规范化后的租户ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请先选择要管理的租户");
        }
        return tenantId.trim();
    }

    /**
     * 规范化编码。
     *
     * @param value        原始值
     * @param errorMessage 错误信息
     * @return 规范编码
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalizeCode(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化名称。
     *
     * @param value        原始值
     * @param errorMessage 错误信息
     * @return 规范名称
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalizeName(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return value.trim();
    }

    /**
     * 规范化文本。
     *
     * @param value 原始值
     * @return 规范文本
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 标准化关系类型。
     *
     * @param relationType 原始关系类型
     * @return 标准化结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String normalizeRelationType(String relationType) {
        return StringUtils.hasText(relationType) ? relationType.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 是否要求源组织负责人。
     *
     * @param relationType 关系类型
     * @return 是否要求
     * @author 余炘洋
     * @date 2026-04-12
     */
    private boolean requiresSourceLeader(String relationType) {
        return "SUPERVISE".equals(relationType)
                || "COOPERATE".equals(relationType)
                || "MATRIX".equals(relationType);
    }

    /**
     * 解析目标组织覆盖的节点编码。
     *
     * @param tenantUnits 租户内全部节点
     * @param target      目标组织
     * @return 编码列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> resolveScopeUnitCodes(List<OrgUnit> tenantUnits, OrgUnit target) {
        if (tenantUnits == null || tenantUnits.isEmpty() || target == null || target.getId() == null) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (OrgUnit unit : tenantUnits) {
            if (unit == null || !StringUtils.hasText(unit.getUnitCode()) || unit.getId() == null) {
                continue;
            }
            if (Objects.equals(unit.getId(), target.getId())) {
                codes.add(unit.getUnitCode().trim().toUpperCase(Locale.ROOT));
                continue;
            }
            if (StringUtils.hasText(target.getTreePath())
                    && StringUtils.hasText(unit.getTreePath())
                    && unit.getTreePath().startsWith(target.getTreePath() + "/")) {
                codes.add(unit.getUnitCode().trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 统计目标组织可覆盖的节点数。
     *
     * @param tenantUnits 租户内全部节点
     * @param target      目标组织
     * @return 节点数
     * @author 余炘洋
     * @date 2026-04-12
     */
    private int countScopeUnits(List<OrgUnit> tenantUnits, OrgUnit target) {
        int count = resolveScopeUnitCodes(tenantUnits, target).size();
        return count == 0 ? 1 : count;
    }

    /**
     * 解析就绪状态。
     *
     * @param missingCapabilities 缺口列表
     * @param targetLeaderMissing 是否缺少目标负责人
     * @return 就绪状态
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveReadinessStatus(List<String> missingCapabilities, boolean targetLeaderMissing) {
        if (targetLeaderMissing) {
            return READINESS_BLOCKED;
        }
        if (missingCapabilities != null && !missingCapabilities.isEmpty()) {
            return READINESS_PENDING;
        }
        return READINESS_READY;
    }

    /**
     * 解析优先级。
     *
     * @param relationType    关系类型
     * @param readinessStatus 就绪状态
     * @return 优先级
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolvePriority(String relationType, String readinessStatus) {
        if (READINESS_BLOCKED.equals(readinessStatus)) {
            return PRIORITY_P0;
        }
        return switch (relationType) {
            case "SERVICE", "SUPERVISE", "MATRIX" -> PRIORITY_P1;
            case "COOPERATE", "OWNERSHIP" -> PRIORITY_P2;
            default -> PRIORITY_P2;
        };
    }

    /**
     * 默认关系名称。
     *
     * @param relationType 关系类型
     * @return 默认名称
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String defaultRelationName(String relationType) {
        return switch (relationType) {
            case "SUPERVISE" -> "主管关系";
            case "COOPERATE" -> "协同关系";
            case "SERVICE" -> "服务归口";
            case "OWNERSHIP" -> "归口关系";
            case "MATRIX" -> "矩阵关系";
            default -> relationType;
        };
    }

    /**
     * 解析场景编码。
     *
     * @param relationType 关系类型
     * @return 场景编码
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveSceneCode(String relationType) {
        return switch (relationType) {
            case "SUPERVISE" -> "SUPERVISE_BOARD";
            case "COOPERATE" -> "COOPERATE_TASK";
            case "SERVICE" -> "SERVICE_DESK";
            case "OWNERSHIP" -> "OWNERSHIP_GOVERNANCE";
            case "MATRIX" -> "MATRIX_REPORT";
            default -> "ORG_RELATION";
        };
    }

    /**
     * 解析场景名称。
     *
     * @param relationType 关系类型
     * @return 场景名称
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveSceneName(String relationType) {
        return switch (relationType) {
            case "SUPERVISE" -> "主管督办";
            case "COOPERATE" -> "协同任务";
            case "SERVICE" -> "服务归口";
            case "OWNERSHIP" -> "归口治理";
            case "MATRIX" -> "矩阵汇报";
            default -> "关系消费";
        };
    }

    /**
     * 解析建议动作。
     *
     * @param relationType    关系类型
     * @param readinessStatus 就绪状态
     * @param targetUnitName  目标组织名称
     * @return 建议动作
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveSuggestedAction(String relationType, String readinessStatus, String targetUnitName) {
        boolean blocked = READINESS_BLOCKED.equals(readinessStatus);
        return switch (relationType) {
            case "SUPERVISE" -> blocked
                    ? "先为目标组织补齐负责人，再配置监管报表、督办台账与升级入口。"
                    : "可围绕“" + targetUnitName + "”配置监管报表、督办台账与升级入口。";
            case "COOPERATE" -> blocked
                    ? "先补齐目标组织负责人，再接入协同任务池或联合审批入口。"
                    : "可为跨组织事项建立协同任务池、联合审批或共享跟进入口。";
            case "SERVICE" -> blocked
                    ? "先补齐目标组织负责人，再将服务单、支持请求路由到该组织。"
                    : "可将服务单、支持请求和售后协作统一路由到目标负责人。";
            case "OWNERSHIP" -> blocked
                    ? "先补齐目标组织负责人，再配置主责归口与责任台账。"
                    : "可将主责归口、主数据维护与责任台账统一落到目标组织。";
            case "MATRIX" -> blocked
                    ? "先补齐目标组织负责人，再配置双线汇报与矩阵审批口径。"
                    : "可为矩阵事项配置双线汇报、联合考核与矩阵审批口径。";
            default -> "建议结合业务场景补齐消费入口。";
        };
    }

    /**
     * 构建触发原因。
     *
     * @param relationType   关系类型
     * @param sourceUnitName 源组织名称
     * @param targetUnitName 目标组织名称
     * @param scopeUnitCount 路由范围节点数
     * @return 触发原因
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String buildTriggerReason(String relationType,
                                      String sourceUnitName,
                                      String targetUnitName,
                                      int scopeUnitCount) {
        return switch (relationType) {
            case "SUPERVISE" -> "“" + sourceUnitName + "”已对“" + targetUnitName + "”形成主管关系，当前覆盖 " + scopeUnitCount + " 个组织节点。";
            case "COOPERATE" -> "“" + sourceUnitName + "”与“" + targetUnitName + "”存在协同关系，适合承载跨组织协作入口。";
            case "SERVICE" -> "“" + targetUnitName + "”已作为“" + sourceUnitName + "”的服务归口，适合承接服务请求路由。";
            case "OWNERSHIP" -> "“" + targetUnitName + "”已进入“" + sourceUnitName + "”的归口治理范围，可承接主责配置。";
            case "MATRIX" -> "“" + sourceUnitName + "”与“" + targetUnitName + "”形成矩阵关系，适合配置双线汇报口径。";
            default -> "当前关系已具备消费工作台生成条件。";
        };
    }

    /**
     * 构建路由范围说明。
     *
     * @param targetUnitName 目标组织名称
     * @param scopeUnitCount 节点数
     * @return 范围说明
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String buildRouteScopeSummary(String targetUnitName, int scopeUnitCount) {
        if (scopeUnitCount <= 1) {
            return "当前仅覆盖目标组织“" + targetUnitName + "”本身。";
        }
        return "当前覆盖目标组织“" + targetUnitName + "”及下级共 " + scopeUnitCount + " 个节点。";
    }

    /**
     * 解析负责人名称。
     *
     * @param user   用户实体
     * @param userId 用户ID
     * @return 展示名称
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String resolveLeaderName(RbacUser user, Long userId) {
        if (user == null) {
            return userId == null ? null : "用户#" + userId;
        }
        if (StringUtils.hasText(user.getNickname()) && StringUtils.hasText(user.getUsername())) {
            return user.getNickname() + " (" + user.getUsername() + ")";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    /**
     * 优先级权重。
     *
     * @param priority 优先级
     * @return 权重
     * @author 余炘洋
     * @date 2026-04-12
     */
    private int priorityWeight(String priority) {
        if (PRIORITY_P0.equals(priority)) {
            return 3;
        }
        if (PRIORITY_P1.equals(priority)) {
            return 2;
        }
        return 1;
    }

    /**
     * 就绪状态权重。
     *
     * @param readinessStatus 就绪状态
     * @return 权重
     * @author 余炘洋
     * @date 2026-04-12
     */
    private int readinessWeight(String readinessStatus) {
        if (READINESS_BLOCKED.equals(readinessStatus)) {
            return 3;
        }
        if (READINESS_PENDING.equals(readinessStatus)) {
            return 2;
        }
        return 1;
    }

    /**
     * 规范化 JSON 文本。
     *
     * @param value 原始值
     * @return 规范结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalizeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            objectMapper.readTree(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "扩展属性必须是合法的 JSON");
        }
    }

    /**
     * 将标签列表写入文本。
     *
     * @param tags 标签列表
     * @return 标签文本
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String writeTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 解析标签文本。
     *
     * @param tagText 标签文本
     * @return 标签列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> parseTags(String tagText) {
        if (!StringUtils.hasText(tagText)) {
            return List.of();
        }
        return java.util.Arrays.stream(tagText.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 校验负责人是否属于当前租户。
     *
     * @param tenantId     租户ID
     * @param leaderUserId 负责人用户ID
     * @return 合法负责人ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long validateLeader(String tenantId, Long leaderUserId) {
        if (leaderUserId == null) {
            return null;
        }
        if (leaderUserId <= 0) {
            return null;
        }
        RbacUser leader = userRepository.findById(leaderUserId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "负责人用户不存在"));
        if (!Objects.equals(tenantId, leader.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "负责人不属于当前租户");
        }
        return leaderUserId;
    }

    /**
     * 要求组织节点ID不能为空。
     *
     * @param orgUnitId    节点ID
     * @param errorMessage 错误信息
     * @return 节点ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long requireOrgUnitId(Long orgUnitId, String errorMessage) {
        if (orgUnitId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return orgUnitId;
    }

    /**
     * 校验组织关系两端是否合法。
     *
     * @param tenantId         租户ID
     * @param sourceOrgUnitId  源节点ID
     * @param targetOrgUnitId  目标节点ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void validateRelationEndpoints(String tenantId, Long sourceOrgUnitId, Long targetOrgUnitId) {
        if (Objects.equals(sourceOrgUnitId, targetOrgUnitId)) {
            throw new BizException(ErrorCode.CONFLICT, "组织关系的源节点和目标节点不能相同");
        }
        orgUnitRepository.findByIdAndTenantId(sourceOrgUnitId, tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "源组织节点不存在"));
        orgUnitRepository.findByIdAndTenantId(targetOrgUnitId, tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_UNIT_NOT_FOUND, "目标组织节点不存在"));
    }

    /**
     * 解析排序值。
     *
     * @param sort 原始排序值
     * @return 合法排序值
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer resolveSort(Integer sort) {
        return sort == null ? 0 : sort;
    }

    /**
     * 解析状态。
     *
     * @param status 原始状态
     * @return 合法状态
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer resolveStatus(Integer status) {
        return status == null ? ACTIVE_STATUS : status;
    }

    /**
     * 规范化可选名称。
     *
     * @param value 原始值
     * @return 规范结果
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String normalizeOptionalName(String value) {
        return StringUtils.hasText(value) ? normalizeName(value, "根组织名称不能为空") : null;
    }

    /**
     * 解析模板父节点ID。
     *
     * @param parentCode 父节点编码
     * @param unitMapping 已创建节点
     * @return 父节点ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long resolveTemplateParentId(String parentCode, Map<String, OrgUnit> unitMapping) {
        if (!StringUtils.hasText(parentCode)) {
            return null;
        }
        OrgUnit parent = unitMapping.get(parentCode.trim().toUpperCase(Locale.ROOT));
        if (parent == null || parent.getId() == null) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "组织模板父节点不存在: " + parentCode);
        }
        return parent.getId();
    }

    /**
     * 解析模板根节点默认名称。
     *
     * @param template 模板定义
     * @return 默认根节点名称
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String resolveDefaultRootUnitName(OrgTemplate template) {
        if (template == null || template.units() == null) {
            return null;
        }
        return template.units().stream()
                .filter(this::isRootTemplateUnit)
                .map(TemplateUnit::unitName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析模板节点名称。
     *
     * @param templateUnit         模板节点
     * @param rootUnitNameOverride 根节点名称覆盖
     * @return 生效节点名称
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String resolveTemplateUnitName(TemplateUnit templateUnit, String rootUnitNameOverride) {
        if (templateUnit == null) {
            return null;
        }
        if (StringUtils.hasText(rootUnitNameOverride) && isRootTemplateUnit(templateUnit)) {
            return rootUnitNameOverride;
        }
        return templateUnit.unitName();
    }

    /**
     * 判断是否模板根节点。
     *
     * @param templateUnit 模板节点
     * @return 是否根节点
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean isRootTemplateUnit(TemplateUnit templateUnit) {
        return templateUnit != null && !StringUtils.hasText(templateUnit.parentCode());
    }

    /**
     * 查询模板定义。
     *
     * @param templateCode 模板编码
     * @return 模板定义
     * @author 余炘洋
     * @date 2026-04-11
     */
    private OrgTemplate findTemplate(String templateCode) {
        return buildTemplates().stream()
                .filter(template -> template.templateCode().equals(templateCode))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "组织模板不存在"));
    }

    /**
     * 构建全部模板定义。
     *
     * @return 模板列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<OrgTemplate> buildTemplates() {
        return List.of(
                buildGovernmentTemplate(),
                buildSchoolTemplate(),
                buildEnterpriseTemplate(),
                buildChainStoreTemplate(),
                buildSoleProprietorTemplate()
        );
    }

    private OrgTemplate buildGovernmentTemplate() {
        return new OrgTemplate(
                TEMPLATE_GOVERNMENT,
                "政府标准模板",
                "政府 / 事业单位",
                "适用于行政机关、事业单位、政务服务中心的条块组织初始化。",
                List.of(
                        new TemplateType("GOV_ORG", "行政机构", 10, "适用于厅局、中心、专班等一级机构"),
                        new TemplateType("GOV_DEPT", "职能部门", 20, "适用于办公室、综合处、业务处室"),
                        new TemplateType("GOV_SERVICE", "服务窗口", 30, "适用于政务大厅、窗口、服务站")
                ),
                List.of(
                        new TemplateUnit("GOV_ROOT", "政务中心", "GOV_ORG", null, 10, "默认根节点", List.of("条线管理"), "{\"level\":\"root\"}"),
                        new TemplateUnit("GOV_GENERAL_OFFICE", "综合办公室", "GOV_DEPT", "GOV_ROOT", 20, "负责综合协调", List.of("综合"), "{\"function\":\"office\"}"),
                        new TemplateUnit("GOV_FINANCE_DEPT", "财政业务处", "GOV_DEPT", "GOV_ROOT", 30, "负责财政条线", List.of("业务"), "{\"function\":\"finance\"}"),
                        new TemplateUnit("GOV_SERVICE_HALL", "政务服务大厅", "GOV_SERVICE", "GOV_ROOT", 40, "对外服务窗口", List.of("窗口"), "{\"serviceType\":\"public\"}")
                ),
                List.of(
                        new TemplateRelation("GOV_ROOT", "GOV_SERVICE_HALL", "OWNERSHIP", "归口服务", 10, "中心统一管理服务大厅"),
                        new TemplateRelation("GOV_GENERAL_OFFICE", "GOV_FINANCE_DEPT", "SUPERVISE", "条线督办", 20, "办公室协调重点事项")
                )
        );
    }

    private OrgTemplate buildSchoolTemplate() {
        return new OrgTemplate(
                TEMPLATE_SCHOOL,
                "学校标准模板",
                "学校 / 教育机构",
                "适用于学校、培训机构、校区制教育组织的初始化。",
                List.of(
                        new TemplateType("SCHOOL_ORG", "学校主体", 10, "适用于学校、学院、教育集团"),
                        new TemplateType("SCHOOL_CAMPUS", "校区", 20, "适用于主校区、分校区"),
                        new TemplateType("SCHOOL_DEPT", "教学部门", 30, "适用于教务处、院系"),
                        new TemplateType("SCHOOL_CLASS", "班级单元", 40, "适用于年级、班级、教研班")
                ),
                List.of(
                        new TemplateUnit("SCHOOL_ROOT", "第一学校", "SCHOOL_ORG", null, 10, "默认学校主体", List.of("学校"), "{\"schoolType\":\"k12\"}"),
                        new TemplateUnit("SCHOOL_MAIN_CAMPUS", "主校区", "SCHOOL_CAMPUS", "SCHOOL_ROOT", 20, "主教学区", List.of("校区"), "{\"campusType\":\"main\"}"),
                        new TemplateUnit("SCHOOL_ACADEMIC", "教务处", "SCHOOL_DEPT", "SCHOOL_ROOT", 30, "负责教学管理", List.of("教学"), "{\"function\":\"academic\"}"),
                        new TemplateUnit("SCHOOL_GRADE_01", "一年级", "SCHOOL_CLASS", "SCHOOL_MAIN_CAMPUS", 40, "默认年级节点", List.of("年级"), "{\"grade\":\"1\"}"),
                        new TemplateUnit("SCHOOL_CLASS_0101", "一年级一班", "SCHOOL_CLASS", "SCHOOL_GRADE_01", 50, "默认班级节点", List.of("班级"), "{\"classNo\":\"01\"}")
                ),
                List.of(
                        new TemplateRelation("SCHOOL_ROOT", "SCHOOL_MAIN_CAMPUS", "OWNERSHIP", "校本管理", 10, "学校主体归口管理校区"),
                        new TemplateRelation("SCHOOL_ACADEMIC", "SCHOOL_GRADE_01", "SERVICE", "教学服务", 20, "教务处服务年级管理")
                )
        );
    }

    private OrgTemplate buildEnterpriseTemplate() {
        return new OrgTemplate(
                TEMPLATE_ENTERPRISE,
                "集团企业模板",
                "大公司 / 小公司 / 集团",
                "适用于集团总部、子公司、部门、团队等企业组织初始化。",
                List.of(
                        new TemplateType("ENTERPRISE_GROUP", "集团主体", 10, "适用于集团、公司、法人主体"),
                        new TemplateType("ENTERPRISE_COMPANY", "下属公司", 20, "适用于分子公司、事业部"),
                        new TemplateType("ENTERPRISE_DEPT", "部门", 30, "适用于职能部门、业务部门"),
                        new TemplateType("ENTERPRISE_TEAM", "团队", 40, "适用于项目组、交付组、销售组")
                ),
                List.of(
                        new TemplateUnit("ENT_GROUP", "集团总部", "ENTERPRISE_GROUP", null, 10, "默认集团根节点", List.of("集团"), "{\"level\":\"group\"}"),
                        new TemplateUnit("ENT_SUBCOMPANY", "区域子公司", "ENTERPRISE_COMPANY", "ENT_GROUP", 20, "默认子公司", List.of("子公司"), "{\"region\":\"default\"}"),
                        new TemplateUnit("ENT_HR", "人力行政部", "ENTERPRISE_DEPT", "ENT_GROUP", 30, "默认职能部门", List.of("职能"), "{\"function\":\"hr\"}"),
                        new TemplateUnit("ENT_SALES", "销售中心", "ENTERPRISE_DEPT", "ENT_SUBCOMPANY", 40, "默认业务部门", List.of("业务"), "{\"function\":\"sales\"}"),
                        new TemplateUnit("ENT_TEAM_A", "销售一组", "ENTERPRISE_TEAM", "ENT_SALES", 50, "默认执行团队", List.of("团队"), "{\"team\":\"A\"}")
                ),
                List.of(
                        new TemplateRelation("ENT_GROUP", "ENT_SUBCOMPANY", "OWNERSHIP", "集团归口", 10, "集团总部归口管理子公司"),
                        new TemplateRelation("ENT_HR", "ENT_SUBCOMPANY", "SERVICE", "职能支持", 20, "人力行政支持子公司运营")
                )
        );
    }

    private OrgTemplate buildChainStoreTemplate() {
        return new OrgTemplate(
                TEMPLATE_CHAIN,
                "连锁门店模板",
                "连锁 / 零售 / 餐饮",
                "适用于总部、区域、门店、班组等连锁经营组织初始化。",
                List.of(
                        new TemplateType("CHAIN_HQ", "总部", 10, "适用于品牌总部、总公司"),
                        new TemplateType("CHAIN_REGION", "区域", 20, "适用于大区、城市公司"),
                        new TemplateType("CHAIN_STORE", "门店", 30, "适用于直营店、加盟店"),
                        new TemplateType("CHAIN_SHIFT", "班组", 40, "适用于早晚班、档口班组")
                ),
                List.of(
                        new TemplateUnit("CHAIN_HQ_ROOT", "品牌总部", "CHAIN_HQ", null, 10, "默认总部节点", List.of("总部"), "{\"channel\":\"offline\"}"),
                        new TemplateUnit("CHAIN_REGION_EAST", "华东大区", "CHAIN_REGION", "CHAIN_HQ_ROOT", 20, "默认区域节点", List.of("区域"), "{\"region\":\"east\"}"),
                        new TemplateUnit("CHAIN_STORE_001", "示范门店", "CHAIN_STORE", "CHAIN_REGION_EAST", 30, "默认门店节点", List.of("直营"), "{\"storeType\":\"direct\"}"),
                        new TemplateUnit("CHAIN_SHIFT_A", "早班组", "CHAIN_SHIFT", "CHAIN_STORE_001", 40, "默认门店班组", List.of("班组"), "{\"shift\":\"morning\"}")
                ),
                List.of(
                        new TemplateRelation("CHAIN_HQ_ROOT", "CHAIN_REGION_EAST", "OWNERSHIP", "总部归口", 10, "总部统一归口区域管理"),
                        new TemplateRelation("CHAIN_REGION_EAST", "CHAIN_STORE_001", "OWNERSHIP", "区域归口", 20, "区域负责门店经营管理")
                )
        );
    }

    private OrgTemplate buildSoleProprietorTemplate() {
        return new OrgTemplate(
                TEMPLATE_SOHO,
                "个体工商模板",
                "个体工商 / 小微经营体",
                "适用于个体经营、小微门店、工作室等轻量组织初始化。",
                List.of(
                        new TemplateType("SOLE_OWNER", "经营主体", 10, "适用于老板、法人、工作室主体"),
                        new TemplateType("SOLE_SITE", "经营场所", 20, "适用于门店、档口、工作室"),
                        new TemplateType("SOLE_ROLE", "经营岗位", 30, "适用于收银、后厨、运营协助")
                ),
                List.of(
                        new TemplateUnit("SOLE_ROOT", "经营主体", "SOLE_OWNER", null, 10, "默认主体节点", List.of("老板"), "{\"entityType\":\"individual\"}"),
                        new TemplateUnit("SOLE_SITE_MAIN", "主营门店", "SOLE_SITE", "SOLE_ROOT", 20, "默认经营场所", List.of("门店"), "{\"siteType\":\"main\"}"),
                        new TemplateUnit("SOLE_ROLE_CASHIER", "收银岗位", "SOLE_ROLE", "SOLE_SITE_MAIN", 30, "默认经营岗位", List.of("岗位"), "{\"roleType\":\"cashier\"}")
                ),
                List.of(
                        new TemplateRelation("SOLE_ROOT", "SOLE_SITE_MAIN", "OWNERSHIP", "主体归口", 10, "经营主体管理主营门店")
                )
        );
    }

    /**
     * 模板定义。
     *
     * @param templateCode 模板编码
     * @param templateName 模板名称
     * @param industry 行业
     * @param description 描述
     * @param types 类型集合
     * @param units 节点集合
     * @param relations 关系集合
     * @author 余炘洋
     * @date 2026-04-11
     */
    private record OrgTemplate(String templateCode,
                               String templateName,
                               String industry,
                               String description,
                               List<TemplateType> types,
                               List<TemplateUnit> units,
                               List<TemplateRelation> relations) {
    }

    /**
     * 模板类型定义。
     *
     * @param typeCode 类型编码
     * @param typeName 类型名称
     * @param sort 排序
     * @param remark 备注
     * @author 余炘洋
     * @date 2026-04-11
     */
    private record TemplateType(String typeCode, String typeName, int sort, String remark) {
    }

    /**
     * 模板节点定义。
     *
     * @param unitCode 节点编码
     * @param unitName 节点名称
     * @param typeCode 类型编码
     * @param parentCode 父节点编码
     * @param sort 排序
     * @param remark 备注
     * @param tags 标签
     * @param attributeJson 扩展属性
     * @author 余炘洋
     * @date 2026-04-11
     */
    private record TemplateUnit(String unitCode,
                                String unitName,
                                String typeCode,
                                String parentCode,
                                int sort,
                                String remark,
                                List<String> tags,
                                String attributeJson) {
    }

    /**
     * 模板关系定义。
     *
     * @param sourceUnitCode 源节点编码
     * @param targetUnitCode 目标节点编码
     * @param relationType 关系类型
     * @param relationName 关系名称
     * @param sort 排序
     * @param remark 备注
     * @author 余炘洋
     * @date 2026-04-11
     */
    private record TemplateRelation(String sourceUnitCode,
                                    String targetUnitCode,
                                    String relationType,
                                    String relationName,
                                    int sort,
                                    String remark) {
    }
}
