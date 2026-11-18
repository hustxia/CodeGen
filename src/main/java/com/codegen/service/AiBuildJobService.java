package com.codegen.service;

import com.codegen.dto.GenAiBuildPlanDTO;
import com.codegen.vo.GenAiBuildJobVO;
import com.codegen.vo.GenAiBuildPlanVO;

/**
 * V9.6 分批建模任务：大型/超大型系统的推荐建表改为「任务 + 批次」后台执行。
 */
public interface AiBuildJobService {

    /** 提交建模任务（立即返回任务号与初始进度，不等模型） */
    GenAiBuildJobVO submitPlan(GenAiBuildPlanDTO dto);

    /** 查询进度 */
    GenAiBuildJobVO status(Long jobId);

    /** 取最终结果（未完成时抛业务异常） */
    GenAiBuildPlanVO result(Long jobId);

    /** 取消：未执行的批次置 skipped，运行中的批次跑完即止 */
    void cancel(Long jobId);

    /** 调度器调用：认领并提交最多 max 个批次，返回本次提交数 */
    int dispatch(int max);

    /** 回收超时批次（重置为 pending 或判失败） */
    void resetStuck();

    /** 有批次结束时调用：全部批次收口后汇总结果 */
    void finalizeIfDone(Long jobId);
}
