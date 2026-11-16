package com.codegen.vo;

import com.codegen.vo.GenPipelineAiVO.TableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V9.5 AI 推荐建表结果。
 *
 * <p>包含：扁平表结构（供前端编辑器增删改）、模块 → 表名映射（供微服务拆分）、
 * 关系视图 {@link TableRelation}（单表/主从/树形角色判定，供生成模板路由）。
 */
@Data
@Schema(description = "V9.5 AI 推荐建表结果")
public class GenAiBuildPlanVO {

    @Schema(description = "计划来源：ai=AI 生成 template=行业模板克隆（V9.7）", example = "ai")
    private String source = "ai";

    @Schema(description = "补表目标系统 ID（scope=supplement 时回显）")
    private Long projectId;

    @Schema(description = "目标系统已有表数量（补表差集基数）")
    private int existingTableCount;

    @Schema(description = "被排除的表数量（已有表 + 推荐结果内重名）")
    private int excludedCount;

    @Schema(description = "被排除的表名及原因（供前端提示，不落库）")
    private List<String> excludedTables = new ArrayList<>();

    @Schema(description = "候选表结构（扁平，前端可增删改）")
    private List<TableModel> tables = new ArrayList<>();

    @Schema(description = "模块名 → 该模块包含的表名清单（微服务拆分依据）")
    private Map<String, List<String>> moduleTableNames = new LinkedHashMap<>();

    @Schema(description = "关系视图：每个表的角色（single/master/detail/tree）")
    private List<TableRelation> relations = new ArrayList<>();

    @Schema(description = "规模：small / large / huge")
    private String scale;

    @Schema(description = "部署形态：mono / micro")
    private String deploy;

    @Schema(description = "表总数")
    private int tableCount;

    @Schema(description = "主表数（master）")
    private int masterCount;

    @Schema(description = "从表数（detail）")
    private int detailCount;

    @Schema(description = "单表数（single）")
    private int singleCount;

    @Schema(description = "树形表数（tree，自关联）")
    private int treeCount;

    @Schema(description = "模块数")
    private int moduleCount;

    @Schema(description = "AI 模块拆分与系统关联度偏低告警")
    private boolean relevanceWarning = false;

    @Schema(description = "关联度告警说明")
    private String relevanceTip = "";

    @Data
    @Schema(description = "单表关系角色")
    public static class TableRelation {

        @Schema(description = "表名")
        private String name;

        @Schema(description = "角色：single / master / detail / tree", example = "master")
        private String role;

        @Schema(description = "主表名（role=detail 时指向的主表）")
        private String master;

        @Schema(description = "从表明细（role=master 时其下挂的从表）")
        private List<String> details = new ArrayList<>();
    }
}
