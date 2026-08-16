package com.lingsql.schema;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.datasource.DataSourceManager;
import com.lingsql.datasource.TenantDbConfigErrors;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.schema.model.SchemaSynchronizationReceipt;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Schema 同步深模块。Interface 只有一次同步；同租户同步串行执行，绝不发布部分收据。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Service
@RequiredArgsConstructor
public class SchemaSynchronizationService {

    private static final int ACTIVE = 1;
    private static final int NOT_SYNCED = 0;

    private final TenantDbConfigRepository repository;
    private final DataSourceManager dataSourceManager;
    private final SchemaScanner schemaScanner;
    private final MetadataCacheService cacheService;
    private final SchemaWhitelistReceiptFactory receiptFactory;
    private final TransactionOperations transactions;

    /**
     * 同步并发布持久收据。
     *
     * <p>开始事务先使旧收据失效，扫描成功后再由完成事务发布新收据，避免失败回滚成旧 READY 状态。</p>
     *
     * @param tenantId 租户标识
     * @return 已发布收据
     */
    public SchemaSynchronizationReceipt synchronize(String tenantId) {
        String syncToken = UUID.randomUUID().toString();
        TenantDbConfig config = transactions.execute(status -> beginSynchronization(tenantId, syncToken));
        if (config == null) {
            throw new IllegalStateException("Schema 同步事务未返回数据源配置");
        }
        cacheService.evictSchema(config.getTenantId());
        DataSource dataSource = dataSourceManager.getDataSource(config.getTenantId());
        List<TableInfo> tables = schemaScanner.scanSchema(
                config.getTenantId(), dataSource, config.getWhiteListTables());
        SchemaWhitelistReceiptFactory.Snapshot snapshot = receiptFactory.verify(
                config.getWhiteListTables(), tables);
        SchemaSynchronizationReceipt receipt = transactions.execute(status -> completeSynchronization(
                config.getTenantId(), syncToken, tables, snapshot));
        if (receipt == null) {
            throw new IllegalStateException("Schema 同步事务未返回完成收据");
        }
        return receipt;
    }

    private TenantDbConfig beginSynchronization(String tenantId, String syncToken) {
        TenantDbConfig config = repository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(TenantDbConfigErrors::missingConfig);
        if (!Integer.valueOf(ACTIVE).equals(config.getStatus())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据源未启用，不能同步 Schema");
        }
        config.setSchemaSyncStatus(NOT_SYNCED);
        config.setLastSyncTime(null);
        config.setSchemaTableCount(null);
        config.setSchemaWhitelistTableCount(null);
        config.setSchemaWhitelistDigest(null);
        config.setSchemaSyncToken(syncToken);
        return repository.saveAndFlush(config);
    }

    private SchemaSynchronizationReceipt completeSynchronization(
            String tenantId,
            String syncToken,
            List<TableInfo> tables,
            SchemaWhitelistReceiptFactory.Snapshot snapshot
    ) {
        TenantDbConfig config = repository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(TenantDbConfigErrors::missingConfig);
        if (!syncToken.equals(config.getSchemaSyncToken())) {
            throw new BizException(ErrorCode.CONFLICT, "数据源配置已变化，本次 Schema 同步结果已丢弃");
        }
        cacheService.cacheSchemaRequired(tenantId, tables);
        LocalDateTime syncedAt = LocalDateTime.now();
        config.setSchemaSyncStatus(1);
        config.setLastSyncTime(syncedAt);
        config.setSchemaTableCount(snapshot.tableCount());
        config.setSchemaWhitelistTableCount(snapshot.whitelistTableCount());
        config.setSchemaWhitelistDigest(snapshot.whitelistDigest());
        config.setSchemaSyncToken(null);
        repository.saveAndFlush(config);
        return new SchemaSynchronizationReceipt(
                tenantId, snapshot.tableCount(), snapshot.whitelistTableCount(),
                snapshot.whitelistDigest(), syncedAt);
    }
}
