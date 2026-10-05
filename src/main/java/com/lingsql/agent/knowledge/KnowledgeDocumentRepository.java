package com.lingsql.agent.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    @Query("select d from KnowledgeDocument d where d.tenantId = :tenantId and d.status = 'PUBLISHED' and (lower(d.title) like lower(concat('%', :keyword, '%')) or lower(d.content) like lower(concat('%', :keyword, '%')))")
    List<KnowledgeDocument> search(@Param("tenantId") String tenantId, @Param("keyword") String keyword);
}
