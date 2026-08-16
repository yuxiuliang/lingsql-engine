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
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 同步并原子发布持久收据。
     *
     * @param tenantId 租户标识
     * @return 已发布收据
     */
    @Transactional
    public SchemaSynchronizationReceipt synchronize(String tenantId) {
        String syncToken = UUID.randomUUID().toString();
        TenantDbConfig config = beginSynchronization(tenantId, syncToken);
        cacheService.evictSchema(config.getTenantId());
        try {
            DataSource dataSource = dataSourceManager.getDataSource(config.getTenantId());
            List<TableInfo> tables = schemaScanner.scanSchema(
                    config.getTenantId(), dataSource, config.getWhiteListTables());
            SchemaWhitelistReceiptFactory.Snapshot snapshot = receiptFactory.verify(
                    config.getWhiteListTables(), tables);
            cacheService.cacheSchemaRequired(config.getTenantId(), tables);
            LocalDateTime syncedAt = LocalDateTime.now();
            int updated = repository.completeSchemaSynchronization(
                    config.getId(), syncToken, syncedAt, snapshot.tableCount(),
                    snapshot.whitelistTableCount(), snapshot.whitelistDigest());
            if (updated != 1) {
                cacheService.evictSchema(config.getTenantId());
                throw new BizException(ErrorCode.CONFLICT, "数据源配置已变化，本次 Schema 同步结果已丢弃");
            }
            return new SchemaSynchronizationReceipt(
                    config.getTenantId(), snapshot.tableCount(), snapshot.whitelistTableCount(),
                    snapshot.whitelistDigest(), syncedAt);
        } catch (RuntimeException exception) {
            cacheService.evictSchema(config.getTenantId());
            throw exception;
        }
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
}
