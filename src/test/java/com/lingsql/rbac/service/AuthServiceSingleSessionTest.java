package com.lingsql.rbac.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.organization.entity.OrgUnit;
import com.lingsql.organization.entity.OrgUnitRelation;
import com.lingsql.organization.entity.UserOrgMembership;
import com.lingsql.organization.repository.OrgUnitRelationRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import com.lingsql.rbac.authorization.AuthorizationDecision;
import com.lingsql.rbac.authorization.AuthorizationStamp;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserDataScopeRepository;
import com.lingsql.rbac.repository.RbacUserExperiencePolicyRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceSingleSessionTest {

    @Test
    void loginShouldRevokeHistoricalSessionsWhenSingleSessionEnabled() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService).revokeUserTokens(100L);
        verify(ctx.refreshTokenService).revokeByUserId(100L);
    }

    @Test
    void loginShouldNotRevokeHistoricalSessionsWhenSingleSessionDisabled() {
        TestContext ctx = new TestContext(false);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService, never()).revokeUserTokens(anyLong());
        verify(ctx.refreshTokenService, never()).revokeByUserId(anyLong());
    }

    @Test
    void refreshShouldRevokeHistoricalAccessTokenWhenSingleSessionEnabled() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        RefreshTokenService.RefreshTokenPayload payload =
                new RefreshTokenService.RefreshTokenPayload(
                        100L,
                        "TENANT_A",
                        System.currentTimeMillis(),
                        1L,
                        new AuthorizationStamp("a".repeat(64))
                );
        RefreshTokenService.RefreshTokenResult result =
                new RefreshTokenService.RefreshTokenResult(payload, "refresh_token_new");
        when(ctx.refreshTokenService.rotateRefreshToken(anyString(), any(Duration.class))).thenReturn(result);
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token_new");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(7200L);
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.refresh("refresh_token_old");

        assertEquals("access_token_new", response.getToken());
        assertEquals("refresh_token_new", response.getRefreshToken());
        verify(ctx.tokenService).revokeUserTokens(100L);
    }

    @Test
    void loginShouldResolveOrgScopePayloads() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        UserOrgMembership membership = new UserOrgMembership();
        membership.setUserId(100L);
        membership.setOrgUnitId(200L);
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(100L)).thenReturn(List.of(membership));

        OrgUnit root = new OrgUnit();
        root.setId(200L);
        root.setTenantId("TENANT_A");
        root.setUnitCode("HN");
        root.setTypeCode("GOV_ORG");
        root.setTagText("政务,省级");
        root.setTreePath("/200");
        OrgUnit child = new OrgUnit();
        child.setId(201L);
        child.setTenantId("TENANT_A");
        child.setUnitCode("HN_CS");
        child.setTreePath("/200/201");
        when(ctx.orgUnitRepository.findByIdIn(List.of(200L))).thenReturn(List.of(root));
        when(ctx.orgUnitRepository.findByTenantId("TENANT_A")).thenReturn(List.of(root, child));

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.orgUnitRepository).findByTenantId("TENANT_A");
        verify(ctx.tokenService).createToken(argThat(session ->
                session.getMemberOrgUnitCodes().contains("HN")
                        && session.getMemberOrgTypeCodes().contains("GOV_ORG")
                        && session.getMemberOrgTags().containsAll(List.of("政务".toUpperCase(), "省级".toUpperCase()))
        ));
    }

    @Test
    void loginShouldRejectWhenTenantMissing() {
        TestContext ctx = new TestContext(true);

        LoginRequest request = new LoginRequest();
        request.setTenantId(" ");
        request.setUsername("alice");
        request.setPassword("pwd");

        assertThrows(BadCredentialsException.class, () -> ctx.authService.login(request));
        verify(ctx.userRepository, never()).findTop2ByUsername(anyString());
        verify(ctx.userRepository, never()).findByTenantIdAndUsername(anyString(), anyString());
    }

    @Test
    void loginShouldExpandOwnershipTargetSubtreeCodes() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        UserOrgMembership membership = new UserOrgMembership();
        membership.setUserId(100L);
        membership.setOrgUnitId(200L);
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(100L)).thenReturn(List.of(membership));

        OrgUnit source = new OrgUnit();
        source.setId(200L);
        source.setTenantId("TENANT_A");
        source.setUnitCode("SRC");
        source.setTreePath("/200");
        OrgUnit target = new OrgUnit();
        target.setId(300L);
        target.setTenantId("TENANT_A");
        target.setUnitCode("OWN");
        target.setTreePath("/300");
        OrgUnit targetChild = new OrgUnit();
        targetChild.setId(301L);
        targetChild.setTenantId("TENANT_A");
        targetChild.setUnitCode("OWN_CHILD");
        targetChild.setTreePath("/300/301");
        when(ctx.orgUnitRepository.findByIdIn(List.of(200L))).thenReturn(List.of(source));
        when(ctx.orgUnitRepository.findByTenantId("TENANT_A")).thenReturn(List.of(source, target, targetChild));

        OrgUnitRelation relation = new OrgUnitRelation();
        relation.setTenantId("TENANT_A");
        relation.setSourceOrgUnitId(200L);
        relation.setTargetOrgUnitId(300L);
        relation.setRelationType("OWNERSHIP");
        relation.setStatus(1);
        when(ctx.orgUnitRelationRepository.findByTenantIdAndStatusAndSourceOrgUnitIdIn(
                "TENANT_A", 1, List.of(200L)
        )).thenReturn(List.of(relation));

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService).createToken(argThat(session ->
                session.getMemberOrgSubtreeCodes().containsAll(List.of("SRC"))
                        && session.getRelationDirectScopes().get("OWNERSHIP").equals(List.of("OWN"))
                        &&
                        session.getOrgUnitCodes().containsAll(List.of("SRC", "OWN", "OWN_CHILD"))
                        && session.getOrgSubtreeCodes().containsAll(List.of("SRC", "OWN", "OWN_CHILD"))
                        && session.getRelationScopes().get("OWNERSHIP").containsAll(List.of("OWN", "OWN_CHILD"))
        ));
    }

    @Test
    void loginShouldExpandSuperviseServiceMatrixAndCooperateTargetSubtree() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        UserOrgMembership membership = new UserOrgMembership();
        membership.setUserId(100L);
        membership.setOrgUnitId(200L);
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(100L)).thenReturn(List.of(membership));

        OrgUnit source = buildOrgUnit(200L, "TENANT_A", "SRC", "/200");
        OrgUnit supervise = buildOrgUnit(300L, "TENANT_A", "SUP", "/300");
        OrgUnit superviseChild = buildOrgUnit(301L, "TENANT_A", "SUP_CHILD", "/300/301");
        OrgUnit service = buildOrgUnit(400L, "TENANT_A", "SRV", "/400");
        OrgUnit serviceChild = buildOrgUnit(401L, "TENANT_A", "SRV_CHILD", "/400/401");
        OrgUnit matrix = buildOrgUnit(500L, "TENANT_A", "MAT", "/500");
        OrgUnit matrixChild = buildOrgUnit(501L, "TENANT_A", "MAT_CHILD", "/500/501");
        OrgUnit cooperate = buildOrgUnit(600L, "TENANT_A", "COO", "/600");
        OrgUnit cooperateChild = buildOrgUnit(601L, "TENANT_A", "COO_CHILD", "/600/601");
        when(ctx.orgUnitRepository.findByIdIn(List.of(200L))).thenReturn(List.of(source));
        when(ctx.orgUnitRepository.findByTenantId("TENANT_A")).thenReturn(List.of(
                source,
                supervise,
                superviseChild,
                service,
                serviceChild,
                matrix,
                matrixChild,
                cooperate,
                cooperateChild
        ));

        OrgUnitRelation superviseRelation = buildRelation("TENANT_A", 200L, 300L, "SUPERVISE");
        OrgUnitRelation serviceRelation = buildRelation("TENANT_A", 200L, 400L, "SERVICE");
        OrgUnitRelation matrixRelation = buildRelation("TENANT_A", 200L, 500L, "MATRIX");
        OrgUnitRelation cooperateRelation = buildRelation("TENANT_A", 200L, 600L, "COOPERATE");
        when(ctx.orgUnitRelationRepository.findByTenantIdAndStatusAndSourceOrgUnitIdIn(
                "TENANT_A", 1, List.of(200L)
        )).thenReturn(List.of(superviseRelation, serviceRelation, matrixRelation, cooperateRelation));

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService).createToken(argThat(session ->
                session.getMemberOrgSubtreeCodes().containsAll(List.of("SRC"))
                        && session.getRelationDirectScopes().get("SUPERVISE").equals(List.of("SUP"))
                        && session.getRelationDirectScopes().get("SERVICE").equals(List.of("SRV"))
                        && session.getRelationDirectScopes().get("MATRIX").equals(List.of("MAT"))
                        && session.getRelationDirectScopes().get("COOPERATE").equals(List.of("COO"))
                        &&
                session.getOrgUnitCodes().containsAll(List.of(
                        "SRC",
                        "SUP",
                        "SUP_CHILD",
                        "SRV",
                        "SRV_CHILD",
                        "MAT",
                        "MAT_CHILD",
                        "COO",
                        "COO_CHILD"
                ))
                        && session.getOrgSubtreeCodes().containsAll(List.of(
                        "SRC",
                        "SUP",
                        "SUP_CHILD",
                        "SRV",
                        "SRV_CHILD",
                        "MAT",
                        "MAT_CHILD",
                        "COO",
                        "COO_CHILD"
                ))
                        && session.getRelationScopes().get("SUPERVISE").containsAll(List.of("SUP", "SUP_CHILD"))
                        && session.getRelationScopes().get("SERVICE").containsAll(List.of("SRV", "SRV_CHILD"))
                        && session.getRelationScopes().get("MATRIX").containsAll(List.of("MAT", "MAT_CHILD"))
                        && session.getRelationScopes().get("COOPERATE").equals(List.of("COO", "COO_CHILD"))
        ));
    }

    @Test
    void loginShouldGrantAllDataScopeToSuperAdmin() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of(9L));
        RbacRole superAdminRole = new RbacRole();
        superAdminRole.setId(9L);
        superAdminRole.setRoleCode("SUPER_ADMIN");
        when(ctx.roleRepository.findByIdIn(List.of(9L))).thenReturn(List.of(superAdminRole));
        when(ctx.roleDataScopeRepository.findActiveByRoleIds(List.of(9L))).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(100L)).thenReturn(List.of());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService).createToken(argThat(session ->
                session.getDataScope() != null
                        && RbacDataScopeType.ALL.equals(session.getDataScope().getScopeType())
        ));
    }

    private static RbacUser buildActiveUser() {
        RbacUser user = new RbacUser();
        user.setId(100L);
        user.setTenantId("TENANT_A");
        user.setUsername("alice");
        user.setPassword("hashed_pwd");
        user.setStatus(1);
        user.setIsExperience(0);
        user.setFailedLoginCount(0);
        return user;
    }

    private static LoginRequest buildLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setTenantId("TENANT_A");
        request.setUsername("alice");
        request.setPassword("pwd");
        return request;
    }

    private static OrgUnit buildOrgUnit(Long id, String tenantId, String unitCode, String treePath) {
        OrgUnit unit = new OrgUnit();
        unit.setId(id);
        unit.setTenantId(tenantId);
        unit.setUnitCode(unitCode);
        unit.setTreePath(treePath);
        return unit;
    }

    private static OrgUnitRelation buildRelation(String tenantId, Long sourceOrgUnitId, Long targetOrgUnitId, String relationType) {
        OrgUnitRelation relation = new OrgUnitRelation();
        relation.setTenantId(tenantId);
        relation.setSourceOrgUnitId(sourceOrgUnitId);
        relation.setTargetOrgUnitId(targetOrgUnitId);
        relation.setRelationType(relationType);
        relation.setStatus(1);
        return relation;
    }

    private static final class TestContext {
        private final RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        private final RbacUserRoleRepository userRoleRepository = Mockito.mock(RbacUserRoleRepository.class);
        private final RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        private final RbacRolePermissionRepository rolePermissionRepository = Mockito.mock(RbacRolePermissionRepository.class);
        private final RbacPermissionRepository permissionRepository = Mockito.mock(RbacPermissionRepository.class);
        private final RbacRoleDataScopeRepository roleDataScopeRepository = Mockito.mock(RbacRoleDataScopeRepository.class);
        private final RbacUserDataScopeRepository userDataScopeRepository = Mockito.mock(RbacUserDataScopeRepository.class);
        private final RbacUserExperiencePolicyRepository experiencePolicyRepository = Mockito.mock(RbacUserExperiencePolicyRepository.class);
        private final TokenService tokenService = Mockito.mock(TokenService.class);
        private final RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        private final OrgUnitRepository orgUnitRepository = Mockito.mock(OrgUnitRepository.class);
        private final OrgUnitRelationRepository orgUnitRelationRepository = Mockito.mock(OrgUnitRelationRepository.class);
        private final UserOrgMembershipRepository userOrgMembershipRepository = Mockito.mock(UserOrgMembershipRepository.class);
        private final RbacTenantPermissionRepository tenantPermissionRepository = Mockito.mock(RbacTenantPermissionRepository.class);
        private final IAuthorizationSessionLifecycle authorizationSessionLifecycle =
                Mockito.mock(IAuthorizationSessionLifecycle.class);
        private final AuthService authService;

        private TestContext(boolean singleSessionEnabled) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAuth().setSingleSessionEnabled(singleSessionEnabled);
            when(authorizationSessionLifecycle.capture(anyLong(), anyString()))
                    .thenReturn(new AuthorizationStamp("a".repeat(64)));
            when(authorizationSessionLifecycle.validate(anyLong(), anyString(), any()))
                    .thenReturn(AuthorizationDecision.permit());
            this.authService = new AuthService(
                    userRepository,
                    userRoleRepository,
                    roleRepository,
                    rolePermissionRepository,
                    permissionRepository,
                    roleDataScopeRepository,
                    userDataScopeRepository,
                    experiencePolicyRepository,
                    tokenService,
                    refreshTokenService,
                    tenantRepository,
                    properties,
                    passwordEncoder,
                    Mockito.mock(PasswordCredentialService.class),
                    new ObjectMapper(),
                    orgUnitRepository,
                    orgUnitRelationRepository,
                    userOrgMembershipRepository,
                    tenantPermissionRepository,
                    authorizationSessionLifecycle
            );
        }
    }
}
