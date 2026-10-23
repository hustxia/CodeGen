package com.codegen.util;

import java.util.List;

/**
 * 生成菜单的「业务域」分组：把「系统 → 分组 → 页面」中间那层从表名/表注释推导出来。
 *
 * <p>推导顺序：表注释（中文，业务语义最准）→ 表名（英文关键字，兜底）。
 * 规则按声明顺序匹配，先命中先用；都不命中归「其他」，保证任何表都有归属，
 * 菜单不会因为某个新表没有映射就退化成平铺。</p>
 */
public final class GenMenuGroup {

    /**
     * @param code  路由片段用（英文、小写、无空格）
     * @param label 菜单显示名
     */
    public record Group(String code, String label) {
    }

    private record Rule(Group group, List<String> keywords) {
    }

    private static final Group OTHER = new Group("other", "其他");

    /**
     * 规则顺序有讲究：
     * 1. 出库/入库 必须排在「库存」之前，否则「入库通知」会被库存抢走；
     * 2. 客户/供应商 排在「基础资料」之前，避免「客户类型」掉进基础资料；
     * 3. 系统类（日志/字典/流程）排最后，避免「业务字典」这类词误判。
     */
    private static final List<Rule> RULES = List.of(
            new Rule(new Group("outbound", "出库管理"),
                    List.of("出库", "发货", "拣货", "outbound", "pick")),
            new Rule(new Group("inbound", "入库管理"),
                    List.of("入库", "收货", "收料", "到货", "上架", "inbound", "receiving", "receipt",
                            "putaway", "appointment")),
            new Rule(new Group("stock", "库存管理"),
                    List.of("库存", "盘点", "移库", "调拨", "库龄", "inventory", "stocktake", "transit")),
            new Rule(new Group("customer", "客户管理"),
                    List.of("客户", "供应商", "第三方", "customer", "supplier", "third_party")),
            new Rule(new Group("product", "产品管理"),
                    List.of("产品", "商品", "物料", "批次", "product", "goods", "batch")),
            new Rule(new Group("charge", "计费管理"),
                    List.of("计费", "费用", "费率", "账单", "结算", "支付", "报价", "charge", "fee",
                            "billing", "cost", "payment", "settlement", "price", "rate")),
            new Rule(new Group("dispatch", "调度作业"),
                    List.of("调度", "派车", "装车", "车辆", "月台", "运输", "任务", "dispatch", "loading",
                            "vehicle", "dock", "transport", "task")),
            new Rule(new Group("order", "订单管理"),
                    List.of("订单", "单据", "order")),
            new Rule(new Group("base", "基础资料"),
                    List.of("仓库", "库位", "单位", "类型", "状态", "参数", "编码", "地址", "质检",
                            "属性", "阶段", "warehouse", "location", "unit", "type", "param", "code",
                            "address", "measure", "qc", "attr", "phase", "rfid")),
            new Rule(new Group("flow", "流程管理"),
                    List.of("流程", "审批", "flow", "approve")),
            new Rule(new Group("system", "系统管理"),
                    List.of("日志", "字典", "用户", "角色", "菜单", "主题", "log", "dict", "user", "role",
                            "menu", "theme"))
    );

    private GenMenuGroup() {
    }

    /**
     * 推导一张表的业务域。
     *
     * @param tableName    物理表名，如 wms_abnormal_outbound_detail
     * @param tableComment 表注释，如 WMS异常出库通知明细
     */
    public static Group of(String tableName, String tableComment) {
        String comment = tableComment == null ? "" : tableComment;
        for (Rule rule : RULES) {
            if (hits(comment, rule.keywords())) {
                return rule.group();
            }
        }
        String table = tableName == null ? "" : tableName.toLowerCase();
        for (Rule rule : RULES) {
            if (hits(table, rule.keywords())) {
                return rule.group();
            }
        }
        return OTHER;
    }

    private static boolean hits(String text, List<String> keywords) {
        if (text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
