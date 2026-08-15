package com.lingsql.web;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.ModelConfigService;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.billing.BillingAdminService;
import com.lingsql.billing.BillingCommercialReadinessService;
import com.lingsql.billing.BillingReconcileLogService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.TenantDataSourceConfigService;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.schema.entity.SemanticLexiconItem;
import com.lingsql.schema.repository.JoinRuleRepository;
import com.lingsql.schema.repository.SemanticLexiconRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class AdminSecurityResponseTest {

    @Test
    void configSerializationShouldOnlyExposeSecretConfiguredStatus() throws Exception {
        TenantDbConfig tenantConfig = new TenantDbConfig();
        tenantConfig.setPassword("database-secret");
        AiModelConfig modelConfig = new AiModelConfig();
        modelConfig.setApiKey("model-secret");
        ObjectMapper objectMapper = new ObjectMapper();

        String tenantJson = objectMapper.writeValueAsString(tenantConfig);
        String modelJson = objectMapper.writeValueAsString(modelConfig);

        assertFalse(tenantJson.contains("database-secret"));
        assertFalse(modelJson.contains("model-secret"));
        assertTrue(tenantJson.contains("\"passwordConfigured\":true"));
        assertTrue(modelJson.contains("\"apiKeyConfigured\":true"));
    }

    @BeforeEach
    void bindSysAdmin() {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId("SYS");
        session.setRoles(List.of("SYS_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testTenantConnectionShouldNotExposeRawExceptionMessage() {
        TenantDataSourceConfigService dataSourceConfigService = Mockito.mock(TenantDataSourceConfigService.class);
        AdminConfigController controller = new AdminConfigController(
                dataSourceConfigService,
                Mockito.mock(ModelConfigService.class),
                Mockito.mock(SemanticLexiconRepository.class),
                Mockito.mock(JoinRuleRepository.class),
                Mockito.mock(AuditOperationExecutor.class)
        );
        TenantDbConfig request = new TenantDbConfig();
        request.setDriverClass("com.example.notfound.Driver");
        request.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/demo");
        request.setUsername("root");
        request.setPassword("secret");

        Mockito.doThrow(new BizException(ErrorCode.CONFIG_INVALID, "连接失败，请检查数据库配置后重试"))
                .when(dataSourceConfigService).testConnection(request);

        BizException ex = assertThrows(BizException.class, () -> controller.testTenantConnection(request));

        assertEquals(ErrorCode.CONFIG_INVALID, ex.getErrorCode());
        assertEquals("连接失败，请检查数据库配置后重试", ex.getMessage());
    }

    @Test
    void semanticMappingValidationShouldReturnGenericMessage() {
        AdminConfigController controller = new AdminConfigController(
                Mockito.mock(TenantDataSourceConfigService.class),
                Mockito.mock(ModelConfigService.class),
                Mockito.mock(SemanticLexiconRepository.class),
                Mockito.mock(JoinRuleRepository.class),
                Mockito.mock(AuditOperationExecutor.class)
        );
        SemanticLexiconItem request = new SemanticLexiconItem();
        request.setTenantId("TENANT_A");
        request.setTerm("销售额");
        request.setMapping("sum(");

        BizException ex = assertThrows(BizException.class, () -> controller.createSemantic(request));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
        assertEquals("映射表达式语法错误", ex.getMessage());
    }

    @Test
    void billingOverrideQueryShouldNotExposeRawExceptionMessage() {
        BillingAdminService billingAdminService = Mockito.mock(BillingAdminService.class);
        when(billingAdminService.getOverride("TENANT_A"))
                .thenThrow(new RuntimeException("Access denied for user 'root'@'localhost'"));

        BillingAdminController controller = new BillingAdminController(
                billingAdminService,
                Mockito.mock(AuditOperationExecutor.class),
                Mockito.mock(BillingReconcileLogService.class),
                Mockito.mock(BillingCommercialReadinessService.class),
                Mockito.mock(com.lingsql.billing.invocation.IPhysicalBillingResolution.class)
        );

        ApiResponse<?> response = controller.getOverride("TENANT_A");

        assertEquals(500, response.getCode());
        assertEquals("查询租户计费覆盖失败，请稍后重试", response.getMessage());
        assertFalse(response.getMessage().contains("root"));
    }
}
