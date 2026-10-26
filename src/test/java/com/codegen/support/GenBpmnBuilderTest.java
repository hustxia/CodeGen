package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V6.1 BPMN 翻译器单测：直线链、candidateGroups、驳回回退、非法输入。
 */
@DisplayName("GenBpmnBuilder：节点 JSON → BPMN 2.0 XML")
class GenBpmnBuilderTest {

    private final GenBpmnBuilder builder = new GenBpmnBuilder();

    private Map<String, Object> edge(String from, String to, String role, String rejectTo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        if (role != null) {
            m.put("roleCode", role);
        }
        if (rejectTo != null) {
            m.put("rejectTo", rejectTo);
        }
        return m;
    }

    @Test
    @DisplayName("两站直线流：start→task→网关→task→end，网关条件 approved true/false")
    void linearChain() {
        String xml = builder.build("hr_leave_flow", "请假流程", List.of(
                edge("0", "1", "manager", null),
                edge("1", "2", "hr", null)));
        assertTrue(xml.contains("<process id=\"hr_leave_flow\""));
        assertTrue(xml.contains("activiti:candidateGroups=\"manager\""));
        assertTrue(xml.contains("activiti:candidateGroups=\"hr\""));
        assertEquals(2, xml.split("userTask ", -1).length - 1, "两个审批站");
        assertEquals(2, xml.split("exclusiveGateway ", -1).length - 1, "每站一个判定网关");
        assertTrue(xml.contains("${approved == true}"));
        assertTrue(xml.contains("${approved == false}"));
        assertTrue(xml.contains("<endEvent id=\"end\""));
    }

    @Test
    @DisplayName("驳回默认回 start；rejectTo 指定目标站时指向该站任务")
    void rejectRouting() {
        String xml = builder.build("f_demo", "demo", List.of(
                edge("0", "1", "a", null),
                edge("1", "2", "b", "1")));
        // 第二站 rejectTo=1 → 驳回链 targetRef=task_s1
        assertTrue(xml.contains("targetRef=\"task_s1\""));
        // 第一站无 rejectTo → 回 start
        assertTrue(xml.contains("targetRef=\"start\""));
    }

    @Test
    @DisplayName("重复 to 状态去重保序：同一站只生成一个 UserTask")
    void dedupStations() {
        String xml = builder.build("f_dedup", "d", List.of(
                edge("0", "1", "a", null),
                edge("1", "1", "a", null)));
        assertEquals(1, xml.split("userTask ", -1).length - 1);
    }

    @Test
    @DisplayName("无 roleCode 的边不产生审批站 → 400")
    void noRole() {
        assertThrows(BusinessException.class,
                () -> builder.build("f_x", "x", List.of(edge("0", "1", null, null))));
    }

    @Test
    @DisplayName("非法流程编码 / 空节点 → 400")
    void invalidInput() {
        assertThrows(BusinessException.class, () -> builder.build("1bad", "x", List.of(edge("0", "1", "a", null))));
        assertThrows(BusinessException.class, () -> builder.build("f_ok", "x", List.of()));
    }

    @Test
    @DisplayName("XML 转义：流程名带尖括号引号不破坏 XML")
    void escaping() {
        String xml = builder.build("f_esc", "流程<\"A\">", List.of(edge("0", "1", "a", null)));
        assertTrue(xml.contains("流程&lt;&quot;A&quot;&gt;"));
        assertFalse(xml.contains("流程<\"A\">"));
    }
}
