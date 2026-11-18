package com.codegen.template;

import com.codegen.util.GenTemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL 保留字列防回归： village_affairs 的 desc/rank/condition/usage 列
 * 曾让 5 个页面全部 500「服务异常」——MyBatis-Plus 默认按列名拼 SQL，
 * 不带反引号时会触发 ERROR 1064。
 *
 * <p>本测试直接渲染 entity.ftl，确保保留字列自动输出 @TableField("`col`")，
 * 普通列不受影响，避免只改了模板字符串却渲染失败。
 */
class GenReservedWordColumnTest {

    private static final String TEMPLATE = "src/main/resources/gen-templates/entity.ftl";

    private String renderEntity(String... columnNames) throws Exception {
        String content = Files.readString(Path.of(TEMPLATE), StandardCharsets.UTF_8);
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String name : columnNames) {
            Map<String, Object> col = new HashMap<>();
            col.put("dbField", name);
            col.put("javaField", name);
            col.put("javaType", "String");
            col.put("columnComment", "测试字段");
            columns.add(col);
        }
        Map<String, Object> model = new HashMap<>();
        model.put("pkg", "com.demo.entity");
        model.put("rootPkg", "com.demo");
        model.put("className", "Demo");
        model.put("tableName", "demo_table");
        model.put("comment", "演示表");
        model.put("columns", columns);
        model.put("entityHasBigDecimal", false);
        model.put("entityHasLocalDateTime", false);
        model.put("entityHasLocalDate", false);
        model.put("entityHasLocalTime", false);
        model.put("entityHasImport", false);
        return GenTemplateEngine.renderString(content, model);
    }

    @Test
    @DisplayName("实体模板：保留字列自动加反引号 @TableField")
    void reservedWordsAreQuoted() throws Exception {
        String out = renderEntity("desc", "rank", "condition", "usage", "title");
        assertTrue(out.contains("@TableField(\"`desc`\")"), "desc 必须加反引号，实际输出：\n" + out);
        assertTrue(out.contains("@TableField(\"`rank`\")"), "rank 必须加反引号");
        assertTrue(out.contains("@TableField(\"`condition`\")"), "condition 必须加反引号");
        assertTrue(out.contains("@TableField(\"`usage`\")"), "usage 必须加反引号");
        assertTrue(out.contains("import com.baomidou.mybatisplus.annotation.TableField;"),
                "有保留字列时必须导入 TableField");
        assertTrue(out.contains("private String title;"), "普通列仍要正常生成");
        assertFalse(out.substring(out.indexOf("private String title;") - 80, out.indexOf("private String title;"))
                        .contains("@TableField"), "普通列不应加 @TableField");
    }

    @Test
    @DisplayName("实体模板：无保留字列时不导入 TableField")
    void noReservedWordKeepsEntityClean() throws Exception {
        String out = renderEntity("title", "status", "create_time");
        assertFalse(out.contains("TableField"), "无保留字列时不应出现 TableField，实际输出：\n" + out);
    }

    @Test
    @DisplayName("实体模板：保留字清单覆盖 MySQL 8 全部 261 词")
    void reservedWordListIsComplete() throws Exception {
        String tpl = Files.readString(Path.of(TEMPLATE), StandardCharsets.UTF_8);
        String list = tpl.split("<#assign mysqlReservedWords = \\[", 2)[1].split("]>", 2)[0];
        int count = list.split("\", \"").length;
        assertTrue(count >= 260, "保留字清单应覆盖 MySQL 8 全部 261 词，实际 " + count);
        for (String word : List.of("desc", "rank", "condition", "usage", "order", "group", "select", "update")) {
            assertTrue(list.contains("\"" + word + "\""), "清单缺少保留字 " + word);
        }
    }
}
