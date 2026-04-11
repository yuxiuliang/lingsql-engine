package com.lingsql.organization.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitType;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.model.OrgUnitView;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitTypeRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class OrganizationAdminServiceTest {

    @Test
    void listOrgUnitsShouldBuildTreeAndMemberCount() {
        OrgUnitTypeRepository typeRepository = Mockito.mock(OrgUnitTypeRepository.class);
        OrgUnitRepository unitRepository = Mockito.mock(OrgUnitRepository.class);
        UserOrgMembershipRepository membershipRepository = Mockito.mock(UserOrgMembershipRepository.class);
        OrgUnitRelationRepository relationRepository = Mockito.mock(OrgUnitRelationRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrganizationAdminService service = new OrganizationAdminService(
                typeRepository,
                unitRepository,
                membershipRepository,
                relationRepository,
                userRepository
        );

        OrgUnitType government = new OrgUnitType();
        government.setId(1L);
        government.setTenantId("tenant-a");
        government.setTypeCode("GOV");
        government.setTypeName("行政层级");

        OrgUnit root = new OrgUnit();
        root.setId(10L);
        root.setTenantId("tenant-a");
        root.setUnitCode("HN");
        root.setUnitName("湖南省");
        root.setTypeCode("GOV");
        root.setTreeLevel(1);
        root.setSort(10);

        OrgUnit child = new OrgUnit();
        child.setId(11L);
        child.setTenantId("tenant-a");
        child.setUnitCode("CS");
        child.setUnitName("长沙市");
        child.setTypeCode("GOV");
        child.setParentId(10L);
        child.setTreeLevel(2);
        child.setSort(20);

        UserOrgMembership membership = new UserOrgMembership();
        membership.setTenantId("tenant-a");
        membership.setUserId(100L);
        membership.setOrgUnitId(11L);

        when(typeRepository.findByTenantIdOrderBySortAscIdAsc("tenant-a")).thenReturn(List.of(government));
        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of(root, child));
        when(membershipRepository.findByTenantId("tenant-a")).thenReturn(List.of(membership));

        List<OrgUnitView> tree = service.listOrgUnits("tenant-a");

        assertEquals(1, tree.size());
        assertEquals("湖南省", tree.get(0).getUnitName());
        assertEquals(0L, tree.get(0).getMemberCount());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("长沙市", tree.get(0).getChildren().get(0).getUnitName());
        assertEquals(1L, tree.get(0).getChildren().get(0).getMemberCount());
    }

    @Test
    void deleteUnitTypeShouldRejectWhenTypeStillInUse() {
        OrgUnitTypeRepository typeRepository = Mockito.mock(OrgUnitTypeRepository.class);
        OrgUnitRepository unitRepository = Mockito.mock(OrgUnitRepository.class);
        UserOrgMembershipRepository membershipRepository = Mockito.mock(UserOrgMembershipRepository.class);
        OrgUnitRelationRepository relationRepository = Mockito.mock(OrgUnitRelationRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrganizationAdminService service = new OrganizationAdminService(
                typeRepository,
                unitRepository,
                membershipRepository,
                relationRepository,
                userRepository
        );

        OrgUnitType type = new OrgUnitType();
        type.setId(1L);
        type.setTenantId("tenant-a");
        type.setTypeCode("SCHOOL");
        type.setTypeName("学校组织");

        when(typeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(unitRepository.existsByTenantIdAndTypeCode("tenant-a", "SCHOOL")).thenReturn(true);

        assertThrows(BizException.class, () -> service.deleteUnitType(1L, "tenant-a", false));
    }
}
