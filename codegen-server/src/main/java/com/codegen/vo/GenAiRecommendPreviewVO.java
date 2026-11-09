package com.codegen.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V7.0 AI 建议预览：建议产物与现值的 diff 视图（只装有差异的项）。
 *
 * <p>manual = 现值偏离规则默认（V6.6 推断/导入默认），即「已手改」——
 * 前端默认不勾选，防止 AI 建议静默覆盖人工调整。
 */
@Data
public class GenAiRecommendPreviewVO {

    /** 产物是否存在（ai_recommend 步成功才有） */
    private boolean exists;

    /**
     * 表级入口（V7.3 P0-3）专用：本次建议的产物 token。
     * 应用时原样回传，服务端按 token 读回产物再写库——建议值仍以服务端产物为准。
     * 流水线入口为 null（应用按 pipelineId 取产物）。
     */
    private String token;

    private List<TablePreview> tables = new ArrayList<>();

    /** 菜单组织建议（应用 = 成员表 moduleName 归到该模块；目录名/图标为参考） */
    private List<GenAiRecommendVO.MenuSuggest> menus = new ArrayList<>();

    /** 生成期被白名单丢弃的项（含原因） */
    private List<String> drops = new ArrayList<>();

    @Data
    public static class TablePreview {
        private Long tableId;
        private String tableName;
        private String tableComment;
        /** 表级字段 diff（moduleName/treeParentColumn/treeLabelColumn/masterTable/relationType） */
        private List<FieldDiff> tableFields = new ArrayList<>();
        private List<ColumnPreview> columns = new ArrayList<>();
    }

    @Data
    public static class ColumnPreview {
        private String columnName;
        private String columnComment;
        private List<FieldDiff> fields = new ArrayList<>();
    }

    @Data
    public static class FieldDiff {
        /** 字段 key：isQuery/queryType/isList/isForm/htmlType/dictType/sort/moduleName/... */
        private String field;
        /** 现值（masterTable 显示主表名；null 表示未配置） */
        private Object current;
        /** 建议值 */
        private Object suggested;
        /** 已手改：现值偏离规则默认 → 默认不勾选 */
        private boolean manual;
    }
}
