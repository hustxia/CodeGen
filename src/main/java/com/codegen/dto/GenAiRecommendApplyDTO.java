package com.codegen.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V7.0 AI 建议应用：前端勾选项（只传「选了哪些」，建议值以服务端产物为准——
 * 客户端传值不可信，防止伪造建议值写库）。
 */
@Data
public class GenAiRecommendApplyDTO {

    /**
     * 表级入口（V7.3 P0-3）专用：推荐时返回的产物 token，服务端按它读回建议值。
     * 流水线入口不传（按 pipelineId 取产物）。
     */
    private String token;

    private List<TablePick> tables = new ArrayList<>();

    /** 勾选的菜单 moduleName 列表（应用 = 成员表 module_name 归到该模块） */
    private List<String> menus = new ArrayList<>();

    @Data
    public static class TablePick {
        private String tableName;

        /** 勾选的表级字段：moduleName/treeParentColumn/treeLabelColumn/masterTable/relationType */
        private List<String> fields = new ArrayList<>();

        private List<ColumnPick> columns = new ArrayList<>();
    }

    @Data
    public static class ColumnPick {
        private String columnName;

        /** 勾选的列级字段：isQuery/queryType/isList/isForm/htmlType/dictType/sort */
        private List<String> fields = new ArrayList<>();
    }
}
