package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.dto.FlowActionDTO;
import com.codegen.service.FlowService;
import com.codegen.common.flow.FlowRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * P3-3 审批流接口。
 *
 * <p>四个动作刻意拆开而不是一个 /flow/action，
 * 因为权限点要分开配（提交是一线，审批是主管），合成一个接口就没法在网关层区分。
 */
@Tag(name = "审批流", description = "单据提交/审批/驳回与审批记录")
@RestController
@RequestMapping("/flow")
public class FlowController {

    @Autowired
    private FlowService flowService;

    @Operation(summary = "提交单据（草稿 → 待审批）")
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody FlowActionDTO dto) {
        flowService.submit(dto.getFlowCode(), dto.getBizId(), dto.getComment());
        return Result.ok();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/approve")
    public Result<Void> approve(@RequestBody FlowActionDTO dto) {
        flowService.approve(dto.getFlowCode(), dto.getBizId(), dto.getComment());
        return Result.ok();
    }

    @Operation(summary = "驳回到草稿")
    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody FlowActionDTO dto) {
        flowService.reject(dto.getFlowCode(), dto.getBizId(), dto.getComment());
        return Result.ok();
    }

    @Operation(summary = "审批记录")
    @GetMapping("/records")
    public Result<List<FlowRecord>> records(@RequestParam String flowCode, @RequestParam Long bizId) {
        return Result.ok(flowService.records(flowCode, bizId));
    }

    @Operation(summary = "当前状态与可用动作（给前端渲染按钮）")
    @GetMapping("/state")
    public Result<Map<String, Object>> state(@RequestParam String flowCode,
                                             @RequestParam(required = false) Long bizId) {
        return Result.ok(flowService.state(flowCode, bizId));
    }

    /**
     * V6.1：节点 JSON → BPMN 翻译并回写 sys_flow_def.bpmn_xml（审计/预览 + 下发产物库）。
     * 部署发生在产物侧（启动 Runner），平台不直连产物库——端点语义是「翻译回写」而非「部署」。
     */
    @Operation(summary = "翻译流程定义为 BPMN 并回写（V6.1）")
    @PostMapping("/def/{id}/bpmn")
    public Result<Map<String, Object>> translateBpmn(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return Result.ok(flowDefBpmnService.translate(id));
    }

    @Operation(summary = "系统绑定的流程定义清单（scaffold flowDefs 组装源，V6.1）")
    @GetMapping("/defs")
    public Result<List<Map<String, Object>>> defs(@RequestParam Long projectId) {
        return Result.ok(flowDefBpmnService.listForProject(projectId));
    }

    @Autowired
    private com.codegen.service.FlowDefBpmnService flowDefBpmnService;
}
