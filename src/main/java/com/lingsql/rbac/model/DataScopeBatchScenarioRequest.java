package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限批量校验样本。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class DataScopeBatchScenarioRequest {

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
     * 样本组织。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<Long> orgUnitIds = new ArrayList<>();

    /**
     * 基线样本组织。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<Long> baselineOrgUnitIds = new ArrayList<>();

    /**
     * 场景样本 SQL。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String sampleSql;
}
