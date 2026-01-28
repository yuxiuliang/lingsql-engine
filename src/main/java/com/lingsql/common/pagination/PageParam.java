package com.lingsql.common.pagination;

/**
 * 通用分页参数。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
public record PageParam(int page, int size) {

    /**
     * 归一化分页参数。
     *
     * @param page    页码
     * @param size    每页大小
     * @param maxSize 最大允许 size，<=0 表示不限制
     * @return 归一化结果
     * @author 余炘洋
     * @date 2026-01-28
     */
    public static PageParam normalize(int page, int size, int maxSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        if (maxSize > 0) {
            safeSize = Math.min(safeSize, maxSize);
        }
        return new PageParam(safePage, safeSize);
    }
}
