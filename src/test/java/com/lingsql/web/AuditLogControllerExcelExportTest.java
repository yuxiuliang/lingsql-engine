package com.lingsql.web;

import com.lingsql.audit.model.SqlExecuteLogQuery;
import com.lingsql.audit.model.SqlExecuteLogView;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.audit.service.SqlExecuteLogExcelExporter;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogControllerExcelExportTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportSqlLogsExcelShouldReturnAttachmentResponse() {
        bindSession("SYS", List.of("SYS_ADMIN"));
        SqlExecuteLogService sqlExecuteLogService = Mockito.mock(SqlExecuteLogService.class);
        SqlExecuteLogExcelExporter exporter = Mockito.mock(SqlExecuteLogExcelExporter.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        SqlExecuteLogView row = new SqlExecuteLogView();
        row.setTenantId("TENANT_A");
        when(pageParamResolver.resolveExportSize()).thenReturn(500);
        when(sqlExecuteLogService.queryForExport(any(SqlExecuteLogQuery.class), eq(1), eq(500)))
                .thenReturn(new PageResult<>(List.of(row), 1, 1, 500));
        when(exporter.export(any())).thenReturn("excel-bytes".getBytes());
        AuditLogController controller = new AuditLogController(
                Mockito.mock(AuditLogService.class),
                sqlExecuteLogService,
                exporter,
                pageParamResolver
        );

        ResponseEntity<byte[]> response = controller.exportSqlLogsExcel(
                "TENANT_A",
                1001L,
                "销售额",
                "WEB",
                null,
                null
        );

        ArgumentCaptor<SqlExecuteLogQuery> queryCaptor = ArgumentCaptor.forClass(SqlExecuteLogQuery.class);
        verify(sqlExecuteLogService).queryForExport(queryCaptor.capture(), eq(1), eq(500));
        verify(exporter).export(eq(List.of(row)));
        assertEquals(200, response.getStatusCode().value());
        assertEquals("TENANT_A", queryCaptor.getValue().getTenantId());
        assertEquals(1001L, queryCaptor.getValue().getUserId());
        assertEquals("销售额", queryCaptor.getValue().getQuestion());
        assertEquals("WEB", queryCaptor.getValue().getRequestSource());
        assertTrue(response.getHeaders().getContentType() != null
                && "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                .equals(response.getHeaders().getContentType().toString()));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains(".xlsx"));
        assertEquals("excel-bytes", new String(response.getBody()));
    }

    private void bindSession(String tenantId, List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId(tenantId);
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
