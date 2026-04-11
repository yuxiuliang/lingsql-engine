package com.lingsql.organization.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.OrgUnitType;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.model.OrgUnitCreateRequest;
import com.lingsql.organization.model.OrgUnitRelationCreateRequest;
import com.lingsql.organization.model.OrgUnitRelationUpdateRequest;
import com.lingsql.organization.model.OrgUnitRelationView;
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

    private final OrgUnitTypeRepository orgUnitTypeRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserOrgMembershipRepository userOrgMembershipRepository;
    private final OrgUnitRelationRepository orgUnitRelationRepository;
    private final RbacUserRepository userRepository;

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
        Map<Long, RbacUser> userMapping = userRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(user -> Objects.equals(resolvedTenantId, user.getTenantId()))
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
        return getOrgUnitView(saved.getId(), tenantId);
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
        if (request.getLeaderUserId() != null) {
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
        return getOrgUnitView(entity.getId(), tenantId);
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
        orgUnitRepository.delete(entity);
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
        return listRelations(tenantId).stream()
                .filter(item -> Objects.equals(item.getId(), saved.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_NOT_FOUND, "组织关系不存在"));
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
        return listRelations(tenantId).stream()
                .filter(item -> Objects.equals(item.getId(), entity.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_NOT_FOUND, "组织关系不存在"));
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
        orgUnitRelationRepository.delete(entity);
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
     * 规范化 JSON 文本。
     *
     * @param value 原始值
     * @return 规范结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalizeJson(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
}
