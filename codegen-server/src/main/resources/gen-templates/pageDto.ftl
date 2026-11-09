package ${pkg}.dto;

<#if pageDtoHasBigDecimal>import java.math.BigDecimal;
</#if>
<#if pageDtoHasLocalDateTime>import java.time.LocalDateTime;
</#if>
<#if pageDtoHasLocalDate>import java.time.LocalDate;
</#if>
<#if pageDtoHasLocalTime>import java.time.LocalTime;
</#if>
<#if pageDtoHasImport>${''}
</#if>
import ${rootPkg}.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ${comment}分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${className}PageDTO extends PageQuery {

    // <gen:begin key="dto-fields">
<#list queryColumns as c>
    private ${c.javaType} ${c.javaField};
</#list>
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}
