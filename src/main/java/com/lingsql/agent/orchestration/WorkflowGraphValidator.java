package com.lingsql.agent.orchestration;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流图校验器：允许引用已注册阶段的受控顺序，且必须保持 SQL 安全链路不可拆分。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Component
public class WorkflowGraphValidator {

    private static final List<String> SAFE_ORDER = List.of("SQL_ARCHITECT", "SECURE_QUERY", "ANALYST");

    /**
     * 校验给定阶段序列是否满足固定安全顺序。
     */
    public void validate(List<String> stages) {
        if (stages == null || !SAFE_ORDER.equals(stages)) {
            throw new IllegalArgumentException("工作流阶段必须严格遵循 SQL_ARCHITECT -> SECURE_QUERY -> ANALYST，且 SQL 安全查询模块不可拆分");
        }
    }
}
