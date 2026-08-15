package com.lingsql.rbac.service;

import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.model.DataScopeBatchPreviewView;
import com.lingsql.rbac.model.DataScopeBatchScenarioRequest;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.RoleDataScopePreviewRequest;
import com.lingsql.rbac.model.UserDataScopePreviewRequest;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class DataScopePreviewServiceTest {

    @Test
    void previewRoleScopeShouldResolveRelationSubtreeOrgs() {
        TestContext ctx = new TestContext();
        seedTenantOrgs(ctx);

        RoleDataScopePreviewRequest request = new RoleDataScopePreviewRequest();
        request.setTenantId("TENANT_A");
        request.setRoleId(10L);
        request.setOrgUnitIds(List.of(1L));
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.RELATION_SUBTREE);
        rule.setRelationTypes(List.of("service"));
        request.setDataScope(rule);

        DataScopePreviewView view = ctx.service.previewRoleScope(request, "TENANT_A", false);

        assertEquals(RbacDataScopeType.RELATION_SUBTREE, view.getEffectiveDataScope().getScopeType());
        assertEquals(2, view.getMatchedOrgCount());
        assertEquals(1, view.getAffectedRoleCount());
        assertEquals(0, view.getAffectedUserCount());
        assertTrue(view.getMatchedOrgs().stream().anyMatch(item -> "SRV".equals(item.getUnitCode())));
        assertTrue(view.getMatchedOrgs().stream().anyMatch(item -> "SRV_CHILD".equals(item.getUnitCode())));
    }

    @Test
    void previewUserScopeShouldExplainInheritedRoleSelection() {
        TestContext ctx = new TestContext();
        seedTenantOrgs(ctx);

        RbacRole relationRole = new RbacRole();
        relationRole.setId(10L);
        relationRole.setRoleCode("ROLE_SERVICE");
        relationRole.setRoleName("服务角色");
        RbacRole tenantRole = new RbacRole();
        tenantRole.setId(11L);
        tenantRole.setRoleCode("ROLE_TENANT");
        tenantRole.setRoleName("租户角色");
        when(ctx.roleRepository.findByIdIn(List.of(10L, 11L))).thenReturn(List.of(relationRole, tenantRole));
        when(ctx.roleDataScopeRepository.findActiveByRoleIds(List.of(10L, 11L))).thenReturn(List.of(
                buildScope(10L, RbacDataScopeType.RELATION, "org_code", "SERVICE")
        ));

        UserDataScopePreviewRequest request = new UserDataScopePreviewRequest();
        request.setTenantId("TENANT_A");
        request.setUserId(100L);
        request.setOrgUnitIds(List.of(1L));
        request.setRoleIds(List.of(10L, 11L));
        DataScopeRule inheritRule = new DataScopeRule();
        inheritRule.setScopeType("INHERIT");
        request.setDataScope(inheritRule);

        DataScopePreviewView view = ctx.service.previewUserScope(request, "TENANT_A", false);

        assertEquals("ROLE_INHERIT", view.getEffectiveSource());
        assertEquals(RbacDataScopeType.RELATION, view.getEffectiveDataScope().getScopeType());
        assertEquals(2, view.getRoleCandidates().size());
        assertEquals(1, view.getAffectedUserCount());
        assertEquals(2, view.getAffectedRoleCount());
        assertTrue(view.getRoleCandidates().stream().anyMatch(item -> item.isSelected() && "ROLE_SERVICE".equals(item.getRoleCode())));
    }

    @Test
    void previewRoleScopeShouldValidateSampleSql() {
        TestContext ctx = new TestContext();
        seedTenantOrgs(ctx);
        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        config.setIsolationMode("PHYSICAL");
        when(ctx.tenantDbConfigRepository.findByTenantId("TENANT_A")).thenReturn(java.util.Optional.of(config));
        when(ctx.sqlRewriter.rewrite("SELECT * FROM orders LIMIT 10", config, "TENANT_A"))
                .thenReturn("SELECT order_id FROM orders WHERE org_code IN ('SRV') LIMIT 10");

        RoleDataScopePreviewRequest request = new RoleDataScopePreviewRequest();
        request.setTenantId("TENANT_A");
        request.setOrgUnitIds(List.of(1L));
        request.setSampleSql("SELECT * FROM orders LIMIT 10");
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.RELATION);
        rule.setRelationTypes(List.of("service"));
        request.setDataScope(rule);

        DataScopePreviewView view = ctx.service.previewRoleScope(request, "TENANT_A", false);

        assertEquals(Boolean.TRUE, view.getSampleSqlValid());
        assertEquals("SELECT order_id FROM orders WHERE org_code IN ('SRV') LIMIT 10", view.getSampleRewrittenSql());
    }

    @Test
    void previewRoleScopeBatchShouldReturnMultipleScenarioResults() {
        TestContext ctx = new TestContext();
        seedTenantOrgs(ctx);

        RoleDataScopePreviewRequest request = new RoleDataScopePreviewRequest();
        request.setTenantId("TENANT_A");
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.RELATION_SUBTREE);
        rule.setRelationTypes(List.of("service"));
        request.setDataScope(rule);

        DataScopeBatchScenarioRequest scenarioA = new DataScopeBatchScenarioRequest();
        scenarioA.setScenarioName("服务归口");
        scenarioA.setOrgUnitIds(List.of(1L));
        DataScopeBatchScenarioRequest scenarioB = new DataScopeBatchScenarioRequest();
        scenarioB.setScenarioName("无组织样本");

        request.setScenarios(List.of(scenarioA, scenarioB));

        DataScopeBatchPreviewView view = ctx.service.previewRoleScopeBatch(request, "TENANT_A", false);

        assertEquals(2, view.getTotalScenarios());
        assertEquals(1, view.getWarningScenarioCount());
        assertEquals("服务归口", view.getScenarios().getFirst().getScenarioName());
    }

    @Test
    void previewUserScopeBatchShouldReturnMultipleScenarioResults() {
        TestContext ctx = new TestContext();
        seedTenantOrgs(ctx);

        UserDataScopePreviewRequest request = new UserDataScopePreviewRequest();
        request.setTenantId("TENANT_A");
        request.setUserId(100L);
        request.setRoleIds(List.of());
        request.setBaselineRoleIds(List.of());
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        request.setDataScope(rule);
        request.setBaselineDataScope(rule);

        DataScopeBatchScenarioRequest scenarioA = new DataScopeBatchScenarioRequest();
        scenarioA.setScenarioName("直属组织");
        scenarioA.setOrgUnitIds(List.of(1L));
        scenarioA.setBaselineOrgUnitIds(List.of(1L));
        DataScopeBatchScenarioRequest scenarioB = new DataScopeBatchScenarioRequest();
        scenarioB.setScenarioName("空样本");

        request.setScenarios(List.of(scenarioA, scenarioB));

        DataScopeBatchPreviewView view = ctx.service.previewUserScopeBatch(request, "TENANT_A", false);

        assertEquals(2, view.getTotalScenarios());
        assertEquals("直属组织", view.getScenarios().getFirst().getScenarioName());
        assertEquals(1, view.getWarningScenarioCount());
    }

    private void seedTenantOrgs(TestContext ctx) {
        OrgUnit src = buildOrg(1L, "TENANT_A", "SRC", "ROOT", "/1");
        OrgUnit srv = buildOrg(2L, "TENANT_A", "SRV", "DEPT", "/2");
        OrgUnit srvChild = buildOrg(3L, "TENANT_A", "SRV_CHILD", "TEAM", "/2/3");
        when(ctx.orgUnitRepository.findByTenantId("TENANT_A")).thenReturn(List.of(src, srv, srvChild));
        OrgUnitRelation relation = new OrgUnitRelation();
        relation.setTenantId("TENANT_A");
        relation.setSourceOrgUnitId(1L);
        relation.setTargetOrgUnitId(2L);
        relation.setRelationType("SERVICE");
        relation.setStatus(1);
        when(ctx.orgUnitRelationRepository.findByTenantIdAndStatusAndSourceOrgUnitIdIn("TENANT_A", 1, List.of(1L)))
                .thenReturn(List.of(relation));
    }

    private OrgUnit buildOrg(Long id, String tenantId, String unitCode, String typeCode, String treePath) {
        OrgUnit unit = new OrgUnit();
        unit.setId(id);
        unit.setTenantId(tenantId);
        unit.setUnitCode(unitCode);
        unit.setUnitName(unitCode);
        unit.setTypeCode(typeCode);
        unit.setTreePath(treePath);
        return unit;
    }

    private RbacRoleDataScope buildScope(Long roleId, String scopeType, String scopeColumn, String scopeRule) {
        RbacRoleDataScope scope = new RbacRoleDataScope();
        scope.setRoleId(roleId);
        scope.setScopeType(scopeType);
        scope.setScopeColumn(scopeColumn);
        scope.setScopeRule(scopeRule);
        scope.setStatus(1);
        return scope;
    }

    private static final class TestContext {
        private final OrgUnitRepository orgUnitRepository = Mockito.mock(OrgUnitRepository.class);
        private final OrgUnitRelationRepository orgUnitRelationRepository = Mockito.mock(OrgUnitRelationRepository.class);
        private final RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        private final RbacRoleDataScopeRepository roleDataScopeRepository = Mockito.mock(RbacRoleDataScopeRepository.class);
        private final RbacUserRoleRepository userRoleRepository = Mockito.mock(RbacUserRoleRepository.class);
        private final TenantDbConfigRepository tenantDbConfigRepository = Mockito.mock(TenantDbConfigRepository.class);
        private final com.lingsql.security.SqlRewriter sqlRewriter = Mockito.mock(com.lingsql.security.SqlRewriter.class);
        private final com.lingsql.security.SqlSecurityChecker sqlSecurityChecker = Mockito.mock(com.lingsql.security.SqlSecurityChecker.class);
        private final DataScopePreviewService service = new DataScopePreviewService(
                orgUnitRepository,
                orgUnitRelationRepository,
                roleRepository,
                roleDataScopeRepository,
                userRoleRepository,
                tenantDbConfigRepository,
                sqlRewriter,
                sqlSecurityChecker
        );
    }
}
