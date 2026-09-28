package com.s2admin.module.system.vo;

import lombok.Data;

/**
 * 仪表盘统计
 */
@Data
public class DashboardStatsVO {

    private long userCount;
    private long roleCount;
    private long todayLoginCount;
    private long errorCount;
}
