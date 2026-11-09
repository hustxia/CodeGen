package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V6.1 BPMN 自动翻译器（单一实现）：sys_flow_def 节点 JSON → BPMN 2.0 XML。
 *
 * <p>语义映射：节点 JSON 的每条边是 {@code from 状态 -> to 状态}，审批站 = 带 roleCode 的 to 状态。
 * 翻译结果是一条「UserTask 链 + 每站后接排他网关」的直线流：
 * 通过 → 下一站/结束；驳回 → 回提交（草稿）或节点 rejectTo 指定的目标站。
 * 变量约定：审批完成时设置 {@code approved=true/false}，网关条件据此分流。
 *
 * <p>刻意不做的事：并行网关、会签多实例（P1/P2）。直线流覆盖规划定的 90% 场景，
 * 复杂拓扑留给真正的 BPMN 设计器，不在这把翻译器里硬撑。
 */
@Component
public class GenBpmnBuilder {

    /** 草稿状态值，与 FlowServiceImpl.DRAFT 保持一致（字符串，不引依赖） */
    private static final String DRAFT = "0";

    /**
     * 翻译为 BPMN 2.0 XML。
     *
     * @param flowCode 流程编码（process id，必须合法标识符）
     * @param flowName 流程名（process name）
     * @param nodes    sys_flow_def.nodes 解析后的列表：{from,to,roleCode,rejectTo,condition}
     * @return BPMN 2.0 XML 文本
     */
    public String build(String flowCode, String flowName, List<Map<String, Object>> nodes) {
        if (!StringUtils.hasText(flowCode) || !flowCode.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) {
            throw new BusinessException(400, "流程编码非法（需字母开头的标识符）：" + flowCode);
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(400, "流程节点为空，无法翻译 BPMN：" + flowCode);
        }
        List<Station> stations = stations(nodes);
        if (stations.isEmpty()) {
            throw new BusinessException(400, "流程没有可翻译的审批节点（缺少带 roleCode 的 to 状态）：" + flowCode);
        }

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n")
                .append("            xmlns:activiti=\"http://activiti.org/bpmn\"\n")
                .append("            targetNamespace=\"http://codegen.local/flows\">\n")
                .append("  <process id=\"").append(flowCode).append("\" name=\"").append(esc(flowName)).append("\" isExecutable=\"true\">\n")
                .append("    <startEvent id=\"start\" name=\"提交\"/>\n");

        String prevRef = "start";
        for (int i = 0; i < stations.size(); i++) {
            Station st = stations.get(i);
            xml.append("    <sequenceFlow id=\"flow_in_").append(st.id).append("\" sourceRef=\"")
                    .append(prevRef).append("\" targetRef=\"task_").append(st.id).append("\"/>\n")
                    .append("    <userTask id=\"task_").append(st.id).append("\" name=\"").append(esc(st.name)).append("\"");
            if (StringUtils.hasText(st.roleCode)) {
                xml.append(" activiti:candidateGroups=\"").append(esc(st.roleCode)).append("\"");
            }
            xml.append("/>\n")
                    .append("    <exclusiveGateway id=\"gw_").append(st.id).append("\" name=\"").append(esc(st.name)).append("判定\"/>\n")
                    .append("    <sequenceFlow id=\"flow_task_gw_").append(st.id).append("\" sourceRef=\"task_")
                    .append(st.id).append("\" targetRef=\"gw_").append(st.id).append("\"/>\n");
            prevRef = "gw_" + st.id;
        }

        // 通过链：每站网关 →（条件）下一站或结束
        String endId = "end";
        xml.append("    <endEvent id=\"").append(endId).append("\" name=\"结束\"/>\n");
        for (int i = 0; i < stations.size(); i++) {
            Station st = stations.get(i);
            boolean last = i == stations.size() - 1;
            String approveTarget = last ? endId : "task_" + stations.get(i + 1).id;
            xml.append("    <sequenceFlow id=\"flow_ap_").append(st.id).append("\" name=\"通过\" sourceRef=\"gw_")
                    .append(st.id).append("\" targetRef=\"").append(approveTarget).append("\">\n")
                    .append("      <conditionExpression xsi:type=\"tFormalExpression\" ")
                    .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">${approved == true}</conditionExpression>\n")
                    .append("    </sequenceFlow>\n");
        }
        // 驳回链：默认回 start（重走），节点配了 rejectTo 指向目标站任务
        for (Station st : stations) {
            String rejectTarget = "start";
            if (StringUtils.hasText(st.rejectTo)) {
                Station target = byStatus(stations, st.rejectTo);
                if (target != null) {
                    rejectTarget = "task_" + target.id;
                }
            }
            xml.append("    <sequenceFlow id=\"flow_rj_").append(st.id).append("\" name=\"驳回\" sourceRef=\"gw_")
                    .append(st.id).append("\" targetRef=\"").append(rejectTarget).append("\">\n")
                    .append("      <conditionExpression xsi:type=\"tFormalExpression\" ")
                    .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">${approved == false}</conditionExpression>\n")
                    .append("    </sequenceFlow>\n");
        }

        xml.append("  </process>\n</definitions>\n");
        return xml.toString();
    }

    /**
     * 审批站提取：按边出现顺序收集「带 roleCode 的 to 状态」，去重保序。
     * 提交边（from=草稿）不产生审批站，只决定链的起点。
     */
    private List<Station> stations(List<Map<String, Object>> nodes) {
        Set<String> seen = new LinkedHashSet<>();
        List<Station> list = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String to = str(node.get("to"));
            String role = str(node.get("roleCode"));
            if (!StringUtils.hasText(to) || !StringUtils.hasText(role) || !seen.add(to)) {
                continue;
            }
            Station st = new Station();
            st.status = to;
            st.name = StringUtils.hasText(str(node.get("toName"))) ? str(node.get("toName")) : to;
            st.roleCode = role;
            st.rejectTo = str(node.get("rejectTo"));
            st.id = "s" + (list.size() + 1);
            list.add(st);
        }
        return list;
    }

    private Station byStatus(List<Station> stations, String status) {
        for (Station st : stations) {
            if (st.status.equals(status)) {
                return st;
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** 审批站：一个 UserTask */
    private static final class Station {
        String id;
        String status;
        String name;
        String roleCode;
        String rejectTo;
    }
}
