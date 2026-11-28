package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SDD 规格工件（V1.30 第一期）
 * <p>
 * 工件链：CONSTITUTION(基本法) → SPEC(模块规格) → PLAN(技术方案) → TABLES(表设计) → TASKS(生成任务)。
 * 保存即新增版本快照，不覆盖历史；converge 对账以 CONFIRMED 的最新 SPEC 工件为依据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_spec_artifact")
public class GenSpecArtifact extends BaseEntity {

    /** 工件类型常量 */
    public static final String TYPE_CONSTITUTION = "CONSTITUTION";
    public static final String TYPE_SPEC = "SPEC";
    public static final String TYPE_PLAN = "PLAN";
    public static final String TYPE_TABLES = "TABLES";
    public static final String TYPE_TASKS = "TASKS";

    /** 状态常量 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    /**
     * 项目ID（sys_project.id）
     */
    private Long projectId;

    /**
     * 工件类型:CONSTITUTION/SPEC/PLAN/TABLES/TASKS
     */
    private String artifactType;

    /**
     * 版本号（同项目同类型递增，保存即 +1）
     */
    private Integer version;

    /**
     * 标题
     */
    private String title;

    /**
     * 结构化元数据 JSON（对应 Markdown front matter：domain/module/template/guardrailPassed/nonGoals...）
     */
    private String yamlMeta;

    /**
     * Markdown 正文（人与 Agent 共读）
     */
    private String contentMd;

    /**
     * 期望清单 JSON（converge 比对数据源）：
     * <pre>
     * {
     *   "tables": [ {"name":"cm_event","fields":["status","grid_id"]} ],
     *   "enums":  [ "cg_event_status", "cg_urgent_level" ]
     * }
     * </pre>
     */
    private String structuredJson;

    /**
     * 状态:DRAFT/CONFIRMED
     */
    private String status;
}
