package com.lingsql.executor;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行结果封装。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
public class SqlExecutionResult {

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private boolean success;

    /**
     * 执行结果数据。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private List<Map<String, Object>> data;

    /**
     * 最终 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String sql;

    /**
     * 错误原因。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String errorMessage;

    /**
     * 构建成功结果。
     *
     * @param sql  SQL 文本
     * @param data 数据结果
     * @return 结果对象
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static SqlExecutionResult success(String sql, List<Map<String, Object>> data) {
        SqlExecutionResult result = new SqlExecutionResult();
        result.success = true;
        result.sql = sql;
        result.data = data == null ? Collections.emptyList() : data;
        return result;
    }

    /**
     * 构建失败结果。
     *
     * @param sql          SQL 文本
     * @param errorMessage 错误原因
     * @return 结果对象
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static SqlExecutionResult failure(String sql, String errorMessage) {
        SqlExecutionResult result = new SqlExecutionResult();
        result.success = false;
        result.sql = sql;
        result.data = Collections.emptyList();
        result.errorMessage = errorMessage;
        return result;
    }
}
