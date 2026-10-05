package com.lingsql.schema;

import com.lingsql.schema.repository.SemanticContextVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 语义上下文版本服务：读取租户当前生效的语义版本，供提示词显式标注与冻结。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class SemanticContextVersionService {

    private final SemanticContextVersionRepository repository;

    /**
     * 查询租户当前语义上下文版本号。
     *
     * @param tenantId 租户标识
     * @return 版本号，未发布时返回 UNSET
     */
    public String currentVersionId(String tenantId) {
        return repository.findTopByTenantIdOrderByPublishedAtDesc(tenantId)
                .map(v -> v.getVersionId())
                .orElse("UNSET");
    }
}
