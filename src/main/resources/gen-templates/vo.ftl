package ${pkg}.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
<#if listHasBigDecimal>import java.math.BigDecimal;
</#if>
<#if listHasLocalDate>import java.time.LocalDate;
</#if>
<#if genTree>import java.util.ArrayList;
import java.util.List;
</#if>

/**
 * ${comment} VO
 */
@Data
public class ${className}VO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
<#list listColumns as c>
    private ${c.javaType} ${c.javaField};
</#list>
<#if genTree && !treeParentInList>
    private ${treeParentJavaType} ${treeParentField};
</#if>
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
<#if genTree>
    /**
     * 树形子节点
     */
    private List<${className}VO> children = new ArrayList<>();
</#if>
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}
