package com.codegen.quality;

import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenQualityIssueVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确定性引擎：每条规则命中后必须给出行号与行级 ❌→✅。
 */
class DeterministicQualityScannerTest {

    private final DeterministicQualityScanner scanner = new DeterministicQualityScanner();

    private List<GenQualityIssueVO> scan(String path, String content) {
        return scanner.scan(List.of(new GenFileVO("demo", path, content)));
    }

    private GenQualityIssueVO byRule(List<GenQualityIssueVO> issues, String ruleId) {
        return issues.stream().filter(i -> ruleId.equals(i.getRuleId())).findFirst().orElse(null);
    }

    @Test
    void detectsPathvariableWithoutNameAtLineLevel() {
        List<GenQualityIssueVO> issues = scan("src/DemoController.java",
                "class DemoController {\n    @GetMapping(\"/{id}\")\n    public Object detail(@PathVariable Long id) { return null; }\n}");
        GenQualityIssueVO issue = byRule(issues, "Q001");
        assertNotNull(issue, "应命中 @PathVariable 未命名");
        assertEquals("BLOCKER", issue.getSeverity());
        assertEquals(3L, issue.getLineNo());
        assertTrue(issue.getBadSnippet().contains("@PathVariable Long id"));
        assertTrue(issue.getFixSnippet().contains("@PathVariable(\"id\")"));
    }

    @Test
    void detectsMapOfTooManyPairs() {
        List<GenQualityIssueVO> issues = scan("src/Demo.java",
                "class Demo {\n  Object m = Map.of(\"a\",1,\"b\",2,\"c\",3,\"d\",4,\"e\",5,\"f\",6,\"g\",7,\"h\",8,\"i\",9,\"j\",10,\"k\",11);\n}");
        GenQualityIssueVO issue = byRule(issues, "Q003");
        assertNotNull(issue);
        assertEquals("WARN", issue.getSeverity());
        assertEquals(2L, issue.getLineNo());
    }

    @Test
    void detectsSnowflakeIdLeakingAsLong() {
        List<GenQualityIssueVO> issues = scan("src/Demo.java",
                "class Demo {\n  void f(Map<String,Object> result) {\n    result.put(\"id\", goods.getId());\n  }\n}");
        GenQualityIssueVO issue = byRule(issues, "Q004");
        assertNotNull(issue, "Long ID 直出应命中");
        assertEquals("BLOCKER", issue.getSeverity());
        assertEquals(3L, issue.getLineNo());
        assertTrue(issue.getFixSnippet().contains("String.valueOf"));
    }

    @Test
    void allowsSnowflakeIdConvertedToString() {
        List<GenQualityIssueVO> issues = scan("src/Demo.java",
                "class Demo {\n  void f(Map<String,Object> result) {\n    result.put(\"id\", String.valueOf(goods.getId()));\n  }\n}");
        assertEquals(null, byRule(issues, "Q004"));
    }

    @Test
    void detectsHandwrittenSqlMissingDeletedFlag() {
        List<GenQualityIssueVO> issues = scan("src/DemoMapper.java",
                "class DemoMapper {\n  void f() {\n    jdbcTemplate.queryForList(\"SELECT id FROM demo_goods WHERE status = 1\");\n  }\n}");
        GenQualityIssueVO issue = byRule(issues, "Q002");
        assertNotNull(issue);
        assertEquals("BLOCKER", issue.getSeverity());
        assertEquals(3L, issue.getLineNo());
        assertTrue(issue.getFixSnippet().contains("deleted = 0"));
    }

    @Test
    void acceptsHandwrittenSqlWithDeletedFlag() {
        List<GenQualityIssueVO> issues = scan("src/DemoMapper.java",
                "class DemoMapper {\n  void f() {\n    jdbcTemplate.queryForList(\"SELECT id FROM demo_goods WHERE status = 1 AND deleted = 0\");\n  }\n}");
        assertEquals(null, byRule(issues, "Q002"));
    }

    @Test
    void ignoresSqlStringFragmentsThatAreCompletedLater() {
        List<GenQualityIssueVO> issues = scan("src/DemoController.java",
                "class DemoController {\n  String base = \"SELECT id FROM \" + refTable;\n"
                        + "  String sql = base + \" WHERE deleted = 0\";\n}");
        assertEquals(null, byRule(issues, "Q002"), "拼接片段不应误报");
    }

    @Test
    void detectsCreateTableMissingAuditColumns() {
        List<GenQualityIssueVO> issues = scan("db/schema.sql",
                "CREATE TABLE demo (\n  id bigint PRIMARY KEY,\n  name varchar(64)\n);");
        GenQualityIssueVO issue = byRule(issues, "Q039");
        assertNotNull(issue);
        assertEquals("BLOCKER", issue.getSeverity());
        assertEquals(1L, issue.getLineNo());
    }

    @Test
    void detectsHardcodedSecretButIgnoresPlaceholder() {
        List<GenQualityIssueVO> bad = scan("src/Demo.java",
                "class Demo {\n  String password = \"admin123\";\n}");
        assertNotNull(byRule(bad, "Q011"));

        List<GenQualityIssueVO> good = scan("src/Demo.java",
                "class Demo {\n  String password = \"${DB_PASSWORD}\";\n}");
        assertEquals(null, byRule(good, "Q011"));
    }

    @Test
    void detectsVueLineLevelIssues() {
        String vue = "<template>\n  <div v-html=\"row.content\"></div>\n"
                + "  <el-upload :http-request=\"upload\"></el-upload>\n</template>\n"
                + "<script>\nconst id = Number(row.id)\n</script>";
        List<GenQualityIssueVO> issues = scan("src/views/demo/index.vue", vue);
        assertNotNull(byRule(issues, "Q019"), "v-html 应命中");
        assertNotNull(byRule(issues, "Q021"), "el-upload 缺 action 应命中");
        assertNotNull(byRule(issues, "Q020"), "Number(id) 应命中");
    }

    @Test
    void detectsTodoInJavaAndHardcodedLocalhostInJs() {
        List<GenQualityIssueVO> javaIssues = scan("src/Demo.java",
                "class Demo {\n  // TODO 补异常处理\n}\n");
        assertNotNull(byRule(javaIssues, "Q012"));

        List<GenQualityIssueVO> jsIssues = scan("src/api/demo.js",
                "const base = 'http://localhost:8080/api';\n");
        assertNotNull(byRule(jsIssues, "Q038"));
    }

    @Test
    void cleanFileProducesNoIssues() {
        String java = "package demo;\n\npublic class DemoService {\n"
                + "    public String echo(String name) {\n        return name;\n    }\n}\n";
        List<GenQualityIssueVO> issues = scan("src/DemoService.java", java);
        assertTrue(issues.isEmpty(), "干净文件不应产生误报：" + issues);
    }
}
