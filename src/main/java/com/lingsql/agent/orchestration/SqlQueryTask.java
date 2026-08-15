package com.lingsql.agent.orchestration;

/**
 * 受控 SQL 查询插件输入。
 *
 * @param question 用户问题
 * @param requestSource 请求来源
 * @author 余炘洋
 * @date 2026-08-15
 */
public record SqlQueryTask(String question, String requestSource) {
}
