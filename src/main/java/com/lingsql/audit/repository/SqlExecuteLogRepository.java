package com.lingsql.audit.repository;

import com.lingsql.audit.entity.SqlExecuteLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SQL 执行日志仓库。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public interface SqlExecuteLogRepository extends JpaRepository<SqlExecuteLog, Long>, JpaSpecificationExecutor<SqlExecuteLog> {
}
