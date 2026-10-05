package com.lingsql.schema.repository;

import com.lingsql.schema.entity.SemanticContextVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SemanticContextVersionRepository extends JpaRepository<SemanticContextVersion, Long> {

    Optional<SemanticContextVersion> findTopByTenantIdOrderByPublishedAtDesc(String tenantId);
}
