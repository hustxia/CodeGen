package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V9.22 行业范式包元数据：registry 的 JSON 只存表结构，
 * 命中度/外接系统/去重边界这些"交付判断"放这里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_industry_paradigm")
@Schema(description = "行业范式包元数据")
public class GenIndustryParadigm extends BaseEntity {

    @Schema(description = "模板包key(对应industry-templates/*.json)")
    private String packKey;

    @Schema(description = "行业")
    private String industry;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "范式版本")
    private String version;

    @Schema(description = "生成器命中度(0-100)")
    private Integer hitRate;

    @Schema(description = "直建表数")
    private Integer directTables;

    @Schema(description = "必须外接的系统数")
    private Integer externalSystems;

    @Schema(description = "去重说明")
    private String dedupNote;

    @Schema(description = "不承诺边界")
    private String boundaryNote;

    @Schema(description = "业务主线")
    private String coreChain;

    @Schema(description = "展示排序")
    private Integer sortNo;

    @Schema(description = "0禁用 1启用")
    private Integer enabled;
}
