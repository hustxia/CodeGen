package ${pkg}.service;

import com.baomidou.mybatisplus.extension.service.IService;
import ${pkg}.entity.${className};
<#if genTree>import ${pkg}.vo.${className}VO;
import java.util.List;
</#if>
<#if hasChild>import ${pkg}.dto.${className}SaveDTO;
<#list children as child>import ${pkg}.dto.${child.className}SaveDTO;
</#list>import java.util.List;
</#if>

/**
 * ${comment} Service
 */
public interface ${className}Service extends IService<${className}> {

    // <gen:begin key="service-body">
<#if genTree>
    /**
     * 树形列表（父子嵌套）
     */
    List<${className}VO> tree();
</#if>
<#if hasChild>
    /**
     * 主表 + 子表同事务保存（每张子表先清后插；参数为 null 表示该子表不动，空列表表示清空）
     */
    void saveWithChildren(${className}SaveDTO master<#list children as child>,
                          List<${child.className}SaveDTO> ${child.varName}Rows</#list>);

    /**
     * V4.4 按表的删除级联模式删除主表：
     * 级联=同事务删光全部子表（含三级）；阻止=还有子行就拒删；不处理=仅删主行。
     */
    void removeWithChildren(Long id);
</#if>
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
