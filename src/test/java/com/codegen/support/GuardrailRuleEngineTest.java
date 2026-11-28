package com.codegen.support;

import com.codegen.entity.GenGuardrailRule;
import com.codegen.vo.GuardrailEvalVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2.1 护栏引擎契约：四类触发表达式 + BLOCK/WARN 分流 */
class GuardrailRuleEngineTest {

    private final GuardrailRuleEngine engine = new GuardrailRuleEngine();

    private GenGuardrailRule rule(String type, String expr, String severity) {
        GenGuardrailRule r = new GenGuardrailRule();
        r.setRuleCode("TEST-" + type);
        r.setTriggerType(type);
        r.setTriggerExpr(expr);
        r.setSeverity(severity);
        r.setTitle("测试规则");
        r.setReason("为什么拦");
        r.setSuggestion("怎么做才对");
        r.setEnabled(1);
        return r;
    }

    @Test
    @DisplayName("KEYWORD：用户管理单独生成 → BLOCK 命中且带解释")
    void keywordBlocksGenericBase() {
        GenGuardrailRule r = rule("KEYWORD", "用户管理|角色管理|菜单管理", "BLOCK");
        GuardrailEvalVO vo = engine.evaluate(List.of(r), "生成一套用户管理系统", Map.of());
        assertTrue(vo.isBlocked());
        assertEquals(1, vo.getBlockCount());
        assertEquals("为什么拦", vo.getHits().get(0).getReason());
        assertEquals("怎么做才对", vo.getHits().get(0).getSuggestion());
    }

    @Test
    @DisplayName("MISSING：缺事件中心 → WARN；补齐后不命中")
    void missingWarnsThenClears() {
        GenGuardrailRule r = rule("MISSING", "{\"moduleKeyword\":\"事件中心\"}", "WARN");
        GuardrailEvalVO miss = engine.evaluate(List.of(r), "城市管理平台 渣土车监管", Map.of());
        assertEquals(1, miss.getWarnCount());
        assertFalse(miss.isBlocked());

        GuardrailEvalVO ok = engine.evaluate(List.of(r), "城市管理平台 事件中心 渣土车监管", Map.of());
        assertEquals(0, ok.getWarnCount());
        assertTrue(ok.getHits().isEmpty());
    }

    @Test
    @DisplayName("COMBO：直连设备 + 水肥关键词才拦截；只台账不拦截")
    void comboRequiresBothAnswerAndKeyword() {
        GenGuardrailRule r = rule("COMBO",
                "{\"questionCode\":\"Q_DEVICE\",\"answer\":\"含直连设备\",\"keyword\":\"水肥|灌溉\"}", "BLOCK");
        GuardrailEvalVO hit = engine.evaluate(List.of(r), "水肥一体化处方下发", Map.of("Q_DEVICE", "含直连设备"));
        assertTrue(hit.isBlocked());

        GuardrailEvalVO safe = engine.evaluate(List.of(r), "水肥一体化台账", Map.of("Q_DEVICE", "只台账"));
        assertFalse(safe.isBlocked());
    }

    @Test
    @DisplayName("ANSWER 与 CLARIFY：答案命中生效；澄清规则永不参与检测")
    void answerAndClarify() {
        GenGuardrailRule a = rule("ANSWER", "{\"answerCode\":\"Q_MASTER\",\"expect\":\"尚未确定\"}", "WARN");
        GenGuardrailRule c = rule("CLARIFY", "", "INFO");
        GuardrailEvalVO vo = engine.evaluate(List.of(a, c), "任意系统", Map.of("Q_MASTER", "尚未确定"));
        assertEquals(1, vo.getWarnCount());
        assertEquals(1, vo.getHits().size());
        assertEquals(0, vo.getBlockCount());
    }

    @Test
    @DisplayName("停用规则不参与检测；空文本不误命中")
    void disabledRuleAndEmptyText() {
        GenGuardrailRule r = rule("KEYWORD", "用户管理", "BLOCK");
        r.setEnabled(0);
        assertFalse(engine.evaluate(List.of(r), "用户管理系统", Map.of()).isBlocked());
        assertFalse(engine.evaluate(List.of(r), "", Map.of()).isBlocked());
    }
}
