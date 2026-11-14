package com.codegen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestSchedule;

/**
 * V9.2 定时回归：任务增删改查 + 到期扫描触发。
 */
public interface TestScheduleService {

    /** 分页查询（按 projectId / name 过滤） */
    Page<TestSchedule> page(TestCenterDTO.SchedulePage dto);

    /** 详情 */
    TestSchedule getById(Long id);

    /** 新增或更新（id 为空新增） */
    Long save(TestCenterDTO.ScheduleSave dto);

    /** 逻辑删除 */
    void remove(Long id);

    /** 启停 */
    void setEnabled(Long id, int enabled);

    /**
     * 扫描所有启用任务，对到期者加行锁认领后触发执行。
     * 由 {@code @Scheduled} 定时调用；本身无事务，认领与触发在各自事务内完成。
     */
    void scanAndTrigger();

    /** 启动兜底：把残留的认领哨兵（-1）复位，避免进程崩溃后任务永久卡死 */
    void resetStuckClaims();
}
