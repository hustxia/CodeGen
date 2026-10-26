package com.codegen.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.dto.GenOutputPageDTO;
import com.codegen.entity.GenOutput;
import com.codegen.vo.GenOutputBatchVO;

import java.util.List;

/**
 * 代码生成器-写盘台账服务（四期 F13）
 */
public interface GenOutputService {

    /**
     * 台账分页查询（表名/批次/动作条件）
     */
    IPage<GenOutput> pageList(GenOutputPageDTO dto);

    /**
     * 批次摘要（最近 limit 个批次，按批次聚合）
     */
    List<GenOutputBatchVO> batchSummary(int limit);
}
