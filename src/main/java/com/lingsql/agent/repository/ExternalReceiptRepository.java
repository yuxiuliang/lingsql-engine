package com.lingsql.agent.repository;

import com.lingsql.agent.entity.ExternalReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalReceiptRepository extends JpaRepository<ExternalReceipt, Long> {
}
