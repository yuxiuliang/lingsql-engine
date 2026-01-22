package com.lingsql.executor;

/**
 * SQL 执行异常封装。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
public class SqlExecutionException extends RuntimeException {

    private final String sql;

    /**
     * 构造函数。
     *
     * @param message 错误原因
     * @param sql     SQL 文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    public SqlExecutionException(String message, String sql) {
        super(message);
        this.sql = sql;
    }

    /**
     * 获取 SQL 文本。
     *
     * @return SQL
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String getSql() {
        return sql;
    }
}
