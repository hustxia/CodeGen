package com.codegen.service;

import com.codegen.common.exception.BusinessException;
import com.codegen.support.GenBpmnBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.1 流程定义 → BPMN 翻译服务（平台侧）：读 sys_flow_def、调 GenBpmnBuilder、回写 bpmn_xml。
 * 唯一职责是「翻译 + 落审计」；部署在产物侧 FlowDeployRunner，平台不直连产物库。
 */
@Service
public class FlowDefBpmnService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenBpmnBuilder bpmnBuilder;

    /**
     * 翻译并回写，返回 {flowCode, bpmnXml}。
     */
    public Map<String, Object> translate(Long flowDefId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, flow_code, flow_name, nodes FROM sys_flow_def WHERE id = ? AND deleted = 0", flowDefId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "流程定义不存在：" + flowDefId);
        }
        Map<String, Object> def = rows.get(0);
        String flowCode = String.valueOf(def.get("flow_code"));
        String flowName = String.valueOf(def.get("flow_name"));
        String nodesJson = String.valueOf(def.get("nodes"));
        List<Map<String, Object>> nodes;
        try {
            nodes = new com.fasterxml.jackson.databind.ObjectMapper().readValue(nodesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (Exception e) {
            throw new BusinessException(400, "流程节点 JSON 解析失败：" + e.getMessage());
        }
        String xml = bpmnBuilder.build(flowCode, flowName, nodes);
        jdbcTemplate.update("UPDATE sys_flow_def SET bpmn_xml = ? WHERE id = ?", xml, flowDefId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flowCode", flowCode);
        result.put("bpmnXml", xml);
        return result;
    }

    /**
     * V6.1：收集系统绑定的流程定义（gen_table.flow_code → sys_flow_def），供 scaffold 调用方
     * （前端向导 / 流水线 / 冒烟脚本）组装 flowDefs 下发——平台不直连产物库的「组装」一侧。
     * nodes 已解析为结构化列表；bpmn_xml 为空时调用方可用 translate() 现译。
     */
    public List<Map<String, Object>> listForProject(Long projectId) {
        // collation 混排：sys_flow_def 建于 V2.0（utf8mb4_0900_ai_ci），gen_table 是 general_ci——
        // 跨表等值 join 必须显式 COLLATE，否则 Illegal mix of collations 直接 500
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT d.id, d.flow_code, d.flow_name, d.table_name, d.status_col, d.nodes, d.bpmn_xml "
                        + "FROM sys_flow_def d "
                        + "WHERE d.deleted = 0 AND d.enabled = 1 AND EXISTS ("
                        + "  SELECT 1 FROM gen_table t WHERE t.flow_code = d.flow_code COLLATE utf8mb4_general_ci "
                        + "  AND t.project_id = ? AND t.deleted = 0)", projectId);
        List<Map<String, Object>> defs = new java.util.ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("flowCode", r.get("flow_code"));
            def.put("flowName", r.get("flow_name"));
            def.put("tableName", r.get("table_name"));
            def.put("statusCol", r.get("status_col"));
            try {
                def.put("nodes", new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        String.valueOf(r.get("nodes")),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                        }));
            } catch (Exception e) {
                def.put("nodes", List.of());
            }
            def.put("bpmnXml", r.get("bpmn_xml"));
            defs.add(def);
        }
        return defs;
    }
}
