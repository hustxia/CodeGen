package ${pkg}.service;

import com.baomidou.mybatisplus.extension.service.IService;
import ${pkg}.entity.${className};
<#if genTree>import ${pkg}.vo.${className}VO;
import java.util.List;
</#if>
<#if hasChild>import ${pkg}.dto.${className}SaveDTO;
import ${pkg}.dto.${child.className}SaveDTO;
import java.util.List;
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
     * 主表 + 子表同事务保存（先清旧子表再插新行）
     */
    void saveWithChildren(${className}SaveDTO master, List<${child.className}SaveDTO> children);
</#if>
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
