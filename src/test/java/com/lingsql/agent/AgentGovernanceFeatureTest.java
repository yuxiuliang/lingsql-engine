package com.lingsql.agent;

import com.lingsql.agent.knowledge.DbKnowledgeCorpusProvider;
import com.lingsql.agent.knowledge.KnowledgeDocument;
import com.lingsql.agent.knowledge.KnowledgeDocumentRepository;
import com.lingsql.agent.orchestration.WorkflowGraphValidator;
import com.lingsql.billing.entity.VendorBillEvent;
import com.lingsql.billing.invocation.BillingInvocationRepository;
import com.lingsql.billing.repository.VendorBillEventRepository;
import com.lingsql.billing.service.VendorBillImportService;
import com.lingsql.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 新增受控能力的单元测试：工作流校验、账单导入、知识检索、外部回执。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class AgentGovernanceFeatureTest {

    @Test
    void workflowGraphValidatorRejectsUnsafeReorder() {
        WorkflowGraphValidator validator = new WorkflowGraphValidator();
        assertDoesNotThrow(() -> validator.validate(List.of("SQL_ARCHITECT", "SECURE_QUERY", "ANALYST")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of("SQL_ARCHITECT", "ANALYST")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of("SECURE_QUERY")));
    }

    @Test
    void vendorBillImportParsesCsvRows() {
        VendorBillEventRepository repository = mock(VendorBillEventRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        VendorBillImportService service = new VendorBillImportService(repository, mock(BillingInvocationRepository.class));
        String csv = "tenantId,providerName,externalBillId,amountMinor,currency,status\n"
                + "t1,openai,b-1,1230,USD,BILLED";
        var report = service.importCsv(csv);
        assertEquals(1, report.importedCount());
        assertEquals(1, report.matchSummary().getOrDefault("NO_REFERENCE", 0));
        verify(repository).save(any(VendorBillEvent.class));
    }

    @Test
    void vendorBillImportRejectsBlankInput() {
        VendorBillImportService service = new VendorBillImportService(mock(VendorBillEventRepository.class),
                mock(BillingInvocationRepository.class));
        assertThrows(BizException.class, () -> service.importCsv(""));
    }

    @Test
    void dbKnowledgeCorpusReturnsTenantScopedHits() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle("销售额口径");
        doc.setContent("按自然月统计");
        doc.setVersion("1.0");
        when(repository.search("t1", "口径")).thenReturn(List.of(doc));
        DbKnowledgeCorpusProvider provider = new DbKnowledgeCorpusProvider(repository);
        var result = provider.retrieve("t1", "口径", 5);
        assertTrue(result.success());
        assertEquals(1, result.hits().size());
        assertTrue(result.hits().get(0).contains("销售额口径"));
    }

    @Test
    void dbKnowledgeCorpusNoHitIsNotSuccess() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        when(repository.search(anyString(), anyString())).thenReturn(List.of());
        DbKnowledgeCorpusProvider provider = new DbKnowledgeCorpusProvider(repository);
        var result = provider.retrieve("t1", "x", 5);
        assertFalse(result.success());
        assertEquals("NO_HIT", result.status());
    }
}