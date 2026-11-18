package com.codegen.service.impl;

import com.codegen.vo.GenPipelineAiVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.7 骨架建模解析：fields 支持对象与纯字符串两种形态，
 * 解析结果必须先过 TableSkeletonExpander 补全再出 VO（出参与完整建模同形）。
 */
class AiSkeletonModelTest {

    private final AiModelServiceImpl service = new AiModelServiceImpl();

    @Test
    void parseSkeleton_readsObjectFields() {
        String json = """
                {"tables":[{"tableName":"pet_pending","tableComment":"待领养宠物",
                  "fields":[{"name":"pet_name","comment":"宠物名"},{"name":"pet_age","comment":"年龄"}]}]}
                """;

        List<GenPipelineAiVO.TableModel> out = service.parseSkeleton(json);

        assertEquals(1, out.size());
        assertEquals("pet_pending", out.get(0).getTableName());
        assertEquals(2, out.get(0).getColumns().size());
        assertEquals("pet_name", out.get(0).getColumns().get(0).getName());
        assertEquals("宠物名", out.get(0).getColumns().get(0).getComment());
    }

    @Test
    void parseSkeleton_acceptsPlainStringFields() {
        String json = """
                {"tables":[{"tableName":"t1","tableComment":"表一",
                  "fields":["order_no 订单号","remark"]}]}
                """;

        List<GenPipelineAiVO.TableModel> out = service.parseSkeleton(json);

        assertEquals(2, out.get(0).getColumns().size());
        assertEquals("order_no", out.get(0).getColumns().get(0).getName());
        assertEquals("订单号", out.get(0).getColumns().get(0).getComment(), "「字段名 注释」同串形态自动拆分");
        assertEquals("remark", out.get(0).getColumns().get(1).getName());
        assertEquals("", out.get(0).getColumns().get(1).getComment());
    }

    @Test
    void parseSkeleton_fallsBackToColumnsKey() {
        String json = """
                {"tables":[{"tableName":"t1","tableComment":"表一",
                  "columns":[{"name":"goods_name","comment":"商品名"}]}]}
                """;

        assertEquals(1, service.parseSkeleton(json).get(0).getColumns().size());
    }

    @Test
    void parseSkeleton_rejectsEmptyOrBroken() {
        assertTrue(assertParse("{\"tables\":[]}"));
        assertTrue(assertParse("not a json"));
    }

    private boolean assertParse(String json) {
        try {
            service.parseSkeleton(json);
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
