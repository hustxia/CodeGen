package com.codegen.service;

import com.codegen.common.flow.FlowRecord;

import java.util.List;
import java.util.Map;

/**
 * P3-3 简单审批流。
 *
 * <p>定位是「够用的单据状态机」，不是 BPMN 引擎：
 * 流程定义在 {@code sys_flow_def.nodes} 里用 JSON 描述「从哪个状态、什么动作、到哪个状态、需要什么角色」，
 * 每次流转写一条 {@code sys_flow_task} 流水。复杂会签/并行网关不在范围内。
 *
 * <p>为什么用 JSON 而不是节点表：审批链在中小项目里通常只有 3~5 步，
 * 存成一行 JSON 可以整体读写、避免多表 JOIN，改流程时也不会有"孤儿节点"。
 */
public interface FlowService {

    /**
     * 提交：草稿(0) → 待审批(1)
     */
    void submit(String flowCode, Long bizId, String comment);

    /**
     * 审批通过：沿节点定义流转到下一状态
     */
    void approve(String flowCode, Long bizId, String comment);

    /**
     * 驳回：回到节点定义里的 rejectTo（缺省回草稿）
     */
    void reject(String flowCode, Long bizId, String comment);

    /**
     * 当前单据的审批流水（按时间正序）
     */
    List<FlowRecord> records(String flowCode, Long bizId);

    /**
     * 流程定义 + 当前可用动作，给前端渲染按钮用
     */
    Map<String, Object> state(String flowCode, Long bizId);
}
