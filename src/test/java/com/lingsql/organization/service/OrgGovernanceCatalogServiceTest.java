package com.lingsql.organization.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.OrgUnitType;
import com.lingsql.organization.model.OrgGovernanceCatalogView;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.OrgUnitTypeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class OrgGovernanceCatalogServiceTest {

    @Test
    void getCatalogShouldAggregateTypeUnitTagAndRelationOptions() {
        OrgUnitTypeRepository typeRepository = Mockito.mock(OrgUnitTypeRepository.class);
        OrgUnitRepository unitRepository = Mockito.mock(OrgUnitRepository.class);
        OrgUnitRelationRepository relationRepository = Mockito.mock(OrgUnitRelationRepository.class);
        OrgGovernanceCatalogService service = new OrgGovernanceCatalogService(typeRepository, unitRepository, relationRepository);

        OrgUnitType type = new OrgUnitType();
        type.setTenantId("tenant-a");
        type.setTypeCode("CHAIN_STORE");
        type.setTypeName("门店");
        type.setStatus(1);

        OrgUnit unit = new OrgUnit();
        unit.setTenantId("tenant-a");
        unit.setTypeCode("CHAIN_STORE");
        unit.setUnitCode("STORE_001");
        unit.setUnitName("长沙门店");
        unit.setTagText("直营,华中");
        unit.setTreeLevel(1);
        unit.setSort(10);

        OrgUnitRelation relation = new OrgUnitRelation();
        relation.setTenantId("tenant-a");
        relation.setRelationType("service");

        when(typeRepository.findByTenantIdOrderBySortAscIdAsc("tenant-a")).thenReturn(List.of(type));
        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of(unit));
        when(relationRepository.findByTenantId("tenant-a")).thenReturn(List.of(relation));

        OrgGovernanceCatalogView catalog = service.getCatalog("tenant-a");

        assertEquals("tenant-a", catalog.getTenantId());
        assertEquals("CHAIN_STORE", catalog.getOrgTypeOptions().getFirst().getCode());
        assertEquals("STORE_001", catalog.getOrgUnitOptions().getFirst().getCode());
        assertEquals("直营".toUpperCase(), catalog.getOrgTagOptions().getFirst().getCode());
        assertEquals(true, catalog.getRelationTypeOptions().stream().anyMatch(item -> "SERVICE".equals(item.getCode())));
        assertEquals(true, catalog.getRelationTypeOptions().stream().anyMatch(item -> "SUPERVISE".equals(item.getCode())));
    }

    @Test
    void validateTenantSelectionsShouldRejectUnknownOrgType() {
        OrgUnitTypeRepository typeRepository = Mockito.mock(OrgUnitTypeRepository.class);
        OrgUnitRepository unitRepository = Mockito.mock(OrgUnitRepository.class);
        OrgUnitRelationRepository relationRepository = Mockito.mock(OrgUnitRelationRepository.class);
        OrgGovernanceCatalogService service = new OrgGovernanceCatalogService(typeRepository, unitRepository, relationRepository);

        OrgUnitType type = new OrgUnitType();
        type.setTenantId("tenant-a");
        type.setTypeCode("SCHOOL");
        type.setTypeName("学校");

        when(typeRepository.findByTenantIdOrderBySortAscIdAsc("tenant-a")).thenReturn(List.of(type));
        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of());
        when(relationRepository.findByTenantId("tenant-a")).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.validateTenantSelections(
                "tenant-a",
                List.of("UNKNOWN"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
    }
}
