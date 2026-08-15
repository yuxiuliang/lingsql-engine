package com.lingsql.organization.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.OrgUnitType;
import com.lingsql.organization.model.OrgGovernanceCatalogView;
import com.lingsql.organization.model.OrgGovernanceOptionView;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.OrgUnitTypeRepository;
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
import java.util.Set;

/**
 * 组织治理字典服务。
 *
 * <p>为菜单治理、角色管理、用户管理提供统一的租户组织维度候选值，
 * 并对租户级配置做入库前校验，降低误配风险。</p>
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class OrgGovernanceCatalogService {

    private static final Map<String, String> DEFAULT_RELATION_TYPE_NAME_MAPPING = new LinkedHashMap<>();

    static {
        DEFAULT_RELATION_TYPE_NAME_MAPPING.put("SUPERVISE", "主管");
        DEFAULT_RELATION_TYPE_NAME_MAPPING.put("COOPERATE", "协同");
        DEFAULT_RELATION_TYPE_NAME_MAPPING.put("SERVICE", "服务");
        DEFAULT_RELATION_TYPE_NAME_MAPPING.put("OWNERSHIP", "归口");
        DEFAULT_RELATION_TYPE_NAME_MAPPING.put("MATRIX", "矩阵");
    }

    private final OrgUnitTypeRepository orgUnitTypeRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final OrgUnitRelationRepository orgUnitRelationRepository;

    /**
     * 构建租户组织治理字典。
     *
     * @param tenantId 租户ID
     * @return 治理字典
     * @author 余炘洋
     * @date 2026-04-13
     */
    public OrgGovernanceCatalogView getCatalog(String tenantId) {
        OrgGovernanceCatalogView view = new OrgGovernanceCatalogView();
        String normalizedTenantId = trimToNull(tenantId);
        view.setTenantId(normalizedTenantId);
        if (!StringUtils.hasText(normalizedTenantId)) {
            return view;
        }

        List<OrgUnitType> types = orgUnitTypeRepository.findByTenantIdOrderBySortAscIdAsc(normalizedTenantId);
        List<OrgUnit> units = new ArrayList<>(orgUnitRepository.findByTenantId(normalizedTenantId));
        units.sort(Comparator
                .comparingInt((OrgUnit unit) -> unit.getTreeLevel() == null ? Integer.MAX_VALUE : unit.getTreeLevel())
                .thenComparingInt(unit -> unit.getSort() == null ? 0 : unit.getSort())
                .thenComparingLong(unit -> unit.getId() == null ? 0L : unit.getId()));

        Map<String, String> typeNameMapping = new LinkedHashMap<>();
        List<OrgGovernanceOptionView> typeOptions = new ArrayList<>();
        for (OrgUnitType type : types) {
            if (!StringUtils.hasText(type.getTypeCode())) {
                continue;
            }
            String code = normalizeValue(type.getTypeCode());
            typeNameMapping.put(code, trimToNull(type.getTypeName()));
            typeOptions.add(createOption(
                    code,
                    trimToNull(type.getTypeName()),
                    resolveStatusText(type.getStatus())
            ));
        }

        List<OrgGovernanceOptionView> unitOptions = new ArrayList<>();
        LinkedHashSet<String> tagCodes = new LinkedHashSet<>();
        for (OrgUnit unit : units) {
            if (!StringUtils.hasText(unit.getUnitCode())) {
                continue;
            }
            String typeCode = normalizeValue(unit.getTypeCode());
            String typeName = typeNameMapping.get(typeCode);
            unitOptions.add(createOption(
                    normalizeValue(unit.getUnitCode()),
                    trimToNull(unit.getUnitName()),
                    StringUtils.hasText(typeName) ? typeName : typeCode
            ));
            tagCodes.addAll(parseNormalizedValues(unit.getTagText()));
        }

        List<OrgGovernanceOptionView> tagOptions = new ArrayList<>();
        for (String tagCode : tagCodes) {
            tagOptions.add(createOption(tagCode, tagCode, "来自当前租户组织标签"));
        }

        view.setOrgTypeOptions(typeOptions);
        view.setOrgUnitOptions(unitOptions);
        view.setOrgTagOptions(tagOptions);
        view.setRelationTypeOptions(resolveRelationTypeOptions(normalizedTenantId));
        return view;
    }

    /**
     * 校验租户级菜单/预览使用的组织维度。
     *
     * @param tenantId          租户ID
     * @param orgTypeCodes      组织类型编码
     * @param orgUnitCodes      组织编码
     * @param orgTags           组织标签
     * @param relationTypeCodes 关系类型编码
     * @author 余炘洋
     * @date 2026-04-13
     */
    public void validateTenantSelections(String tenantId,
                                         List<String> orgTypeCodes,
                                         List<String> orgUnitCodes,
                                         List<String> orgTags,
                                         List<String> relationTypeCodes) {
        String normalizedTenantId = trimToNull(tenantId);
        if (!StringUtils.hasText(normalizedTenantId)) {
            return;
        }
        OrgGovernanceCatalogView catalog = getCatalog(normalizedTenantId);
        validateOptionCodes(
                catalog.getOrgTypeOptions().stream().map(OrgGovernanceOptionView::getCode).toList(),
                orgTypeCodes,
                "组织类型编码"
        );
        validateOptionCodes(
                catalog.getOrgUnitOptions().stream().map(OrgGovernanceOptionView::getCode).toList(),
                orgUnitCodes,
                "组织编码"
        );
        validateOptionCodes(
                catalog.getOrgTagOptions().stream().map(OrgGovernanceOptionView::getCode).toList(),
                orgTags,
                "组织标签"
        );
        validateRelationTypes(normalizedTenantId, relationTypeCodes, "关系类型");
    }

    /**
     * 校验关系类型列表是否合法。
     *
     * @param tenantId      租户ID
     * @param relationTypes 关系类型列表
     * @param label         字段名称
     * @author 余炘洋
     * @date 2026-04-13
     */
    public void validateRelationTypes(String tenantId, List<String> relationTypes, String label) {
        List<String> normalizedRelationTypes = normalizeValues(relationTypes);
        if (normalizedRelationTypes.isEmpty()) {
            return;
        }
        Set<String> allowedRelationTypes = resolveRelationTypeCodeSet(tenantId);
        List<String> invalidCodes = normalizedRelationTypes.stream()
                .filter(code -> !allowedRelationTypes.contains(code))
                .toList();
        if (!invalidCodes.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    (StringUtils.hasText(label) ? label : "关系类型")
                            + "存在未定义项: " + String.join("、", invalidCodes));
        }
    }

    private void validateOptionCodes(List<String> allowedValues, List<String> actualValues, String label) {
        List<String> normalizedValues = normalizeValues(actualValues);
        if (normalizedValues.isEmpty()) {
            return;
        }
        Set<String> allowedValueSet = new LinkedHashSet<>(normalizeValues(allowedValues));
        List<String> invalidValues = normalizedValues.stream()
                .filter(value -> !allowedValueSet.contains(value))
                .toList();
        if (!invalidValues.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    (StringUtils.hasText(label) ? label : "组织维度")
                            + "存在未定义项: " + String.join("、", invalidValues));
        }
    }

    private List<OrgGovernanceOptionView> resolveRelationTypeOptions(String tenantId) {
        LinkedHashMap<String, String> relationTypeNameMapping = new LinkedHashMap<>(DEFAULT_RELATION_TYPE_NAME_MAPPING);
        for (String relationTypeCode : resolveObservedRelationTypeCodes(tenantId)) {
            relationTypeNameMapping.putIfAbsent(relationTypeCode, relationTypeCode);
        }
        List<OrgGovernanceOptionView> options = new ArrayList<>();
        for (Map.Entry<String, String> entry : relationTypeNameMapping.entrySet()) {
            options.add(createOption(
                    entry.getKey(),
                    entry.getValue(),
                    DEFAULT_RELATION_TYPE_NAME_MAPPING.containsKey(entry.getKey()) ? "标准关系语义" : "租户已配置关系类型"
            ));
        }
        return options;
    }

    private Set<String> resolveRelationTypeCodeSet(String tenantId) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(DEFAULT_RELATION_TYPE_NAME_MAPPING.keySet());
        codes.addAll(resolveObservedRelationTypeCodes(tenantId));
        return codes;
    }

    private Set<String> resolveObservedRelationTypeCodes(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return Set.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (OrgUnitRelation relation : orgUnitRelationRepository.findByTenantId(tenantId.trim())) {
            String relationType = normalizeValue(relation == null ? null : relation.getRelationType());
            if (StringUtils.hasText(relationType)) {
                codes.add(relationType);
            }
        }
        return codes;
    }

    private OrgGovernanceOptionView createOption(String code, String name, String description) {
        OrgGovernanceOptionView option = new OrgGovernanceOptionView();
        option.setCode(code);
        option.setName(StringUtils.hasText(name) ? name.trim() : code);
        option.setDescription(trimToNull(description));
        return option;
    }

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

    private List<String> parseNormalizedValues(String rawValue) {
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

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveStatusText(Integer status) {
        return status != null && status == 0 ? "已停用" : "已启用";
    }
}
