package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Converge 规格对账报告（V1.30 第一期）
 * <p>
 * 生成完成后拿 spec 工件期望清单逐项核对实际产物（gen_table / gen_table_column），
 * 输出"已实现 / 缺失 / 多余"三栏结论；缺口一键追加任务重跑，循环到 CONVERGED 才允许进入验收。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_converge_report")
public class GenConvergeReport extends BaseEntity {

    public static final String STATUS_CONVERGED = "CONVERGED";
    public static final String STATUS_UNCONVERGED = "UNCONVERGED";

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 对账依据的 spec 工件ID（gen_spec_artifact.id）
     */
    private Long artifactId;

    /**
     * spec 工件版本号
     */
    private Integer specVersion;

    /**
     * 已实现项数
     */
    private Integer okCount;

    /**
     * 缺失项数
     */
    private Integer missingCount;

    /**
     * 多余项数
     */
    private Integer extraCount;

    /**
     * 状态:CONVERGED/UNCONVERGED
     */
    private String status;
}
