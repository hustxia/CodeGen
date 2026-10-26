package com.codegen.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.dto.GenPipelineCreateDTO;
import com.codegen.dto.GenPipelinePageDTO;
import com.codegen.dto.GenPipelinePrepareDTO;
import com.codegen.vo.GenPipelineLintFixVO;
import com.codegen.vo.GenPipelineLintVO;
import com.codegen.vo.GenPipelinePrepareVO;
import com.codegen.vo.GenPipelineVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * V5.0「一句话建系统」流水线：建模 → 建库 → 骨架 → 生成 → 打包 → 部署 → 健康检查。
 */
public interface GenPipelineService {

    /** 创建任务（建模结果已在前端暂存/确认，随创建一起落库） */
    GenPipelineVO create(GenPipelineCreateDTO dto);

    /** 分页查询 */
    IPage<GenPipelineVO> page(GenPipelinePageDTO dto);

    /** 详情（头 + 全部步骤） */
    GenPipelineVO detail(Long id);

    /** 建库建表 + 建系统台账 + 导入表配置（表结构确认后调用，幂等可重试） */
    GenPipelinePrepareVO prepareDb(Long id, GenPipelinePrepareDTO dto);

    /** 启动流水线（异步执行，进度走 SSE；同一时刻仅 1 条运行中） */
    GenPipelineVO run(Long id);

    /** 停止：杀双进程并置 stopped */
    GenPipelineVO stop(Long id);

    /** 生成前置 lint：目标库每表比对 BaseEntity 约定列（只读 dry-run，语义不变） */
    GenPipelineLintVO lint(String dbName);

    /** lint 一键补齐（V5.1）：逐表执行 ALTER 补缺审计列，幂等，单表失败不拖垮整批，执行后复检 */
    GenPipelineLintFixVO fix(Long id);

    /** V5.2 数据初始化：存量系统补跑 seed（只补台账中 0 行的表，幂等） */
    com.codegen.vo.GenSeedVO seedExisting(Long id);

    /** SSE 进度订阅 */
    SseEmitter stream(Long id);
}
