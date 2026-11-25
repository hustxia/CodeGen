package com.codegen.template;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V6.3 补全契约：退款状态机/金额校验 + pay_code 表级绑定。
 *
 * <p>与 {@link GenTemplateContractTest} 同款思路：不连数据库、不起 Spring，
 * 只读 scaffold-pay 生成源与 gen-templates 模板做「不变量 + 同源」断言。
 * 只写不运行（V6.3 补全批次约定：mvn 由流水线统一执行）。
 */
@DisplayName("V6.3 补全：退款状态机与 pay_code 表级绑定契约")
class PayRefundContractTest {

    private static final Path SCAFFOLD_PAY = Path.of("src/main/resources/scaffold-pay");
    private static final Path TPL_DIR = Path.of("src/main/resources/gen-templates");
    private static final Path TEST_SCHEMA = Path.of("src/main/resources/scaffold-test/pay.sql");

    @BeforeAll
    static void setUp() {
        assertTrue(Files.isDirectory(SCAFFOLD_PAY), "找不到 scaffold-pay 目录：" + SCAFFOLD_PAY.toAbsolutePath());
        GenTemplateModel.setRootPkg("com.codegen");
    }

    // ------------------------------------------------------------------
    // 退款状态机（AbstractPayService.refund 唯一入口）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("退款状态机：终态拒绝/余额校验/条件 UPDATE 幂等/退满联动置 3")
    void refundStateMachineInvariants() {
        String svc = scaffold("AbstractPayService.java");
        // 前置校验：金额 > 0、终态拒绝重退、非已支付拒绝
        assertTrue(svc.contains("\"退款金额必须大于 0\""), "退款金额必须 > 0（负数/0 直接 400）");
        assertTrue(svc.contains("\"订单已全额退款，不可重复退款\""), "重复退款（已退满）必须拒绝");
        assertTrue(svc.contains("\"仅支付成功的订单可退款\""), "待支付/已关闭订单不可退款");
        assertTrue(svc.contains("\"退款金额超过可退余额"), "部分退款累计超额必须拒绝");
        // 幂等核心：pay_status=1 且余额足够才累加（与 markPaid 条件 UPDATE 同款思路）
        assertTrue(svc.contains(".eq(PayOrder::getOutTradeNo, outTradeNo)")
                        && svc.contains(".eq(PayOrder::getPayStatus, PayOrder.STATUS_PAID)"),
                "退款累加必须走条件 UPDATE（pay_status=1），并发退款天然收敛");
        assertTrue(svc.contains(".apply(\"IFNULL(refund_amount, 0) + {0} <= amount\", refundFen)"),
                "余额守卫必须下推到 UPDATE 条件里（应用层校验挡不住并发）");
        // 同 refund_no 重放幂等：直接返回当前状态，不再调渠道
        assertTrue(svc.contains("refundNo.equals(order.getRefundNo())"),
                "同 refund_no 重放必须短路返回（渠道侧 out_refund_no 双保险）");
        // 退满 → 3 终态 + 业务表 pay_status 联动置 3
        assertTrue(svc.contains("PayOrder.STATUS_REFUNDED"), "退满必须置已退款终态");
        assertTrue(svc.contains("linkBusiness(latest, 3)"), "全额退款必须联动业务表 pay_status=3");
        // 回归护栏：markPaid 幂等链路未被退款改动破坏
        assertTrue(svc.contains(".eq(PayOrder::getPayStatus, PayOrder.STATUS_PENDING)"),
                "markPaid 条件 UPDATE（pay_status=0）必须保留");
        assertTrue(svc.contains("linkBusiness(order, 1)"), "支付成功联动业务表 pay_status=1 必须保留");
    }

    @Test
    @DisplayName("退款端点：/pay/refund/{outTradeNo} + 金额元→分转算与精度校验")
    void refundEndpointAndAmountPrecision() {
        String ctrl = scaffold("PayController.java");
        assertTrue(ctrl.contains("@PostMapping(\"/refund/{outTradeNo}\")"), "全局退款端点必须存在");
        assertTrue(ctrl.contains("\"退款金额必填\""), "退款金额缺失必须 400");
        assertTrue(ctrl.contains("req.refundAmount.movePointRight(2).longValueExact()"),
                "退款金额入口是元（BigDecimal），必须转分存储（与下单同款单一精度来源）");
        assertTrue(ctrl.contains("\"退款金额精度非法（最多两位小数）\""), "超过两位小数必须拒绝（longValueExact 抛 ArithmeticException）");
        String itf = scaffold("PaymentService.java");
        assertTrue(itf.contains("PayOrderVO refund(String outTradeNo, long refundFen, String refundNo, String reason);"),
                "PaymentService 接口必须声明退款方法（渠道无关抽象）");
    }

    @Test
    @DisplayName("三渠道退款 hook：mock 空实现 / 微信 outRefundNo / 支付宝 out_request_no")
    void threeChannelRefundHooks() {
        String mock = scaffold("MockPayService.java");
        assertTrue(mock.contains("protected void channelRefund(PayOrder order, long refundFen, String refundNo)"),
                "mock 必须实现 channelRefund hook（余额校验与状态流转在骨架统一收敛）");
        String wx = scaffold("WxPayNativeService.java");
        assertTrue(wx.contains("new RefundService.Builder().config(config()).build()"),
                "微信退款必须复用 RSAAutoCertificateConfig（零新依赖）");
        assertTrue(wx.contains("req.setOutRefundNo(refundNo)"), "微信退款必须传 out_refund_no（渠道幂等键）");
        assertTrue(wx.contains("amount.setRefund(refundFen)"), "微信退款金额必须传分");
        String ali = scaffold("AliPayPageService.java");
        assertTrue(ali.contains("AlipayTradeRefundRequest"), "支付宝退款走 AlipayTradeRefund（沿用现有 RSA2 客户端）");
        assertTrue(ali.contains("out_request_no"), "支付宝退款必须传 out_request_no（渠道幂等键）");
        assertTrue(ali.contains("refund_amount"), "支付宝退款金额字段名必须是 refund_amount（单位元字符串）");
        assertTrue(ali.contains("fenToYuan(refundFen)"), "支付宝金额以本地单的分换算元（单一精度来源）");
    }

    @Test
    @DisplayName("pay_order DDL：退款列与业务索引（主 DDL 与 H2 测试版同源）")
    void payOrderDdlRefundColumns() {
        String ddl = scaffold("pay.sql");
        String h2 = read(TEST_SCHEMA);
        for (String col : List.of("pay_code", "refund_amount", "refund_no", "refund_at", "refund_reason")) {
            assertTrue(ddl.contains(col), "db/pay.sql 缺列：" + col);
            assertTrue(h2.contains(col), "scaffold-test/pay.sql 缺列：" + col);
        }
        assertTrue(ddl.contains("refund_amount  bigint       NOT NULL DEFAULT 0"),
                "refund_amount 必须有 NOT NULL DEFAULT 0（部分退款累计语义）");
        assertTrue(ddl.contains("idx_pay_business (business_type, business_id)"),
                "按业务单查最新支付单必须有 (business_type, business_id) 索引");
        assertTrue(h2.contains("CREATE INDEX IF NOT EXISTS idx_pay_business"),
                "H2 测试版索引要独立语句建（MySQL MODE 下不认表内 KEY）");
    }

    // ------------------------------------------------------------------
    // pay_code 表级绑定（payBizApi.ftl → {Class}PayController）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("payBizApi：绑定表渲染出按单号支付/查状态/退款三端点，列名生成期固化")
    void payBizApiTemplateRendersAndAligns() {
        GenTable table = billTable();
        List<GenTableColumn> cols = billColumns();
        Map<String, Object> model = GenTemplateModel.build(table, cols, null,
                new GenTemplateModel.ProjectConfig("com.demo", "demo", "com.demo", "demo",
                        null, null, null, 1));
        assertTrue(Boolean.TRUE.equals(model.get("hasPayBiz")),
                "pay_code 非空 + pay_enabled=1 + genController=1 + 非从表 → hasPayBiz 必须为真");
        assertEquals("order_no", model.get("payBizNoColumn"), "单号列按命名约定推导");
        assertEquals("amount", model.get("payBizAmountColumn"), "金额列按命名约定推导");

        String out = render("payBizApi", model);
        assertTrue(out.contains("@RequestMapping(\"/bill/pay\")"), "绑定端点挂 /{moduleName}/pay");
        assertTrue(out.contains("BIZ_TABLE = \"demo_bill\""), "业务表名生成期固化（防注入）");
        assertTrue(out.contains("NO_COLUMN = \"order_no\""), "单号列生成期固化（防注入）");
        assertTrue(out.contains("PAY_CODE = \"DEMO_BILL\""), "pay_code 固化进 PAY_CODE 常量");
        assertTrue(out.contains("AMOUNT_COLUMN = \"amount\""), "金额列生成期固化");
        assertTrue(out.contains("@PostMapping(\"/order\")"), "发起支付端点");
        assertTrue(out.contains("@GetMapping(\"/status/{bizNo}\")"), "查状态端点");
        assertTrue(out.contains("@PostMapping(\"/refund/{bizNo}\")"), "退款端点");
        assertTrue(out.contains("paymentService.create("), "发起支付必须走 PaymentService 统一状态机");
        assertTrue(out.contains("paymentService.refund("), "退款必须走 PaymentService 统一状态机");
        assertTrue(out.contains("PayOrder.STATUS_PAID"), "退款前置校验支付单已支付");
        assertTrue(out.contains("BIZ_TABLE,") || out.contains("businessType"),
                "业务表名固化进 business_type → 支付成功联动本表 pay_status（V9.17 改为 BIZ_TABLE 常量直接传参）");
    }

    @Test
    @DisplayName("pay_code 绑定网控：系统未开支付能力包 / 未绑定 → 不生成绑定接口")
    void payCodeBindingGated() {
        // 未绑定 pay_code：即使 pay_enabled=1 也不生成
        GenTable unbound = billTable();
        unbound.setPayCode(null);
        Map<String, Object> m1 = GenTemplateModel.build(unbound, billColumns(), null,
                new GenTemplateModel.ProjectConfig("com.demo", "demo", "com.demo", "demo",
                        null, null, null, 1));
        assertFalse(Boolean.TRUE.equals(m1.get("hasPayBiz")), "未绑定 pay_code 不生成绑定接口");
        // 绑定但系统 pay_enabled=NULL（关）：不生成（防死链，与 genPrint 同思路）
        GenTable bound = billTable();
        Map<String, Object> m2 = GenTemplateModel.build(bound, billColumns());
        assertFalse(Boolean.TRUE.equals(m2.get("hasPayBiz")), "系统未开支付能力包不生成绑定接口");
    }

    @Test
    @DisplayName("前端同源：payApi 导出 refundPayOrder，payOrderPage 退款入口引用它")
    void frontendRefundEntryAligns() {
        String api = render("payApi", Map.of());
        assertTrue(api.contains("export function refundPayOrder(outTradeNo, data)"), "payApi 必须导出退款函数");
        assertTrue(api.contains("url: '/pay/refund/' + outTradeNo"), "退款 URL 必须与后端 /pay/refund/{outTradeNo} 对齐");
        String page = render("payOrderPage", Map.of());
        assertTrue(page.contains("import { payOrderPage, getPayOrder, closePayOrder, refundPayOrder } from '@/api/pay'"),
                "支付记录页必须 import refundPayOrder（漏 import 渲染即 ReferenceError）");
        assertTrue(page.contains("openRefund(row)"), "退款入口必须存在");
        assertTrue(page.contains("refundDlg.maxYuan"), "退款弹窗必须以剩余可退金额封顶（部分退款语义）");
        // 空模型渲染兜底（renderPayShared 异常即静默丢能力包的坑，见 GenTemplateContractTest）
        assertTrue(page.length() > 20 && api.length() > 20, "空模型渲染产物不应为空");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 绑定 pay_code 的演示表：demo_bill（单号列 order_no + 金额列 amount） */
    private static GenTable billTable() {
        GenTable t = new GenTable();
        t.setTableName("demo_bill");
        t.setTableComment("演示账单");
        t.setClassName("DemoBill");
        t.setModuleName("bill");
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setGatewayPrefix("demo");
        t.setAuthor("codegen");
        t.setGenEntity(1);
        t.setGenMapper(1);
        t.setGenService(1);
        t.setGenController(1);
        t.setGenFrontend(1);
        t.setPayCode("DEMO_BILL");
        return t;
    }

    private static List<GenTableColumn> billColumns() {
        List<GenTableColumn> cols = new ArrayList<>();
        GenTableColumn id = col("id", "id", "Long", 1);
        cols.add(id);
        cols.add(col("order_no", "orderNo", "String", 2));
        GenTableColumn amount = col("amount", "amount", "BigDecimal", 3);
        amount.setColumnType("decimal(10,2)");
        cols.add(amount);
        return cols;
    }

    private static GenTableColumn col(String columnName, String javaField, String javaType, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(columnName);
        c.setColumnComment(columnName);
        c.setColumnType("varchar(50)");
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(sort == 1 ? 1 : 0);
        c.setIsRequired(0);
        c.setIsQuery(0);
        c.setQueryType("EQ");
        c.setIsList(1);
        c.setIsForm(1);
        c.setSort(sort);
        c.setHtmlType("input");
        return c;
    }

    private static String scaffold(String file) {
        return read(SCAFFOLD_PAY.resolve(file));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String render(String tplCode, Map<String, Object> model) {
        String content = read(TPL_DIR.resolve(tplCode + ".ftl"));
        try {
            return GenTemplateEngine.renderString(content, model);
        } catch (Exception e) {
            fail("渲染模板 " + tplCode + " 失败：" + e.getMessage());
            return "";
        }
    }
}
