package com.codegen.quality;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2.2 规则库契约：52 条、ID 唯一、DET 规则可执行、LLM 规则可送审。
 */
class QualityRuleCatalogTest {

    @Test
    void catalogHasExactly52ExecutableRules() {
        List<QualityRule> rules = QualityRuleCatalog.all();
        assertEquals(52, rules.size(), "坑库提炼后应固化 52 条可执行规则");
    }

    @Test
    void ruleIdsAreUniqueAndWellFormed() {
        Set<String> ids = new HashSet<>();
        for (QualityRule rule : QualityRuleCatalog.all()) {
            assertTrue(rule.id().matches("Q\\d{3}"), "规则 ID 格式应为 Qxxx: " + rule.id());
            assertTrue(ids.add(rule.id()), "规则 ID 重复: " + rule.id());
        }
        assertEquals(52, ids.size());
    }

    @Test
    void everyRuleCarriesLineLevelBadAndGoodSamples() {
        for (QualityRule rule : QualityRuleCatalog.all()) {
            assertNotNull(rule.title());
            assertNotNull(rule.message());
            assertFalse(rule.badSnippet().isBlank(), rule.id() + " 缺 ❌ 示例");
            assertFalse(rule.goodSnippet().isBlank(), rule.id() + " 缺 ✅ 示例");
        }
    }

    @Test
    void deterministicRulesHaveDetectorOrPattern() {
        for (QualityRule rule : QualityRuleCatalog.deterministicRules()) {
            assertTrue(rule.detector() != QualityRule.Detector.NONE,
                    rule.id() + " DET 规则必须有检测器");
            if (rule.detector() == QualityRule.Detector.REGEX) {
                assertNotNull(rule.pattern(), rule.id() + " REGEX 规则必须有正则");
            }
        }
    }

    @Test
    void dualEnginesAreBothPresent() {
        assertTrue(QualityRuleCatalog.deterministicRules().size() >= 20);
        assertEquals(52 - QualityRuleCatalog.deterministicRules().size(), QualityRuleCatalog.llmRules().size());
        assertTrue(QualityRuleCatalog.llmRules().size() >= 20);
    }
}
