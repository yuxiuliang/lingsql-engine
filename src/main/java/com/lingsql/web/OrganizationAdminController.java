package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.organization.model.OrgUnitCreateRequest;
import com.lingsql.organization.model.OrgRelationExecutionCreateRequest;
import com.lingsql.organization.model.OrgRelationExecutionTimelineView;
import com.lingsql.organization.model.OrgRelationExecutionUpdateRequest;
import com.lingsql.organization.model.OrgRelationExecutionView;
import com.lingsql.organization.model.OrgUnitRelationCreateRequest;
import com.lingsql.organization.model.OrgUnitRelationUpdateRequest;
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
import com.lingsql.organization.service.OrgRelationExecutionService;
import com.lingsql.organization.service.OrganizationAdminService;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织域管理接口。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@RestController
@RequestMapping("/api/v1/admin/org")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private final OrganizationAdminService organizationAdminService;
    private final OrgRelationExecutionService orgRelationExecutionService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询组织类型列表。
     *
     * @param tenantId 租户ID
     * @return 类型列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<List<OrgUnitTypeView>> listUnitTypes(@RequestParam(required = false) String tenantId) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(organizationAdminService.listUnitTypes(resolvedTenant));
    }

    /**
     * 创建组织类型。
     *
     * @param request 请求体
     * @param http    请求
     * @return 类型视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PostMapping("/types")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgUnitTypeView> createUnitType(@Valid @RequestBody OrgUnitTypeCreateRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_TYPE_CREATE",
                "ORG_TYPE",
                request,
                http,
                () -> organizationAdminService.createUnitType(request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 更新组织类型。
     *
     * @param id      类型ID
     * @param request 请求体
     * @param http    请求
     * @return 类型视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgUnitTypeView> updateUnitType(@PathVariable Long id,
                                                       @RequestBody OrgUnitTypeUpdateRequest request,
                                                       HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_TYPE_UPDATE",
                "ORG_TYPE",
                request,
                http,
                () -> organizationAdminService.updateUnitType(id, request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 删除组织类型。
     *
     * @param id   类型ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:DELETE')")
    public ApiResponse<String> deleteUnitType(@PathVariable Long id, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.executeVoid(
                "ORG_TYPE_DELETE",
                "ORG_TYPE",
                "id=" + id,
                http,
                () -> organizationAdminService.deleteUnitType(id, session.getTenantId(), sysAdmin),
                "组织类型已删除"
        );
    }

    /**
     * 查询组织树。
     *
     * @param tenantId 租户ID
     * @return 组织树
     * @author 余炘洋
     * @date 2026-04-11
     */
    @GetMapping("/units")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<List<OrgUnitView>> listOrgUnits(@RequestParam(required = false) String tenantId) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(organizationAdminService.listOrgUnits(resolvedTenant));
    }

    /**
     * 创建组织节点。
     *
     * @param request 请求体
     * @param http    请求
     * @return 组织视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PostMapping("/units")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgUnitView> createOrgUnit(@Valid @RequestBody OrgUnitCreateRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_UNIT_CREATE",
                "ORG_UNIT",
                request,
                http,
                () -> organizationAdminService.createOrgUnit(request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 更新组织节点。
     *
     * @param id      节点ID
     * @param request 请求体
     * @param http    请求
     * @return 组织视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgUnitView> updateOrgUnit(@PathVariable Long id,
                                                  @RequestBody OrgUnitUpdateRequest request,
                                                  HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_UNIT_UPDATE",
                "ORG_UNIT",
                request,
                http,
                () -> organizationAdminService.updateOrgUnit(id, request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 删除组织节点。
     *
     * @param id   节点ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:DELETE')")
    public ApiResponse<String> deleteOrgUnit(@PathVariable Long id, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.executeVoid(
                "ORG_UNIT_DELETE",
                "ORG_UNIT",
                "id=" + id,
                http,
                () -> organizationAdminService.deleteOrgUnit(id, session.getTenantId(), sysAdmin),
                "组织节点已删除"
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
    @GetMapping("/relations")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<List<OrgUnitRelationView>> listRelations(@RequestParam(required = false) String tenantId) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(organizationAdminService.listRelations(resolvedTenant));
    }

    /**
     * 查询组织关系消费工作台。
     *
     * @param tenantId 租户ID
     * @return 工作台视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    @GetMapping("/workbench")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<OrgRelationWorkbenchView> relationWorkbench(@RequestParam(required = false) String tenantId) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(organizationAdminService.relationWorkbench(resolvedTenant));
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
    @GetMapping("/routes/resolve")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<OrgRelationRouteResolveView> resolveRelationRoute(@RequestParam(required = false) String tenantId,
                                                                         @RequestParam Long sourceOrgUnitId,
                                                                         @RequestParam String relationType) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(organizationAdminService.resolveRelationRoute(resolvedTenant, sourceOrgUnitId, relationType));
    }

    /**
     * 查询组织关系执行台账。
     *
     * @param tenantId     租户ID
     * @param status       状态
     * @param relationType 关系类型
     * @return 台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    @GetMapping("/executions")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<List<OrgRelationExecutionView>> listExecutions(@RequestParam(required = false) String tenantId,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String relationType) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(orgRelationExecutionService.listExecutions(resolvedTenant, status, relationType));
    }

    /**
     * 查询执行轨迹。
     *
     * @param id 执行项ID
     * @return 轨迹列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    @GetMapping("/executions/{id}/timeline")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<List<OrgRelationExecutionTimelineView>> executionTimeline(@PathVariable Long id) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(orgRelationExecutionService.listExecutionTimeline(id, session.getTenantId(), sysAdmin));
    }

    /**
     * 创建组织关系执行项。
     *
     * @param request 请求体
     * @param http    请求
     * @return 执行项
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PostMapping("/executions")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgRelationExecutionView> createExecution(@Valid @RequestBody OrgRelationExecutionCreateRequest request,
                                                                 HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_RELATION_EXECUTION_CREATE",
                "ORG_RELATION_EXECUTION",
                request,
                http,
                () -> orgRelationExecutionService.createExecution(request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 更新组织关系执行项。
     *
     * @param id      执行项ID
     * @param request 请求体
     * @param http    请求
     * @return 执行项
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PutMapping("/executions/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgRelationExecutionView> updateExecution(@PathVariable Long id,
                                                                 @Valid @RequestBody OrgRelationExecutionUpdateRequest request,
                                                                 HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_RELATION_EXECUTION_UPDATE",
                "ORG_RELATION_EXECUTION",
                request,
                http,
                () -> orgRelationExecutionService.updateExecution(id, request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 查询组织模板列表。
     *
     * @return 模板列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('ADMIN:ORG:READ')")
    public ApiResponse<List<OrgTemplateView>> listTemplates() {
        requireSession();
        return ApiResponse.success(organizationAdminService.listTemplates());
    }

    /**
     * 应用组织模板。
     *
     * @param request 请求体
     * @param http    请求
     * @return 初始化结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PostMapping("/templates/apply")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<String> applyTemplate(@Valid @RequestBody OrgTemplateApplyRequest request,
                                             HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_TEMPLATE_APPLY",
                "ORG_TEMPLATE",
                request,
                http,
                () -> organizationAdminService.applyTemplate(request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 创建组织关系。
     *
     * @param request 请求体
     * @param http    请求
     * @return 关系视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PostMapping("/relations")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgUnitRelationView> createRelation(@Valid @RequestBody OrgUnitRelationCreateRequest request,
                                                           HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_RELATION_CREATE",
                "ORG_RELATION",
                request,
                http,
                () -> organizationAdminService.createRelation(request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 更新组织关系。
     *
     * @param id      关系ID
     * @param request 请求体
     * @param http    请求
     * @return 关系视图
     * @author 余炘洋
     * @date 2026-04-11
     */
    @PutMapping("/relations/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:WRITE')")
    public ApiResponse<OrgUnitRelationView> updateRelation(@PathVariable Long id,
                                                           @RequestBody OrgUnitRelationUpdateRequest request,
                                                           HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ORG_RELATION_UPDATE",
                "ORG_RELATION",
                request,
                http,
                () -> organizationAdminService.updateRelation(id, request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 删除组织关系。
     *
     * @param id   关系ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    @DeleteMapping("/relations/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ORG:DELETE')")
    public ApiResponse<String> deleteRelation(@PathVariable Long id, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.executeVoid(
                "ORG_RELATION_DELETE",
                "ORG_RELATION",
                "id=" + id,
                http,
                () -> organizationAdminService.deleteRelation(id, session.getTenantId(), sysAdmin),
                "组织关系已删除"
        );
    }

    /**
     * 获取当前会话。
     *
     * @return 会话信息
     * @author 余炘洋
     * @date 2026-04-11
     */
    private RbacUserSession requireSession() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        return session;
    }

    /**
     * 规范化字符串。
     *
     * @param value 原始值
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
