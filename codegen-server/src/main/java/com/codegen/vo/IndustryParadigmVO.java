package com.codegen.vo;

import com.codegen.entity.GenIndustryParadigm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * V9.22 范式包 VO：元数据全字段 + registry 实算的模块/表数。
 * 列表不返回 modules（避免 250+ 包的明细传输），详情才填充。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "行业范式包")
public class IndustryParadigmVO extends GenIndustryParadigm {

    @Schema(description = "范式包展示名（来自registry模板，元数据表不重复存）")
    private String name;

    @Schema(description = "registry 实际模块数")
    private Integer moduleCount;

    @Schema(description = "registry 实际表数")
    private Integer tableCount;

    @Schema(description = "是否存在对应模板包（元数据有但JSON缺失时为false）")
    private Boolean templateAvailable;

    @Schema(description = "模块与表清单（仅详情返回）")
    private List<GenAiTemplateVO.Module> modules = new ArrayList<>();
}
