package com.lingsql.tenant.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户生命周期运营工作台视图。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TenantLifecycleWorkbenchView {

    /**
     * 生成时间。
     */
    private LocalDateTime generatedAt;

    /**
     * 任务总数。
     */
    private int totalTasks;

    /**
     * P0 任务数。
     */
    private int p0Count;

    /**
     * P1 任务数。
     */
    private int p1Count;

    /**
     * P2 任务数。
     */
    private int p2Count;

    /**
     * 任务列表。
     */
    private List<TenantLifecycleOpsTaskView> tasks = new ArrayList<>();
}

