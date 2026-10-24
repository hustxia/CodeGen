package ${pkg}.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
<#if listHasBigDecimal>import java.math.BigDecimal;
</#if>
<#if listHasLocalDate>import java.time.LocalDate;
</#if>
<#if listHasLocalTime>import java.time.LocalTime;
</#if>
<#if genTree>import java.util.ArrayList;
import java.util.List;
</#if>
<#if isChild && hasChild>import java.util.List;
<#list children as g>import ${g.pkg}.vo.${g.className}VO;
</#list></#if>

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
<#if isChild && hasChild>
<#list children as g>
    /**
     * 第三级明细：详情接口一次性带出来，前端不用逐行再查
     */
    private List<${g.className}VO> ${g.varName}s;
</#list>
</#if>
    // <gen:end key="vo-fields">
<#if hasMask>
    // ===== 脱敏 getter（P1-1）：@Data 生成的 getter 被这里同名覆盖，序列化时自动脱敏 =====
<#list maskColumns as c>
    /**
     * ${c.columnComment} 脱敏（${c.maskType}）
     */
    public ${c.javaType} get${c.capField}() {
        return ${rootPkg}.common.util.MaskUtils.mask(this.${c.javaField}, "${c.maskType}");
    }

</#list>
</#if>
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}
