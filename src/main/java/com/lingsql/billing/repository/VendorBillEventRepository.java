package com.lingsql.billing.repository;

import com.lingsql.billing.entity.VendorBillEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorBillEventRepository extends JpaRepository<VendorBillEvent, Long> {
}
