package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V5.0「一句话建系统」流水线任务头。
 *
 * <p>一次「录入名称+一句话描述 → 确认表结构 → 一键构建」对应一行。
 * 状态机：draft 建模中 → await_confirm 待确认（表结构已确认，库已建）
 * → running 运行中 → success / failed / stopped。
 *
 * <p>端口与 PID 落库的原因：服务重启后仍要知道「哪个系统是哪个进程」，
 * 否则「停止」按钮无从下手。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_pipeline")
public class GenPipeline extends BaseEntity {

    /** 流水线号：PPL + 时间戳，唯一 */
    private String pipelineNo;

    /** 关联 sys_project.id（prepare-db 建台账后回填） */
    private Long projectId;

    /** 录入的系统名称（中文展示用） */
    private String systemName;

    /** 一句话业务描述（喂给 AI 建模） */
    private String systemDesc;

    /** 目标数据库名 */
    private String dbName;

    /** 后端端口（分配器回填） */
    private Integer backendPort;

    /** 前端 dev 端口（分配器回填） */
    private Integer frontendPort;

    /** ai=AI 建模 manual=手动粘贴 DDL */
    private String mode;

    /** V5.2 数据初始化：rule=规则生成 off=跳过（NULL=off） */
    private String seedMode;

    /** draft/await_confirm/running/success/failed/stopped */
    private String status;

    /** 当前执行到的步骤 key */
    private String currentStep;

    /** 失败原因摘要 */
    private String failReason;

    /** 部署完成的前端访问地址 */
    private String frontendUrl;

    /** 后端子进程 PID */
    private Long backendPid;

    /** 前端子进程 PID */
    private Long frontendPid;
}
