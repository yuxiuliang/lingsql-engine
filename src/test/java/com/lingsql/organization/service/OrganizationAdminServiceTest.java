package com.lingsql.organization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitType;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.model.OrgRelationWorkbenchView;
import com.lingsql.organization.model.OrgRelationRouteResolveView;
import com.lingsql.organization.model.OrgTemplateApplyRequest;
import com.lingsql.organization.model.OrgTemplateView;
import com.lingsql.organization.model.OrgUnitCreateRequest;
import com.lingsql.organization.model.OrgUnitUpdateRequest;
import com.lingsql.organization.model.OrgUnitView;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitTypeRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.repository.RbacUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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
                userRepository,
                new ObjectMapper()
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
        when(userRepository.findByTenantIdOrderByIdAsc("tenant-a")).thenReturn(List.of());

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
                userRepository,
                new ObjectMapper()
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

    @Test
    void createOrgUnitShouldRejectInvalidAttributeJson() {
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
                userRepository,
                new ObjectMapper()
        );

        OrgUnitType type = new OrgUnitType();
        type.setTenantId("tenant-a");
        type.setTypeCode("DEPARTMENT");
        type.setTypeName("部门");

        OrgUnitCreateRequest request = new OrgUnitCreateRequest();
        request.setUnitCode("DEV");
        request.setUnitName("研发部");
        request.setTypeCode("DEPARTMENT");
        request.setAttributeJson("{bad json");

        when(unitRepository.existsByTenantIdAndUnitCode("tenant-a", "DEV")).thenReturn(false);
        when(typeRepository.findByTenantIdAndTypeCode("tenant-a", "DEPARTMENT")).thenReturn(Optional.of(type));

        assertThrows(BizException.class, () -> service.createOrgUnit(request, "tenant-a", false));
        Mockito.verify(unitRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void updateOrgUnitShouldAllowClearingLeader() {
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
                userRepository,
                new ObjectMapper()
        );

        OrgUnitType type = new OrgUnitType();
        type.setTenantId("tenant-a");
        type.setTypeCode("DEPARTMENT");
        type.setTypeName("部门");

        OrgUnit unit = new OrgUnit();
        unit.setId(1L);
        unit.setTenantId("tenant-a");
        unit.setUnitCode("DEV");
        unit.setUnitName("研发部");
        unit.setTypeCode("DEPARTMENT");
        unit.setLeaderUserId(88L);
        unit.setTreeLevel(1);

        RbacUser leader = new RbacUser();
        leader.setId(88L);
        leader.setTenantId("tenant-a");
        leader.setUsername("leader");
        leader.setNickname("负责人");

        OrgUnitUpdateRequest request = new OrgUnitUpdateRequest();
        request.setLeaderUserId(null);

        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.save(Mockito.any(OrgUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of(unit));
        when(typeRepository.findByTenantIdOrderBySortAscIdAsc("tenant-a")).thenReturn(List.of(type));
        when(membershipRepository.findByTenantId("tenant-a")).thenReturn(List.of());
        when(userRepository.findByTenantIdOrderByIdAsc("tenant-a")).thenReturn(List.of(leader));

        OrgUnitView view = service.updateOrgUnit(1L, request, "tenant-a", false);

        assertEquals(null, view.getLeaderUserId());
    }

    @Test
    void listTemplatesShouldExposeFiveBuiltInTemplates() {
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
                userRepository,
                new ObjectMapper()
        );

        List<OrgTemplateView> templates = service.listTemplates();

        assertEquals(5, templates.size());
        assertEquals("GOVERNMENT_STANDARD", templates.getFirst().getTemplateCode());
    }

    @Test
    void applyTemplateShouldRejectWhenTenantAlreadyHasOrgData() {
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
                userRepository,
                new ObjectMapper()
        );

        OrgUnitType existing = new OrgUnitType();
        existing.setId(1L);
        existing.setTenantId("tenant-a");
        existing.setTypeCode("EXISTING");
        when(typeRepository.findByTenantIdOrderBySortAscIdAsc("tenant-a")).thenReturn(List.of(existing));

        OrgTemplateApplyRequest request = new OrgTemplateApplyRequest();
        request.setTemplateCode("GOVERNMENT_STANDARD");

        assertThrows(BizException.class, () -> service.applyTemplate(request, "tenant-a", false));
        Mockito.verify(typeRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void relationWorkbenchShouldBuildActionableTasks() {
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
                userRepository,
                new ObjectMapper()
        );

        OrgUnit source = new OrgUnit();
        source.setId(10L);
        source.setTenantId("tenant-a");
        source.setUnitCode("HQ");
        source.setUnitName("总部");
        source.setLeaderUserId(100L);
        source.setTreePath("/10");

        OrgUnit target = new OrgUnit();
        target.setId(11L);
        target.setTenantId("tenant-a");
        target.setUnitCode("SERVICE_CENTER");
        target.setUnitName("共享服务中心");
        target.setLeaderUserId(200L);
        target.setTreePath("/11");

        OrgUnit targetChild = new OrgUnit();
        targetChild.setId(12L);
        targetChild.setTenantId("tenant-a");
        targetChild.setUnitCode("SERVICE_TEAM");
        targetChild.setUnitName("服务一组");
        targetChild.setTreePath("/11/12");

        OrgUnitRelation relation = new OrgUnitRelation();
        relation.setId(9L);
        relation.setTenantId("tenant-a");
        relation.setSourceOrgUnitId(10L);
        relation.setTargetOrgUnitId(11L);
        relation.setRelationType("SERVICE");
        relation.setRelationName("售后服务归口");
        relation.setStatus(1);

        RbacUser targetLeader = new RbacUser();
        targetLeader.setId(200L);
        targetLeader.setTenantId("tenant-a");
        targetLeader.setUsername("service.owner");
        targetLeader.setNickname("服务负责人");

        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of(source, target, targetChild));
        when(relationRepository.findByTenantId("tenant-a")).thenReturn(List.of(relation));
        when(userRepository.findByTenantIdOrderByIdAsc("tenant-a")).thenReturn(List.of(targetLeader));

        OrgRelationWorkbenchView workbench = service.relationWorkbench("tenant-a");

        assertEquals(1, workbench.getTotalTasks());
        assertEquals("SERVICE_DESK", workbench.getTasks().getFirst().getSceneCode());
        assertEquals("READY", workbench.getTasks().getFirst().getReadinessStatus());
        assertEquals("服务负责人 (service.owner)", workbench.getTasks().getFirst().getTargetLeaderName());
        assertEquals(2, workbench.getTasks().getFirst().getScopeUnitCount());
    }

    @Test
    void resolveRelationRouteShouldReturnPrimaryAndAmbiguousCandidates() {
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
                userRepository,
                new ObjectMapper()
        );

        OrgUnit source = new OrgUnit();
        source.setId(10L);
        source.setTenantId("tenant-a");
        source.setUnitCode("SRC");
        source.setUnitName("源组织");
        source.setTreePath("/10");

        OrgUnit targetA = new OrgUnit();
        targetA.setId(20L);
        targetA.setTenantId("tenant-a");
        targetA.setUnitCode("TARGET_A");
        targetA.setUnitName("协同A");
        targetA.setLeaderUserId(201L);
        targetA.setTreePath("/20");

        OrgUnit targetAChild = new OrgUnit();
        targetAChild.setId(21L);
        targetAChild.setTenantId("tenant-a");
        targetAChild.setUnitCode("TARGET_A_CHILD");
        targetAChild.setUnitName("协同A一组");
        targetAChild.setTreePath("/20/21");

        OrgUnit targetB = new OrgUnit();
        targetB.setId(30L);
        targetB.setTenantId("tenant-a");
        targetB.setUnitCode("TARGET_B");
        targetB.setUnitName("协同B");
        targetB.setTreePath("/30");

        OrgUnitRelation relationA = new OrgUnitRelation();
        relationA.setId(1L);
        relationA.setTenantId("tenant-a");
        relationA.setSourceOrgUnitId(10L);
        relationA.setTargetOrgUnitId(20L);
        relationA.setRelationType("COOPERATE");
        relationA.setRelationName("协同A");
        relationA.setSort(20);
        relationA.setStatus(1);

        OrgUnitRelation relationB = new OrgUnitRelation();
        relationB.setId(2L);
        relationB.setTenantId("tenant-a");
        relationB.setSourceOrgUnitId(10L);
        relationB.setTargetOrgUnitId(30L);
        relationB.setRelationType("COOPERATE");
        relationB.setRelationName("协同B");
        relationB.setSort(10);
        relationB.setStatus(1);

        RbacUser leaderA = new RbacUser();
        leaderA.setId(201L);
        leaderA.setTenantId("tenant-a");
        leaderA.setUsername("leader.a");
        leaderA.setNickname("负责人A");

        when(unitRepository.findByIdAndTenantId(10L, "tenant-a")).thenReturn(Optional.of(source));
        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of(source, targetA, targetAChild, targetB));
        when(relationRepository.findByTenantIdAndStatusAndRelationTypeAndSourceOrgUnitIdIn(
                "tenant-a", 1, "COOPERATE", List.of(10L)
        )).thenReturn(List.of(relationA, relationB));
        when(userRepository.findByTenantIdOrderByIdAsc("tenant-a")).thenReturn(List.of(leaderA));

        OrgRelationRouteResolveView view = service.resolveRelationRoute("tenant-a", 10L, "COOPERATE");

        assertEquals(true, view.isMatched());
        assertEquals(true, view.isAmbiguous());
        assertEquals(2, view.getCandidateCount());
        assertEquals(2L, view.getPrimaryRelationId());
        assertEquals("TARGET_B", view.getPrimaryTargetOrgUnitCode());
        assertEquals(1, view.getPrimaryScopeUnitCount());
        assertEquals(2, view.getCandidates().size());
        assertEquals(true, view.getWarnings().contains("当前存在多个候选目标，系统已按排序值选择主路由。"));
        assertEquals(true, view.getWarnings().contains("主路由目标组织未配置负责人，暂不适合直接自动派单。"));
    }

    @Test
    void applyTemplateShouldCreateGovernmentTemplateWhenTenantEmpty() {
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
                userRepository,
                new ObjectMapper()
        );

        when(typeRepository.findByTenantIdOrderBySortAscIdAsc("tenant-a")).thenReturn(List.of());
        AtomicLong typeId = new AtomicLong(10L);
        when(typeRepository.save(Mockito.any(OrgUnitType.class))).thenAnswer(invocation -> {
            OrgUnitType entity = invocation.getArgument(0);
            entity.setId(typeId.getAndIncrement());
            return entity;
        });

        AtomicLong unitId = new AtomicLong(100L);
        java.util.List<OrgUnit> savedUnits = new java.util.ArrayList<>();
        when(unitRepository.findByTenantId("tenant-a")).thenReturn(List.of(), savedUnits);
        when(unitRepository.save(Mockito.any(OrgUnit.class))).thenAnswer(invocation -> {
            OrgUnit entity = invocation.getArgument(0);
            entity.setId(unitId.getAndIncrement());
            savedUnits.add(entity);
            return entity;
        });
        when(unitRepository.saveAll(Mockito.anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(relationRepository.findByTenantId("tenant-a")).thenReturn(List.of());
        when(relationRepository.save(Mockito.any(OrgUnitRelation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrgTemplateApplyRequest request = new OrgTemplateApplyRequest();
        request.setTemplateCode("GOVERNMENT_STANDARD");
        request.setRootUnitName("湖南省政务服务和大数据局");

        String message = service.applyTemplate(request, "tenant-a", false);

        assertEquals(true, message.contains("政府标准模板"));
        assertEquals("湖南省政务服务和大数据局", savedUnits.getFirst().getUnitName());
        Mockito.verify(typeRepository, Mockito.times(3)).save(Mockito.any(OrgUnitType.class));
        Mockito.verify(unitRepository, Mockito.times(4)).save(Mockito.any(OrgUnit.class));
        Mockito.verify(relationRepository, Mockito.times(2)).save(Mockito.any(OrgUnitRelation.class));
        Mockito.verify(unitRepository).saveAll(savedUnits);
    }
}
