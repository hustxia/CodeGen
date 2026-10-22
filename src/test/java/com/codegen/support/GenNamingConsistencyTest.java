package com.codegen.support;

import com.codegen.entity.GenTable;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 权限点/字典编码的「三方一致性」测试。
 *
 * <p>背景：权限点前缀原本有三份实现——菜单注册（GenMenuServiceImpl）、
 * 模板模型（GenTemplateModel）、生成自检（GenSelfCheckServiceImpl）。
 * V2.0 已经因此出过一次事故：模板里是 {@code mkt2_x:add}，菜单里是 {@code MKT2_x:add}，
 * 页面上按钮全在、点了全 403，日志里连个错都没有。
 *
 * <p>V3.1 把规则收敛到 {@link GenNaming} 之后，本测试守两件事：
 * <ol>
 *   <li>模板模型输出的 permsPrefix 必须等于 GenNaming 的结论（菜单/自检调的是同一个方法，
 *       所以这一条成立即三方成立）；</li>
 *   <li>大小写、空白、特殊字符这类「看上去等价」的输入，结果必须完全一致。</li>
 * </ol>
 */
@DisplayName("权限点与字典编码：三方一致性")
class GenNamingConsistencyTest {

    @ParameterizedTest(name = "系统编码=[{0}] 表名={1} → 权限前缀 {2}")
    @CsvSource({
            // 常规
            "mkt,      sys_flow_task, mkt_sys_flow_task",
            // 大写：菜单/自检过去都会 toLowerCase，模板模型没做，正是事故点
            "MKT,      sys_flow_task, mkt_sys_flow_task",
            // 前后空白
            "'  mkt  ', sys_flow_task, mkt_sys_flow_task",
            // 未绑定系统：不加前缀，存量表行为不变
            "'',       sys_flow_task, sys_flow_task"
    })
    @DisplayName("权限前缀归一化")
    void permsPrefix(String projectCode, String tableName, String expected) {
        assertEquals(expected, GenNaming.permsPrefix(projectCode, tableName));
    }

    @Test
    @DisplayName("未绑定系统（projectCode 为 null）不加前缀")
    void permsPrefixWithoutProject() {
        assertEquals("sys_user", GenNaming.permsPrefix(null, "sys_user"));
    }

    @Test
    @DisplayName("模板模型里的 permsPrefix 必须等于 GenNaming 的结论")
    void templateMatchesNaming() {
        GenTable table = new GenTable();
        table.setTableName("sys_flow_task");
        table.setClassName("SysFlowTask");
        table.setModuleName("flowTask");

        // 故意用大写 + 空白的系统编码：这是最容易被漏掉的一类输入
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                "com.mkt", "mkt", "com.mkt", "  MKT  ");

        Map<String, Object> model = GenTemplateModel.build(table, List.of(), null, project);

        assertEquals(GenNaming.permsPrefix("  MKT  ", "sys_flow_task"), model.get("permsPrefix"));
        assertEquals("mkt_sys_flow_task", model.get("permsPrefix"));
    }

    @Test
    @DisplayName("两个系统同名表：权限前缀必须不同，否则按钮级鉴权会串")
    void differentProjectsDoNotCollide() {
        String shop = GenNaming.permsPrefix("shop", "sys_user");
        String mkt = GenNaming.permsPrefix("mkt", "sys_user");
        assertNotEquals(shop, mkt);
        assertEquals("shop_sys_user", shop);
        assertEquals("mkt_sys_user", mkt);
    }

    @Test
    @DisplayName("字典编码带系统前缀，跨系统同名表同名列不撞")
    void dictCodeIsolation() {
        assertEquals("mkt2_sys_user_status", GenNaming.dictCode("mkt2", "sys_user", "status"));
        assertEquals("shop_sys_user_status", GenNaming.dictCode("SHOP", "SYS_USER", "STATUS"));
        // 未绑定系统退化为旧规则
        assertEquals("sys_user_status", GenNaming.dictCode(null, "sys_user", "status"));
    }

    @Test
    @DisplayName("字典编码把非法字符换成下划线，避免生成出无法解析的标识符")
    void dictCodeSanitizes() {
        assertEquals("mkt_my_table_my_col", GenNaming.dictCode("mkt", "my-table", "my col"));
    }
}
