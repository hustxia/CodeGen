package com.codegen.support;

import com.codegen.vo.GenPipelineAiVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.7 两段式骨架生成第二段：命名语义 → 类型/主键规则补全。
 *
 * <p>骨架模式 AI 只列字段名（输出量降 60%+），类型全靠本规则表。
 * 这里的断言锁住口径表（见 TableSkeletonExpander 类注释）——
 * 规则变了必须显式改测试，防止「顺手改一行」悄悄改掉全站建表行为。
 */
class TableSkeletonExpanderTest {

    @Test
    void inferType_idFamily() {
        assertEquals("bigint", TableSkeletonExpander.inferType("id"));
        assertEquals("bigint", TableSkeletonExpander.inferType("order_id"));
        assertEquals("bigint", TableSkeletonExpander.inferType("Goods_ID"));
    }

    @Test
    void inferType_moneyAndRate() {
        assertEquals("decimal(12,2)", TableSkeletonExpander.inferType("pay_amount"));
        assertEquals("decimal(12,2)", TableSkeletonExpander.inferType("sale_price"));
        assertEquals("decimal(12,2)", TableSkeletonExpander.inferType("balance"));
        assertEquals("decimal(5,2)", TableSkeletonExpander.inferType("win_rate"));
        assertEquals("decimal(10,6)", TableSkeletonExpander.inferType("lat"));
        assertEquals("decimal(10,6)", TableSkeletonExpander.inferType("longitude"));
    }

    @Test
    void inferType_textAndContact() {
        assertEquals("text", TableSkeletonExpander.inferType("apply_reason"));
        assertEquals("text", TableSkeletonExpander.inferType("article_content"));
        assertEquals("text", TableSkeletonExpander.inferType("receiver_address"));
        assertEquals("varchar(128)", TableSkeletonExpander.inferType("email"));
        assertEquals("varchar(20)", TableSkeletonExpander.inferType("contact_phone"));
        assertEquals("varchar(512)", TableSkeletonExpander.inferType("cover_image"));
        assertEquals("varchar(512)", TableSkeletonExpander.inferType("logo_url"));
    }

    @Test
    void inferType_codeAndName() {
        assertEquals("varchar(64)", TableSkeletonExpander.inferType("order_no"));
        assertEquals("varchar(64)", TableSkeletonExpander.inferType("brand_code"));
        assertEquals("varchar(128)", TableSkeletonExpander.inferType("goods_name"));
        assertEquals("varchar(128)", TableSkeletonExpander.inferType("title"));
    }

    @Test
    void inferType_flagAndNumber() {
        assertEquals("tinyint", TableSkeletonExpander.inferType("status"));
        assertEquals("tinyint", TableSkeletonExpander.inferType("audit_status"));
        assertEquals("tinyint", TableSkeletonExpander.inferType("is_main_flag"));
        assertEquals("tinyint", TableSkeletonExpander.inferType("checked_flag"));
        assertEquals("int", TableSkeletonExpander.inferType("stock_num"));
        assertEquals("int", TableSkeletonExpander.inferType("view_count"));
        assertEquals("int", TableSkeletonExpander.inferType("quantity"));
        assertEquals("int", TableSkeletonExpander.inferType("priority"));
    }

    @Test
    void inferType_timeAndDate() {
        assertEquals("datetime", TableSkeletonExpander.inferType("pay_time"));
        assertEquals("datetime", TableSkeletonExpander.inferType("created_at"));
        assertEquals("date", TableSkeletonExpander.inferType("sign_date"));
        assertEquals("date", TableSkeletonExpander.inferType("birthday"));
    }

    @Test
    void inferType_accountNotCount() {
        // account 结尾曾被 count 规则误判成 int
        assertEquals("varchar(64)", TableSkeletonExpander.inferType("user_account"));
    }

    @Test
    void inferType_fallbackVarchar255() {
        assertEquals("varchar(255)", TableSkeletonExpander.inferType("biz_field"));
        assertEquals("varchar(255)", TableSkeletonExpander.inferType(""));
    }

    @Test
    void expand_addsIdPkAndKeepsOrder() {
        GenPipelineAiVO.TableModel skeleton = skeleton("goods",
                col("goods_name", "商品名称"), col("goods_price", "价格"));

        List<GenPipelineAiVO.TableModel> out = TableSkeletonExpander.expand(List.of(skeleton));

        assertEquals(1, out.size());
        GenPipelineAiVO.TableModel t = out.get(0);
        assertEquals("goods", t.getTableName());
        assertEquals(3, t.getColumns().size());
        GenPipelineAiVO.Column id = t.getColumns().get(0);
        assertEquals("id", id.getName());
        assertEquals("bigint", id.getType());
        assertTrue(id.isPk(), "骨架模式主键统一由规则补在首位");
        assertEquals("varchar(128)", t.getColumns().get(1).getType());
        assertEquals("decimal(12,2)", t.getColumns().get(2).getType());
        assertEquals("商品名称", t.getColumns().get(1).getComment(), "注释原样保留");
    }

    @Test
    void expand_normalizesNameAndDropsDuplicateColumns() {
        GenPipelineAiVO.TableModel skeleton = skeleton("Goods ",
                col("Goods-Name", "商品名称"), col("goods_name", "重名列"), col("__stock_num__", "库存"));

        List<GenPipelineAiVO.TableModel> out = TableSkeletonExpander.expand(List.of(skeleton));

        GenPipelineAiVO.TableModel t = out.get(0);
        assertEquals("goods", t.getTableName());
        // id + goods_name + stock_num = 3，重名列被丢弃
        assertEquals(3, t.getColumns().size());
        assertEquals("goods_name", t.getColumns().get(1).getName());
        assertEquals("stock_num", t.getColumns().get(2).getName());
    }

    @Test
    void expand_dropsBlankTablesAndColumnsAndModelGivenId() {
        GenPipelineAiVO.TableModel blank = new GenPipelineAiVO.TableModel();
        GenPipelineAiVO.TableModel skeleton = skeleton("order_info",
                col("", "空列名"), col("id", "模型自带的 id"), col("order_no", "订单号"));

        List<GenPipelineAiVO.TableModel> out = TableSkeletonExpander.expand(List.of(blank, skeleton));

        assertEquals(1, out.size(), "无名表被丢弃");
        // id(规则补) + order_no：模型给的 id 列被规则主键接管，空列名被丢
        assertEquals(2, out.get(0).getColumns().size());
        assertEquals("id", out.get(0).getColumns().get(0).getName());
        assertEquals("order_no", out.get(0).getColumns().get(1).getName());
    }

    @Test
    void expand_trustsModelGivenType() {
        GenPipelineAiVO.TableModel skeleton = skeleton("t", col("biz_col", "业务列"));
        skeleton.getColumns().get(0).setType("varchar(99)");

        List<GenPipelineAiVO.TableModel> out = TableSkeletonExpander.expand(List.of(skeleton));

        assertEquals("varchar(99)", out.get(0).getColumns().get(1).getType(),
                "模型已给类型时信任模型值，规则只补缺失");
    }

    @Test
    void expand_nullAndEmptyInput() {
        assertTrue(TableSkeletonExpander.expand(null).isEmpty());
        assertTrue(TableSkeletonExpander.expand(List.of()).isEmpty());
    }

    // ---- 工具 ----

    private GenPipelineAiVO.TableModel skeleton(String name, GenPipelineAiVO.Column... cols) {
        GenPipelineAiVO.TableModel t = new GenPipelineAiVO.TableModel();
        t.setTableName(name);
        t.setTableComment("注释");
        t.getColumns().addAll(List.of(cols));
        return t;
    }

    private GenPipelineAiVO.Column col(String name, String comment) {
        GenPipelineAiVO.Column c = new GenPipelineAiVO.Column();
        c.setName(name);
        c.setComment(comment);
        return c;
    }
}
