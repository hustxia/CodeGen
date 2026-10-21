package ${pkg}.service.impl;

<#if genTree || hasChild>import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
</#if>
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import ${pkg}.entity.${className};
import ${pkg}.mapper.${className}Mapper;
import ${pkg}.service.${className}Service;
<#if genTree>import ${pkg}.vo.${className}VO;
import org.springframework.beans.BeanUtils;
</#if>
<#if hasChild>import ${pkg}.dto.${className}SaveDTO;
import ${pkg}.dto.${child.className}SaveDTO;
import ${pkg}.entity.${child.className};
import ${pkg}.mapper.${child.className}Mapper;
<#if !genTree>import org.springframework.beans.BeanUtils;
</#if>import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
</#if>
import org.springframework.stereotype.Service;
<#if genTree>import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
</#if>
<#if hasChild && !genTree>import java.util.List;
</#if>

/**
 * ${comment} Service
 */
@Service
public class ${className}ServiceImpl extends ServiceImpl<${className}Mapper, ${className}> implements ${className}Service {

    // <gen:begin key="service-body">
<#if hasChild>
    @Autowired
    private ${child.className}Mapper ${child.varName}Mapper;
<#if child.hasGrandChild>
    @Autowired
    private ${child.grandChild.className}Mapper ${child.grandChild.varName}Mapper;
</#if>
</#if>
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
<#if hasChild>
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithChildren(${className}SaveDTO master, List<${child.className}SaveDTO> children) {
        ${className} entity = new ${className}();
        BeanUtils.copyProperties(master, entity);
        saveOrUpdate(entity);
        ${child.className}Mapper childMapper = ${child.varName}Mapper;
        // 子表全量替换：先按外键逻辑删除旧行，再逐行插入，整体与主表同一事务
        childMapper.delete(new LambdaQueryWrapper<${child.className}>()
                .eq(${child.className}::get${child.fkCapField}, entity.getId()));
        if (children != null) {
            for (${child.className}SaveDTO dto : children) {
                ${child.className} child = new ${child.className}();
                BeanUtils.copyProperties(dto, child);
                // 关键：旧行是逻辑删除（deleted=1，物理行仍在），沿用回传主键会撞 PRIMARY。
                // 一律清空主键与审计字段，让雪花 ID 重新生成、时间交给自动填充。
                child.setId(null);
                child.setCreateTime(null);
                child.setUpdateTime(null);
                child.setCreateBy(null);
                child.setUpdateBy(null);
                child.set${child.fkCapField}(entity.getId());
                childMapper.insert(child);
<#if child.hasGrandChild>
                // P3-1 三级：这一行的下级也要跟着落库，外键指向刚插入的这行
                save${child.grandChild.className}Rows(child.getId(), dto.get${child.grandChild.className}s());
</#if>
            }
        }
    }
<#if child.hasGrandChild>
    /**
     * 第三级全量替换。抽成独立方法，避免 {@code saveWithChildren} 里出现三层嵌套循环。
     */
    private void save${child.grandChild.className}Rows(Long parentId,
                                                  List<${child.grandChild.className}SaveDTO> rows) {
        ${child.grandChild.className}Mapper mapper = ${child.grandChild.varName}Mapper;
        if (parentId == null) {
            return;
        }
        mapper.delete(new LambdaQueryWrapper<${child.grandChild.className}>()
                .eq(${child.grandChild.className}::get${child.grandChild.fkCapField}, parentId));
        if (rows == null) {
            return;
        }
        for (${child.grandChild.className}SaveDTO dto : rows) {
            ${child.grandChild.className} row = new ${child.grandChild.className}();
            BeanUtils.copyProperties(dto, row);
            row.setId(null);
            row.setCreateTime(null);
            row.setUpdateTime(null);
            row.setCreateBy(null);
            row.setUpdateBy(null);
            row.set${child.grandChild.fkCapField}(parentId);
            mapper.insert(row);
        }
    }
</#if>
</#if>
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
