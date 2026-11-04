package com.codegen.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V7.0 AI 页面与菜单推荐产物：三层建议（列级 / 表级 / 菜单）+ 丢弃清单。
 *
 * <p>建议暂存于 pipeline 步骤产物（ai_recommend 步），不落 gen_table；
 * 用户在前端 diff 预览勾选后，应用接口才写库。
 */
@Data
public class GenAiRecommendVO {

    /** 表级建议列表（含各表列级建议） */
    private List<TableSuggest> tables = new ArrayList<>();

    /** 菜单组织建议 */
    private List<MenuSuggest> menus = new ArrayList<>();

    /** 白名单校验丢弃项（原因可读，前端展示用） */
    private List<String> drops = new ArrayList<>();

    @Data
    public static class TableSuggest {
        /** 物理表名（唯一键，应用时按名定位 gen_table 行） */
        private String tableName;

        /** 模块 key（英文 camelCase 或下划线风格） */
        private String moduleName;

        /** 树形父级列 */
        private String treeParentColumn;

        /** 树节点显示列 */
        private String treeLabelColumn;

        /** 主子：所属主表物理表名（应用时转成 gen_table.id） */
        private String masterTableName;

        /** 关系类型：middle=多对多中间表，空为普通表 */
        private String relationType;

        /** 列级建议 */
        private List<ColumnSuggest> columns = new ArrayList<>();
    }

    @Data
    public static class ColumnSuggest {
        private String columnName;

        /** 0/1；null 表示 AI 未给建议（预览不展示该字段） */
        private Integer isQuery;

        /** EQ/LIKE/GE/LE */
        private String queryType;

        private Integer isList;

        private Integer isForm;

        /** input/textarea/number/select/radio/switch/date/datetime/image/file/multiImage */
        private String htmlType;

        /** 字典编码，空串/null 表示不挂字典 */
        private String dictType;

        private Integer sort;
    }

    @Data
    public static class MenuSuggest {
        /** 模块 key（与 table 层 moduleName 对齐） */
        private String moduleName;

        /** 菜单目录显示名 */
        private String dirLabel;

        /** 图标候选（element icon class），仅作建议 */
        private String icon;

        /** 目录排序 */
        private Integer sort;

        /** 该目录下挂的表 */
        private List<String> tables = new ArrayList<>();
    }
}
