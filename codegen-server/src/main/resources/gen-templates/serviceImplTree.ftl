package ${pkg}.service.impl;

<#if genTree || hasChild>import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
</#if>import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import ${pkg}.entity.${className};
import ${pkg}.mapper.${className}Mapper;
import ${pkg}.service.${className}Service;
<#if genTree>import ${pkg}.vo.${className}VO;
import org.springframework.beans.BeanUtils;
</#if>import org.springframework.stereotype.Service;
<#if genTree>import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
</#if>

/**
 * ${comment} Service
 */
@Service
public class ${className}ServiceImpl extends ServiceImpl<${className}Mapper, ${className}> implements ${className}Service {

    // <gen:begin key="service-body">
<#if genTree>
    @Override
    public List<${className}VO> tree() {
        List<${className}> all = list(new LambdaQueryWrapper<${className}>().orderByAsc(${className}::getId));
        List<${className}VO> vos = new ArrayList<>();
        for (${className} entity : all) {
            ${className}VO vo = new ${className}VO();
            BeanUtils.copyProperties(entity, vo);
            vos.add(vo);
        }
        Map<Long, ${className}VO> index = vos.stream()
                .collect(Collectors.toMap(${className}VO::getId, Function.identity(), (a, b) -> a));
        List<${className}VO> roots = new ArrayList<>();
        for (${className}VO vo : vos) {
            ${treeParentJavaType} parentId = vo.get${treeParentCapField}();
            Long parentKey = parentId == null ? null : Long.valueOf(String.valueOf(parentId));
            ${className}VO parent = parentKey == null ? null : index.get(parentKey);
            if (parent == null || parent == vo) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }
</#if>
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
