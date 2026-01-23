package com.lingsql.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 通用分页响应。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    /**
     * 数据列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<T> list;

    /**
     * 总条数。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private long total;

    /**
     * 当前页码。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private int page;

    /**
     * 每页大小。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private int size;
}

