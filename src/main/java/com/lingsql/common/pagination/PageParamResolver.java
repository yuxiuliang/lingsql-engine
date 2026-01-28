package com.lingsql.common.pagination;

import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 分页参数解析器。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Component
@RequiredArgsConstructor
public class PageParamResolver {

    private final LingSqlProperties properties;

    /**
     * 解析分页参数并应用全局最大分页限制。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 归一化分页参数
     * @author 余炘洋
     * @date 2026-01-28
     */
    public PageParam resolve(int page, int size) {
        LingSqlProperties.Web web = properties.getWeb();
        int maxSize = web == null ? 0 : web.getMaxPageSize();
        return PageParam.normalize(page, size, maxSize);
    }

    /**
     * 解析导出分页参数。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 归一化分页参数
     * @author 余炘洋
     * @date 2026-01-28
     */
    public PageParam resolveExport(int page, int size) {
        LingSqlProperties.Web web = properties.getWeb();
        int maxSize = web == null ? 0 : web.getExportMaxRows();
        return PageParam.normalize(page, size, maxSize);
    }

    /**
     * 解析导出最大行数。
     *
     * @return 导出最大行数
     * @author 余炘洋
     * @date 2026-01-28
     */
    public int resolveExportSize() {
        LingSqlProperties.Web web = properties.getWeb();
        if (web == null) {
            return 1;
        }
        int maxRows = web.getExportMaxRows();
        return Math.max(maxRows, 1);
    }
}
