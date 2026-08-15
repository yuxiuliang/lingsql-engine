package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 数据权限批量校验结果项。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class DataScopeBatchScenarioResultView {

    /**
     * 场景编码。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String scenarioCode;

    /**
     * 场景名称。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String scenarioName;

    /**
     * 预览结果。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private DataScopePreviewView preview;
}
