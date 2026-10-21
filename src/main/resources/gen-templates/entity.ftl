package ${pkg}.entity;

<#if entityHasBigDecimal>import java.math.BigDecimal;
</#if>
<#if entityHasLocalDateTime>import java.time.LocalDateTime;
</#if>
<#if entityHasLocalDate>import java.time.LocalDate;
</#if>
<#if entityHasLocalTime>import java.time.LocalTime;
</#if>
<#if entityHasImport>${''}
</#if>
import com.baomidou.mybatisplus.annotation.TableName;
import ${rootPkg}.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ${comment}
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("${tableName}")
public class ${className} extends BaseEntity {

    // <gen:begin key="entity-fields">
<#list columns as c>
    /**
     * ${c.columnComment}
     */
    private ${c.javaType} ${c.javaField};

</#list>
    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}
