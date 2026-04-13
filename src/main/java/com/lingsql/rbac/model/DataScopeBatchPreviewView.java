package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限批量校验结果。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class DataScopeBatchPreviewView {

    /**
     * 场景总数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private int totalScenarios;

    /**
     * 样本 SQL 通过数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private int sampleSqlSuccessCount;

    /**
     * 告警场景数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private int warningScenarioCount;

    /**
     * 结果列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<DataScopeBatchScenarioResultView> scenarios = new ArrayList<>();
}
