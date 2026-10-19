package com.lingsql.billing.repository;

import com.lingsql.billing.entity.VendorBillEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 供应商账单事件仓库。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
public interface VendorBillEventRepository extends JpaRepository<VendorBillEvent, Long>,
        JpaSpecificationExecutor<VendorBillEvent> {

    /**
     * 按供应商与外部账单标识查询已导入账单，用于导入幂等判重。
     *
     * @param providerName   供应商名称
     * @param externalBillId 外部账单标识
     * @return 已导入的账单事件
     * @author 余炘洋
     * @date 2026-10-17
     */
    Optional<VendorBillEvent> findFirstByProviderNameAndExternalBillId(String providerName, String externalBillId);
}
