package ${pkg}.dto;

<#if saveDtoHasBigDecimal>import java.math.BigDecimal;
</#if>
<#if saveDtoHasLocalDateTime>import java.time.LocalDateTime;
</#if>
<#if saveDtoHasLocalDate>import java.time.LocalDate;
</#if>
<#if saveDtoHasImport>${''}
</#if>
<#list validImports as imp>import ${imp};
</#list>
<#if validImports?size gt 0>${''}
</#if>
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * ${comment}保存 DTO（新增/修改）
 */
@Data
public class ${className}SaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

<#list formColumns as c>
<#if c.validJava?has_content>    ${c.validJava}
</#if>    private ${c.javaType} ${c.javaField};
</#list>
<#if isChild && !masterFkInForm>
    /**
     * 所属主表 ID（由主表保存接口自动回填）
     */
    private Long ${masterFkField};
</#if>
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}
